package org.example.clients;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.example.context.ClientContext;
import org.example.objects.*;
import org.example.secureDocuments.Protect;
import org.example.secureDocuments.ProtectResult;
import org.example.secureDocuments.Unprotect;
import org.example.secureDocuments.Check;
import org.example.utils.CryptoUtils;
import org.example.utils.JsonUtils;
import org.example.utils.KeyUtils;
import org.glassfish.jersey.gson.JsonGsonFeature;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TransactionsClient {

  private static final String PATH = "/chain-of-product";
  private final ClientContext context;
  private final WebTarget baseTarget;
  private final GroupServerClient groupServerClient;

  public TransactionsClient(ClientContext context) {
    this.context = context;

    Client client = ClientBuilder.newClient().register(JsonGsonFeature.class);
    this.baseTarget = client.target(context.getServerUri()).path(PATH);

    this.groupServerClient = new GroupServerClient(context);
  }

  public void storeTransaction(String filepath, String myCompanyId) {

    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[STORE TRANSACTION] Not logged in");
      return;
    }

    EncryptedTransaction transaction = loadAndBuildTransaction(filepath, myCompanyId);
    if (transaction == null) {
      System.out.println("Error loading transaction");
      return;
    }

    try (Response response = baseTarget.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .post(Entity.entity(transaction, MediaType.APPLICATION_JSON))) {

      if (response.getStatus() == 200) { // 200 OK
        System.out.println("[STORE TRANSACTION] Success! Transaction stored.");
      } else {
        System.out.println("[STORE TRANSACTION] Failed: " + response.getStatus());
        String error = response.readEntity(String.class);
        System.out.println("Server said: " + error);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private EncryptedTransaction loadAndBuildTransaction(String filename, String myCompanyId) {
    try {
      Path filepath = Path.of(filename);
      if (!Files.exists(filepath)) {
        System.out.println("File not found: " + filepath.toAbsolutePath());
        throw new RuntimeException("File not found: " + filepath.toAbsolutePath());
      }

      String rawJsonContent = Files.readString(filepath);
      System.out.println(rawJsonContent);

      Gson gson = new Gson();
      JsonObject rawDoc = gson.fromJson(rawJsonContent, JsonObject.class);

      if (!rawDoc.has("id") || !rawDoc.has("timestamp") || !rawDoc.has("seller") ||
          !rawDoc.has("buyer") || !rawDoc.has("product") || !rawDoc.has("units") ||
          !rawDoc.has("amount")) {
        System.out.println("Error: JSON file must contain 'id', 'timestamp', 'seller', 'buyer', " +
            "'product', 'units' and 'amount' fields.");
        throw new RuntimeException("JSON file must contain all the required fields.");
      }

      String docId = rawDoc.get("id").getAsString();
      String buyer = rawDoc.get("buyer").getAsString();
      String seller = rawDoc.get("seller").getAsString();
      long timestamp = rawDoc.get("timestamp").getAsLong();

      boolean actingAsSeller = myCompanyId.equals(seller);
      boolean actingAsBuyer  = myCompanyId.equals(buyer);
      
      if (!actingAsSeller && !actingAsBuyer) {
        System.out.println("[STORE TRANSACTION] You (" + myCompanyId + ") are neither buyer nor seller of this transaction.");
        return null;
      }

      String otherParty = actingAsSeller ? buyer : seller;
      
      Map<String, Object> protectDoc = new HashMap<>();
      protectDoc.put("sender", myCompanyId);
      protectDoc.put("receiver", otherParty);
      protectDoc.put("timestamp", timestamp);
      protectDoc.put("seq_num", 1);
      protectDoc.put("content", rawJsonContent);

      // load keys
      String signerPrivKeyPath = KeyUtils.getPrivateKeyPathForCompany(myCompanyId);
      String sellerPubKeyPath = KeyUtils.getPublicKeyPathForCompany(seller);
      String buyerPubKeyPath = KeyUtils.getPublicKeyPathForCompany(buyer);

      // protect the document
      ProtectResult protectResult = Protect.protect(protectDoc, signerPrivKeyPath);

      Map<String, Object> secureDocument = protectResult.getSecureDocument();
      SecretKey transactionKey = protectResult.getTransactionKey();
      String signature = protectResult.getSenderSignature();

      // encrypted content to JSON
      String encryptedContent = gson.toJson(secureDocument);

      EncryptedTransaction transaction = new EncryptedTransaction(docId, seller, buyer, myCompanyId, encryptedContent);

      // add seller or buyer signature (whoever is creating)
      if (actingAsSeller) {
        transaction.addSellerSignature(signature);
      } else if (actingAsBuyer) {
        transaction.addBuyerSignature(signature);
      }

      // Share the symmetric key with seller and buyer
      PublicKey sellerPublicKey = KeyUtils.loadPublicKeyFromFile(sellerPubKeyPath);
      PublicKey buyerPublicKey  = KeyUtils.loadPublicKeyFromFile(buyerPubKeyPath);

      // Encrypt transactionKey for each party
      String encKeyForSeller = CryptoUtils.encryptKeyRSA(sellerPublicKey, transactionKey);
      String encKeyForBuyer  = CryptoUtils.encryptKeyRSA(buyerPublicKey, transactionKey);

      // Add keys to the metadata
      SharedKey sellerKeyShare = new SharedKey("seller", seller, encKeyForSeller);
      SharedKey buyerKeyShare = new SharedKey("buyer", buyer, encKeyForBuyer);
      transaction.addSharedKey(sellerKeyShare);
      transaction.addSharedKey(buyerKeyShare);

      return transaction;

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Fetch a transaction and decrypt it, supporting both direct shares and group-based shares.
   */
  public Map<String, Object> fetchAndDecrypt(String transactionId, String myCompanyId) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[FETCH+DECRYPT] Not logged in");
      return null;
    }

    WebTarget fetchTarget = baseTarget.path(transactionId);

    try (Response response = fetchTarget.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .get()) {

      int status = response.getStatus();
      if (status != 200) {
        System.out.println("[FETCH+DECRYPT] Failed: " + status);
        String error = response.hasEntity() ? response.readEntity(String.class) : "";
        if (!error.isEmpty()) {
          System.out.println("Server said: " + error);
        }
        return null;
      }

      // Deserialize EncryptedTransaction from JSON
      EncryptedTransaction tx = response.readEntity(EncryptedTransaction.class);
      System.out.println("[FETCH+DECRYPT] Got transaction " + tx.getTransactionId());

      // Parse encryptedContent from JSON to Map
      String encryptedContentJson = tx.getEncryptedContent();
      Gson gson = new Gson();
      @SuppressWarnings("unchecked")
      Map<String, Object> secureDocument =
          gson.fromJson(encryptedContentJson, Map.class);

      // Derive transactionKey (company first; if missing, try group-based)
      SecretKey transactionKey = deriveTransactionKey(tx, myCompanyId);

      // Unprotect: decrypt and verify inner integrity hash
      Map<String, Object> originalDoc = Unprotect.unprotect(secureDocument, transactionKey);

      // Print
      System.out.println("Decrypted transaction:");
      System.out.println("  Sender:    " + originalDoc.get("sender"));
      System.out.println("  Receiver:  " + originalDoc.get("receiver"));
      System.out.println("  Timestamp: " + originalDoc.get("timestamp"));
      System.out.println("  Seq num:   " + originalDoc.get("seq_num"));
      System.out.println("  Content:   " + originalDoc.get("content"));

      System.out.println(verifyAgreementSignatures(tx, secureDocument, myCompanyId));
      verifySharePermissionsOnFetch(tx);

      return originalDoc;

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * First try deriving the transaction key directly for this company.
   * If that fails (no key for this company), fall back to group-based key via Group Server.
   */
  private SecretKey deriveTransactionKey(EncryptedTransaction tx, String myCompanyId) throws Exception {
    try {
      // Try standard path (buyer / seller / direct 3rd party)
      return deriveTransactionKeyForCompany(tx, myCompanyId);
    } catch (SecurityException | IllegalStateException e) {
      // No direct key -> likely a group-based share; try that
      SecretKey fromGroup = deriveTransactionKeyViaGroup(tx);
      if (fromGroup != null) {
        return fromGroup;
      }
      throw new SecurityException("No usable shared key found for " + myCompanyId + " (company or group).", e);
    }
  }

  private SecretKey deriveTransactionKeyForCompany(EncryptedTransaction tx, String myCompanyId) throws Exception {
    if (tx.getMetadata() == null || tx.getSharedKeys() == null) {
      throw new IllegalStateException("No shared keys available in metadata.");
    }

    // Find this company's SharedKey entry
    SharedKey myKeyShare = null;
    for (SharedKey k : tx.getSharedKeys()) {
      if (myCompanyId.equals(k.getForCompany())) {
        myKeyShare = k;
        break;
      }
    }

    if (myKeyShare == null) {
      throw new SecurityException("No shared key for company: " + myCompanyId);
    }

    // Load this company's private RSA key
    String myPrivKeyPath = KeyUtils.getPrivateKeyPathForCompany(myCompanyId);
    PrivateKey myPrivateKey = KeyUtils.loadPrivateKeyFromFile(myPrivKeyPath);

    // Decrypt encKey -> AES transactionKey K_tx
    String encKeyB64 = myKeyShare.getEncKey();
    return CryptoUtils.decryptKeyRSA(myPrivateKey, encKeyB64);
  }

  private SecretKey deriveTransactionKeyViaGroup(EncryptedTransaction tx) throws Exception {
    if (tx.getMetadata() == null || tx.getSharedKeys() == null || tx.getSharedKeys().isEmpty()) {
      return null;
    }

    for (SharedKey k : tx.getSharedKeys()) {
      String encKeyB64 = k.getEncKey();
      if (encKeyB64 == null || encKeyB64.isEmpty()) {
        continue;
      }

      // Ask Group Server to unwrap this key for the calling user (if they are a member)
      byte[] keyBytes = groupServerClient.decryptTransactionKey(encKeyB64);
      if (keyBytes != null) {
        System.out.println("[FETCH+DECRYPT] Transaction key obtained");
        return new SecretKeySpec(keyBytes, CryptoUtils.AES_ALGORITHM);
      }
    }
    return null;
  }

  /**
   * Verify agreement signatures, but only for the current user:
   *  - seller sees/validates sellerSignature
   *  - buyer sees/validates buyerSignature
   *  - third parties see no agreement signatures
   */
  private String verifyAgreementSignatures(EncryptedTransaction tx,
                                           Map<String, Object> secureDocument,
                                           String myCompanyId) throws Exception {
    if (tx.getMetadata() == null) {
      return "No agreement signatures present in metadata.";
    }

    boolean isSeller = myCompanyId.equals(tx.getSeller());
    boolean isBuyer  = myCompanyId.equals(tx.getBuyer());

    if (!isSeller && !isBuyer) {
      return "Third party view: you do not hold agreement signatures.";
    }

    StringBuilder sb = new StringBuilder("\nAgreement status:\n");

    if (isSeller) {
      if (!tx.signedBySeller()) {
        sb.append("  Your agreement signature (seller): PENDING\n");
      } else {
          try {
            String sellerPubKeyPath = KeyUtils.getPublicKeyPathForCompany(tx.getSeller());
            String sellerSig = tx.getSellerSignature();
            boolean sellerOk = Check.check(secureDocument, sellerSig, sellerPubKeyPath);
            sb.append("  Your agreement signature (seller): ")
              .append(sellerOk ? "VALID" : "INVALID")
              .append("\n");
          } catch (IllegalArgumentException e) {
            sb.append("  Your agreement signature (seller): ERROR (").append(e.getMessage()).append(")\n");
          }
      }
    }

    if (isBuyer) {
      if (!tx.signedByBuyer()) {
        sb.append("  Your agreement signature (buyer): PENDING\n");
      } else {
          try {
            String buyerPubKeyPath = KeyUtils.getPublicKeyPathForCompany(tx.getBuyer());
            String buyerSig = tx.getBuyerSignature();
            boolean buyerOk =  Check.check(secureDocument, buyerSig, buyerPubKeyPath);
            sb.append("  Your agreement signature (buyer): ")
              .append(buyerOk ? "VALID" : "INVALID")
              .append("\n");
        } catch (IllegalArgumentException e) {
          sb.append("  Your agreement signature (buyer): ERROR (").append(e.getMessage()).append(")\n");
        }
      }
    }

    return sb.toString();
  }

  private void verifySharePermissionsOnFetch(EncryptedTransaction tx) {
    if (tx.getMetadata() == null) {
      System.out.println("[SHARE LOG] No share logs present in metadata.");
      return;
    }

    try {
      Gson gson = new Gson();
      @SuppressWarnings("unchecked")
      Map<String, Object> secureDocument = gson.fromJson(tx.getEncryptedContent(), Map.class);
      long txTimestamp = Long.parseLong(secureDocument.get("timestamp").toString());

      // Individual shares
      if (tx.getShareList() == null || tx.getShareList().isEmpty()) {
        System.out.println("[SHARE LOG] No individual share logs present in metadata.");
      } else {
        System.out.println("[SHARE LOG] Verifying individual share permissions:");
        for (ShareLog log : tx.getShareList()) {
          String sharedBy = log.getSharedBy();
          String sharedWith = log.getSharedWith();
          String sig = log.getSignature();

          if (sig == null || sig.trim().isEmpty()) {
            System.out.println("  " + sharedBy + " -> " + sharedWith + " : NO SIGNATURE");
            continue;
          }

          String dataToVerify = tx.getTransactionId() + "|" + txTimestamp + "|" + sharedBy + "|" + sharedWith;

          try {
            String sharedByPubKeyPath = KeyUtils.getPublicKeyPathForCompany(sharedBy);
            PublicKey sharedByPublicKey = KeyUtils.loadPublicKeyFromFile(sharedByPubKeyPath);

            boolean ok = CryptoUtils.verifySignature(sharedByPublicKey, dataToVerify, sig);
            System.out.println("  " + sharedBy + " -> " + sharedWith + " : " + (ok ? "VALID" : "INVALID"));
          } catch (Exception ex) {
            System.out.println("  " + sharedBy + " -> " + sharedWith + " : ERROR verifying (" + ex.getMessage() + ")");
            ex.printStackTrace();
          }
        }
      }

      // Group shares
      if (tx.getGroupShareList() == null || tx.getGroupShareList().isEmpty()) {
        System.out.println("[SHARE LOG] No group share logs present in metadata.");
      } else {
        System.out.println("[SHARE LOG] Verifying group share permissions:");
        for (ShareLog log : tx.getGroupShareList()) {
          String sharedBy = log.getSharedBy();
          String groupName = log.getSharedWith();
          String sig = log.getSignature();

          if (sig == null || sig.trim().isEmpty()) {
            System.out.println("  " + sharedBy + " -> GROUP " + groupName + " : NO SIGNATURE");
            continue;
          }

          String dataToVerify = tx.getTransactionId() + "|" + txTimestamp + "|" + sharedBy + "|" + groupName;

          try {
            String sharedByPubKeyPath = KeyUtils.getPublicKeyPathForCompany(sharedBy);
            PublicKey sharedByPublicKey = KeyUtils.loadPublicKeyFromFile(sharedByPubKeyPath);

            boolean ok = CryptoUtils.verifySignature(sharedByPublicKey, dataToVerify, sig);
            System.out.println("  " + sharedBy + " -> GROUP " + groupName + " : " + (ok ? "VALID" : "INVALID"));
          } catch (Exception ex) {
            System.out.println("  " + sharedBy + " -> GROUP " + groupName + " : ERROR verifying (" + ex.getMessage() + ")");
            ex.printStackTrace();
          }
        }
      }

    } catch (Exception e) {
      System.out.println("[SHARE LOG] Error while verifying share logs: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public void signTransaction(String transactionId, String myCompanyId) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[SIGN TRANSACTION] Not logged in");
      return;
    }

    // Fetch the transaction from the server
    WebTarget fetchTarget = baseTarget.path(transactionId);

    EncryptedTransaction tx;
    try (Response response = fetchTarget.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .get()) {

      int status = response.getStatus();
      if (status != 200) {
        System.out.println("[SIGN TRANSACTION] Fetch failed: " + status);
        String error = response.hasEntity() ? response.readEntity(String.class) : "";
        if (!error.isEmpty()) {
          System.out.println("Server said: " + error);
        }
        return;
      }

      tx = response.readEntity(EncryptedTransaction.class);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    try {
      // Parse encryptedContent
      String encryptedContentJson = tx.getEncryptedContent();
      Gson gson = new Gson();

      @SuppressWarnings("unchecked")
      Map<String, Object> secureDocument = gson.fromJson(encryptedContentJson, Map.class);

      // JSON of secureDocument
      Map<String, Object> sortedDoc = JsonUtils.sortDocument(secureDocument);
      String jsonToSign = JsonUtils.toSortedJson(sortedDoc);

      // Load own private key
      String myPrivKeyPath = KeyUtils.getPrivateKeyPathForCompany(myCompanyId);
      java.security.PrivateKey myPrivateKey = KeyUtils.loadPrivateKeyFromFile(myPrivKeyPath);

      // Create the digital signature over the secureDocument
      String signature = CryptoUtils.signData(myPrivateKey, jsonToSign);

      // Send signature as a JSON string body
      WebTarget signTarget = baseTarget.path("sign").path(transactionId);

      try (Response signResponse = signTarget.request(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + token)
          .method("POST", Entity.entity(signature, MediaType.APPLICATION_JSON))) {

        int status = signResponse.getStatus();
        if (status == 200) {
          System.out.println("[SIGN TRANSACTION] Signature sent successfully.");
        } else {
          System.out.println("[SIGN TRANSACTION] Failed: " + status);
          String error = signResponse.hasEntity() ? signResponse.readEntity(String.class) : "";
          if (!error.isEmpty()) {
            System.out.println("Server said: " + error);
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void shareWithThirdParty(String transactionId, String myCompanyId, String sharedWithCompanyId) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[SHARE] Not logged in");
      return;
    }
    WebTarget fetchTarget = baseTarget.path(transactionId);

    EncryptedTransaction tx;
    try (Response response = fetchTarget.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .get()) {

      int status = response.getStatus();
      if (status != 200) {
        System.out.println("[SHARE] Fetch failed: " + status);
        String error = response.hasEntity() ? response.readEntity(String.class) : "";
        if (!error.isEmpty()) {
          System.out.println("Server said: " + error);
        }
        return;
      }

      tx = response.readEntity(EncryptedTransaction.class);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    try {
      // Derive the symmetric key and encrypt it for the 3rd party using their public key
      SecretKey transactionKey = deriveTransactionKeyForCompany(tx, myCompanyId);
      String thirdPartyPubKeyPath = KeyUtils.getPublicKeyPathForCompany(sharedWithCompanyId);
      PublicKey thirdPartyPublicKey = KeyUtils.loadPublicKeyFromFile(thirdPartyPubKeyPath);

      String encKeyForThirdParty = CryptoUtils.encryptKeyRSA(thirdPartyPublicKey, transactionKey);

      SharedKey thirdPartyKeyShare = new SharedKey("third_party", sharedWithCompanyId, encKeyForThirdParty);

      // Parse secureDocument to get the clear timestamp
      Gson gson = new Gson();
      @SuppressWarnings("unchecked")
      Map<String, Object> secureDocument = gson.fromJson(tx.getEncryptedContent(), Map.class);
      long txTimestamp = Long.parseLong(secureDocument.get("timestamp").toString());

      String dataToSign = tx.getTransactionId() + "|" + txTimestamp + "|" + myCompanyId + "|" + sharedWithCompanyId;

      // Load private key
      String sharedByPrivKeyPath = KeyUtils.getPrivateKeyPathForCompany(myCompanyId);
      PrivateKey sharedByPrivateKey = KeyUtils.loadPrivateKeyFromFile(sharedByPrivKeyPath);

      // Create digital signature over dataToSign
      String shareSignature = CryptoUtils.signData(sharedByPrivateKey, dataToSign);

      // Send the updated metadata
      ShareLog shareLog = new ShareLog(myCompanyId, sharedWithCompanyId, shareSignature);
      ShareRequest shareRequest = new ShareRequest(shareLog, thirdPartyKeyShare);
      WebTarget shareTarget = baseTarget.path("share").path(transactionId);

      try (Response shareResponse = shareTarget.request(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + token)
          .method("POST", Entity.entity(shareRequest, MediaType.APPLICATION_JSON))) {

        int status = shareResponse.getStatus();
        if (status == 200) {
          System.out.println("[SHARE] Transaction shared with " + sharedWithCompanyId + " successfully.");
        } else {
          System.out.println("[SHARE] Failed: " + status);
          String error = shareResponse.hasEntity() ? shareResponse.readEntity(String.class) : "";
          if (!error.isEmpty()) {
            System.out.println("Server said: " + error);
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Share with a group: encrypt the transaction key (plus groupName) with the Group Server public key,
   * and log the share using groupName as the "sharedWith" principal.
   */
  public void shareWithGroup(String transactionId, String myCompanyId, String groupName) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[SHARE-GROUP] Not logged in");
      return;
    }

    WebTarget fetchTarget = baseTarget.path(transactionId);

    EncryptedTransaction tx;
    try (Response response = fetchTarget.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .get()) {

      int status = response.getStatus();
      if (status != 200) {
        System.out.println("[SHARE-GROUP] Fetch failed: " + status);
        String error = response.hasEntity() ? response.readEntity(String.class) : "";
        if (!error.isEmpty()) {
          System.out.println("Server said: " + error);
        }
        return;
      }

      tx = response.readEntity(EncryptedTransaction.class);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    try {
      SecretKey transactionKey = deriveTransactionKeyForCompany(tx, myCompanyId);

      // Get Group Server public key (Base64 DER)
      String groupServerPubKeyB64 = groupServerClient.getGroupServerPublicKey();
      if (groupServerPubKeyB64 == null) {
        System.out.println("[SHARE-GROUP] Could not obtain Group Server public key.");
        return;
      }

      byte[] pubBytes = Base64.getDecoder().decode(groupServerPubKeyB64);
      X509EncodedKeySpec spec = new X509EncodedKeySpec(pubBytes);
      KeyFactory keyFactory = KeyFactory.getInstance(CryptoUtils.RSA_ALGORITHM);
      PublicKey groupServerPublicKey = keyFactory.generatePublic(spec);

      // Encrypt both the transaction key and group name with the Group Server public key
      // Create a JSON payload with groupName and transactionKey
      Gson gson = new Gson();
      Map<String, String> payload = new HashMap<>();
      payload.put("groupName", groupName);
      payload.put("transactionKey", Base64.getEncoder().encodeToString(transactionKey.getEncoded()));
      
      String payloadJson = gson.toJson(payload);
      byte[] payloadBytes = payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      
      // Encrypt the JSON payload with RSA
      javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, groupServerPublicKey);
      byte[] encryptedPayload = cipher.doFinal(payloadBytes);
      String encKeyForGroup = Base64.getEncoder().encodeToString(encryptedPayload);
      
      SharedKey groupKeyShare = new SharedKey("group", groupName, encKeyForGroup);

      // Parse secureDocument to get the clear timestamp
      @SuppressWarnings("unchecked")
      Map<String, Object> secureDocument = gson.fromJson(tx.getEncryptedContent(), Map.class);
      long txTimestamp = Long.parseLong(secureDocument.get("timestamp").toString());

      String dataToSign = tx.getTransactionId() + "|" + txTimestamp + "|" + myCompanyId + "|" + groupName;

      String sharedByPrivKeyPath = KeyUtils.getPrivateKeyPathForCompany(myCompanyId);
      PrivateKey sharedByPrivateKey = KeyUtils.loadPrivateKeyFromFile(sharedByPrivKeyPath);

      String shareSignature = CryptoUtils.signData(sharedByPrivateKey, dataToSign);

      ShareLog shareLog = new ShareLog(myCompanyId, groupName, shareSignature);
      ShareRequest shareRequest = new ShareRequest(shareLog, groupKeyShare);

      WebTarget shareTarget = baseTarget.path("group-share").path(transactionId);

      try (Response shareResponse = shareTarget.request(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + token)
          .method("POST", Entity.entity(shareRequest, MediaType.APPLICATION_JSON))) {

        int status = shareResponse.getStatus();
        if (status == 200) {
          System.out.println("[SHARE-GROUP] Transaction shared with group '" + groupName + "' successfully.");
        } else {
          System.out.println("[SHARE-GROUP] Failed: " + status);
          String error = shareResponse.hasEntity() ? shareResponse.readEntity(String.class) : "";
          if (!error.isEmpty()) {
            System.out.println("Server said: " + error);
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

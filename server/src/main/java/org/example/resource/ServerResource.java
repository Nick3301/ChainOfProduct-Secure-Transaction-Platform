package org.example.resource;

import com.google.gson.Gson;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.example.clients.GroupServerClient;
import org.example.database.Database;
import org.example.objects.*;
import org.example.api.ServerApi;
import org.example.secureDocuments.Check;
import org.example.utils.CryptoUtils;
import org.example.utils.JsonUtils;
import org.example.utils.KeyUtils;

import java.security.PublicKey;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ServerResource is the class that implements the execution of the transaction related requests.
 * Basically, the CRUD operations for each transaction.
 */
public class ServerResource implements ServerApi {

  GroupServerClient client = null;

  private GroupServerClient getGroupServerClient() {
    if (client == null) {
      client = new GroupServerClient();
    }
    return client;
  }

  @Override
  public Response storeTransaction(ContainerRequestContext context,
                                   EncryptedTransaction transaction) {
    System.out.println("[ServerResource] storeTransaction request received");

    String username = context.getProperty("user").toString();
    String buyer = transaction.getBuyer();
    String seller = transaction.getSeller();
    if (!(username.equals(buyer) || username.equals(seller))) {
      System.out.println("[ServerResource] Request denied for user " + username +
          ". You cannot store a transaction you are not a part of.");
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    try (Connection conn = Database.getConnection()) {
      String sql1 = "SELECT * FROM transactions WHERE transaction_id = ?";
      PreparedStatement preparedStatement = conn.prepareStatement(sql1);
      preparedStatement.setString(1, transaction.getTransactionId());
      ResultSet rs = preparedStatement.executeQuery();
      if (rs.next()) {
        return Response.status(Response.Status.CONFLICT).entity("Duplicate transactionId").build();
      }

      Gson gson = new Gson();

      // --- Verify signature over secureDocument before storing ---
      EncryptedTransaction.TransactionMetadata metadata = transaction.getMetadata();

      // Build temp transaction
      EncryptedTransaction temp = new EncryptedTransaction(
          transaction.getTransactionId(),
          transaction.getSeller(),
          transaction.getBuyer(),
          transaction.getCreatedBy(),
          transaction.getEncryptedContent(),
          metadata
      );

      @SuppressWarnings("unchecked")
      Map<String, Object> secureDocument =
          gson.fromJson(transaction.getEncryptedContent(), Map.class);

      // Verify seller signature if present
      if (temp.signedBySeller()) {
        String sellerSig = temp.getSellerSignature();
        String sellerPubKeyPath = KeyUtils.getPublicKeyPathForCompany(seller);
        boolean sellerOk = Check.check(secureDocument, sellerSig, sellerPubKeyPath);
        if (!sellerOk) {
          return Response.status(Response.Status.BAD_REQUEST)
              .entity("Invalid seller agreement signature").build();
        }
      }

      // Verify buyer signature if present
      if (temp.signedByBuyer()) {
        String buyerSig = temp.getBuyerSignature();
        String buyerPubKeyPath = KeyUtils.getPublicKeyPathForCompany(buyer);
        boolean buyerOk = Check.check(secureDocument, buyerSig, buyerPubKeyPath);
        if (!buyerOk) {
          return Response.status(Response.Status.BAD_REQUEST)
              .entity("Invalid buyer agreement signature").build();
        }
      }

      // --- Store transaction + metadata ---
      String sql2 =
          "INSERT INTO transactions (transaction_id, seller, buyer, created_by, " +
              "encrypted_content, " +
              "metadata) VALUES (?, ?, ?, ?, ?, ?)";

      PreparedStatement stmt = conn.prepareStatement(sql2);
      stmt.setString(1, transaction.getTransactionId());
      stmt.setString(2, transaction.getSeller());
      stmt.setString(3, transaction.getBuyer());
      stmt.setString(4, transaction.getCreatedBy());
      stmt.setString(5, transaction.getEncryptedContent());

      String metadataJson = gson.toJson(transaction.getMetadata());
      stmt.setObject(6, metadataJson, java.sql.Types.OTHER);

      stmt.executeUpdate();

      System.out.println("[ServerResource] Transaction '" + transaction.getTransactionId() +
          "' stored successfully");

      return Response.ok().build();

    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("[ServerResource] storeTransaction request failed");
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @Override
  public Response fetchTransaction(ContainerRequestContext context, String transactionId) {
    System.out.println(
        "[ServerResource] Fetch transaction request received for the transaction: '" +
            transactionId + "'");

    try (Connection conn = Database.getConnection()) {
      String sql = "SELECT * FROM transactions WHERE transaction_id = ?";
      PreparedStatement stmt = conn.prepareStatement(sql);
      stmt.setString(1, transactionId);

      ResultSet rs = stmt.executeQuery();

      if (rs.next()) {
        Gson gson = new Gson();

        String encryptedContent = rs.getString("encrypted_content");
        String seller = rs.getString("seller");
        String buyer = rs.getString("buyer");
        String createdBy = rs.getString("created_by");
        String metadataJson = rs.getString("metadata");

        EncryptedTransaction.TransactionMetadata metadata = gson.fromJson(metadataJson,
            EncryptedTransaction.TransactionMetadata.class);

        EncryptedTransaction transaction = new EncryptedTransaction(transactionId, seller,
            buyer, createdBy, encryptedContent, metadata);

        String username = context.getProperty("user").toString();
        if (username.equals(buyer) || username.equals(seller)) {
          EncryptedTransaction.TransactionMetadata filteredMetadata =
              filterMetadataForParty(transaction, username);
          EncryptedTransaction filteredTx = new EncryptedTransaction(
              transactionId, seller, buyer, createdBy, encryptedContent, filteredMetadata);
          return Response.ok(filteredTx).build();
        }

        PermissionResponse response = verifyPermission(transaction.getShareList(),
            transaction.getGroupShareList(), username);

        if (!response.allowed()) {
          System.out.println("[ServerResource] Access denied for user " + username);
          return Response.status(Response.Status.FORBIDDEN).build();
        }

        // Third party only after signatures exist
        if (!(transaction.signedByBuyer() && transaction.signedBySeller())) {
          return Response.status(Response.Status.UNAUTHORIZED)
              .entity("Unable to fetch transaction: integrity signatures still missing").build();
        }

        EncryptedTransaction.TransactionMetadata filteredMetadata =
            filterMetadataForThirdParty(transaction, username, response.groupId());
        EncryptedTransaction filteredTransaction = new EncryptedTransaction(
            transactionId, seller, buyer, createdBy, encryptedContent, filteredMetadata);

        return Response.ok(filteredTransaction).build();
      }

      return Response.status(Response.Status.NOT_FOUND).entity("Transaction not found").build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Metadata for seller/buyer (sharedKeys, sharedLogs and own signature)
   */
  private EncryptedTransaction.TransactionMetadata filterMetadataForParty(
      EncryptedTransaction originalTransaction, String username) {

    if (originalTransaction == null) {
      return null;
    }

    EncryptedTransaction filtered = new EncryptedTransaction();

    if (originalTransaction.getSharedKeys() != null) {
      for (SharedKey k : originalTransaction.getSharedKeys()) {
        filtered.addSharedKey(k);
      }
    }
    if (originalTransaction.getShareList() != null) {
      for (ShareLog log : originalTransaction.getShareList()) {
        filtered.addShareLog(log);
      }
    }
    if (originalTransaction.getGroupShareList() != null) {
      for (ShareLog log : originalTransaction.getGroupShareList()) {
        filtered.addGroupShareLog(log);
      }
    }

    boolean isSeller = username.equals(originalTransaction.getSeller());
    boolean isBuyer  = username.equals(originalTransaction.getBuyer());

    if (isSeller && originalTransaction.signedBySeller()) {
      filtered.addSellerSignature(originalTransaction.getSellerSignature());
    }
    if (isBuyer && originalTransaction.signedByBuyer()) {
      filtered.addBuyerSignature(originalTransaction.getBuyerSignature());
    }

    return filtered.getMetadata();
  }

  /**
   * Metadata for 3rd parties (only their own sharedKey)
   */
  private EncryptedTransaction.TransactionMetadata filterMetadataForThirdParty(
      EncryptedTransaction originalTransaction, String username, String groupId) {
    if (originalTransaction == null) {
      return null;
    }

    EncryptedTransaction filtered = new EncryptedTransaction();

    if (originalTransaction.getSharedKeys() != null) {
      for (SharedKey k : originalTransaction.getSharedKeys()) {
        if (username.equals(k.getForCompany()) ||
            (groupId != null && !groupId.isEmpty() && groupId.equals(k.getForCompany()))) {
          filtered.addSharedKey(k);
          break;
        }
      }
    }
    return filtered.getMetadata();
  }

  private PermissionResponse verifyPermission(List<ShareLog> logs, List<ShareLog> groupLogs,
                                              String username) {
    if (logs != null) {
      for (ShareLog log : logs) {
        if (username.equals(log.getSharedWith())) {
          return new PermissionResponse(true, "");
        }
      }
    }

    List<String> groupIds = new ArrayList<>();
    if (groupLogs != null) {
      for (ShareLog log : groupLogs) {
        groupIds.add(log.getSharedWith());
      }
    }
    if (groupIds.isEmpty()) {
      return new PermissionResponse(false, "");
    }
    GroupServerClient client = getGroupServerClient();
    GroupServerClient.GroupResponse groupResponse = client.isInGroups(username, groupIds);
    if (groupResponse.found()) {
      return new PermissionResponse(true, groupResponse.group());
    }

    return new PermissionResponse(false, "");
  }

  private record PermissionResponse(boolean allowed, String groupId) {
  }

  @Override
  public Response signTransaction(ContainerRequestContext context, String transactionId,
                                  String signature) {
    System.out.println(
        "[ServerResource] Sign transaction request received for transaction: '" + transactionId +
            "'");

    try (Connection conn = Database.getConnection()) {
      conn.setAutoCommit(false);

      String selectSql =
          "SELECT buyer, seller, encrypted_content, metadata FROM transactions " +
              "WHERE transaction_id = ? FOR UPDATE";
      PreparedStatement selectStmt = conn.prepareStatement(selectSql);
      selectStmt.setString(1, transactionId);
      ResultSet rs = selectStmt.executeQuery();

      if (rs.next()) {
        Gson gson = new Gson();

        String seller = rs.getString("seller");
        String buyer = rs.getString("buyer");
        String encryptedContent = rs.getString("encrypted_content");
        String metadataJson = rs.getString("metadata");

        EncryptedTransaction.TransactionMetadata metadata = gson.fromJson(
            metadataJson, EncryptedTransaction.TransactionMetadata.class);

        String username = context.getProperty("user").toString();
        if (!(username.equals(seller) || username.equals(buyer))) {
          return Response.status(Response.Status.UNAUTHORIZED)
              .entity("Not authorized to sign this transaction").build();
        }

        // Build temp transaction
        EncryptedTransaction temp = new EncryptedTransaction(
            transactionId, seller, buyer, null, encryptedContent, metadata);

        // Prevent duplicate signatures
        if (username.equals(seller) && temp.signedBySeller()) {
          return Response.status(Response.Status.CONFLICT)
              .entity("Seller already signed").build();
        }
        if (username.equals(buyer) && temp.signedByBuyer()) {
          return Response.status(Response.Status.CONFLICT)
              .entity("Buyer already signed").build();
        }

        // Verify signature
        @SuppressWarnings("unchecked")
        Map<String, Object> secureDocument =
            gson.fromJson(encryptedContent, Map.class);

        String pubKeyPath = KeyUtils.getPublicKeyPathForCompany(username);
        boolean ok = Check.check(secureDocument, signature, pubKeyPath);
        if (!ok) {
          return Response.status(Response.Status.BAD_REQUEST)
              .entity("Invalid agreement signature").build();
        }

        if (username.equals(seller)) {
          temp.addSellerSignature(signature);
        } else if (username.equals(buyer)) {
          temp.addBuyerSignature(signature);
        }

        EncryptedTransaction.TransactionMetadata updatedMetadata = temp.getMetadata();
        String updateMetadataJson = gson.toJson(updatedMetadata);

        // update transaction metadata with new signature
        String updateSql = "UPDATE transactions SET metadata = ? WHERE transaction_id = ?";
        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
        updateStmt.setObject(1, updateMetadataJson, java.sql.Types.OTHER);
        updateStmt.setString(2, transactionId);
        updateStmt.executeUpdate();
        conn.commit();

        return Response.ok().build();
      }

      return Response.status(Response.Status.NOT_FOUND).entity("Transaction not found").build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  @Override
  public Response shareWithThirdParty(ContainerRequestContext context, String transactionId,
                                      ShareRequest shareRequest) {
    System.out.println(
        "[ServerResource] Share with third party request received for  transaction: '" +
            transactionId + "'");
    return shareWithRecipient(context, transactionId, shareRequest, false);
  }

  @Override
  public Response shareWithGroup(ContainerRequestContext context, String transactionId,
                                 ShareRequest shareRequest) {
    System.out.println(
        "[ServerResource] Share with group request received for  transaction: '" + transactionId +
            "'");

    return shareWithRecipient(context, transactionId, shareRequest, true);
  }

  private Response shareWithRecipient(ContainerRequestContext context, String transactionId,
                                      ShareRequest shareRequest, boolean sharingWithGroup) {
    ShareLog shareLog = shareRequest.getShareLog();
    SharedKey sharedKey = shareRequest.getSharedKey();

    String username = context.getProperty("user").toString();
    if (shareLog == null || sharedKey == null) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("ShareLog and SharedKey must be provided").build();
    }

    if (!username.equals(shareLog.getSharedBy())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("User sharing the transaction must match the ShareLog entry").build();
    }

    // Ensure the SharedKey is bound to the same target as the ShareLog
    String shareWith = shareLog.getSharedWith();
    if (!shareWith.equals(sharedKey.getForCompany())) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("SharedKey 'forCompany' must match ShareLog 'sharedWith'").build();
    }

    try (Connection conn = Database.getConnection()) {
      conn.setAutoCommit(false);

      String selectSql =
          "SELECT buyer, seller, encrypted_content, metadata FROM transactions " +
              "WHERE transaction_id = ? FOR UPDATE";
      PreparedStatement selectStmt = conn.prepareStatement(selectSql);
      selectStmt.setString(1, transactionId);
      ResultSet rs = selectStmt.executeQuery();

      if (rs.next()) {
        Gson gson = new Gson();

        String seller = rs.getString("seller");
        String buyer = rs.getString("buyer");
        String encryptedContent = rs.getString("encrypted_content");
        String metadataJson = rs.getString("metadata");

        EncryptedTransaction.TransactionMetadata metadata = gson.fromJson(
            metadataJson, EncryptedTransaction.TransactionMetadata.class);

        if (!(username.equals(seller) || username.equals(buyer))) {
          return Response.status(Response.Status.FORBIDDEN)
              .entity("Not authorized to share this transaction").build();
        }

        // Verify share signature
        String sharedBy = shareLog.getSharedBy();
        @SuppressWarnings("unchecked")
        Map<String, Object> secureDocument = gson.fromJson(encryptedContent, Map.class);
        long txTimestamp = Long.parseLong(secureDocument.get("timestamp").toString());
        String dataToVerify =
            transactionId + "|" + txTimestamp + "|" + sharedBy + "|" + shareWith;

        String sharedByPubKeyPath = KeyUtils.getPublicKeyPathForCompany(sharedBy);
        PublicKey sharedByPubKey = KeyUtils.loadPublicKeyFromFile(sharedByPubKeyPath);

        boolean shareOk = CryptoUtils.verifySignature(
            sharedByPubKey, dataToVerify, shareLog.getSignature());
        if (!shareOk) {
          return Response.status(Response.Status.BAD_REQUEST)
              .entity("Invalid share signature").build();
        }

        // build temporary transaction to update metadata and check conditions
        EncryptedTransaction temp = new EncryptedTransaction(
            transactionId, seller, buyer, null, encryptedContent, metadata);

        List<ShareLog> logs;
        if (sharingWithGroup) {
          logs = temp.getGroupShareList();
          if (alreadySharedWith(logs, shareWith)) {
            return Response.status(Response.Status.CONFLICT)
                .entity("Group already has access to the transaction").build();
          }
          temp.addGroupShareLog(shareLog);
        } else {
          logs = temp.getShareList();
          if (shareWith.equals(seller) || shareWith.equals(buyer) ||
              alreadySharedWith(logs, shareWith)) {
            return Response.status(Response.Status.CONFLICT)
                .entity("User already has access to the transaction").build();
          }
          temp.addShareLog(shareLog);
        }
        temp.addSharedKey(sharedKey);

        EncryptedTransaction.TransactionMetadata updatedMetadata = temp.getMetadata();
        String updateMetadataJson = gson.toJson(updatedMetadata);
        String updateSql = "UPDATE transactions SET metadata = ? WHERE transaction_id = ?";
        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
        updateStmt.setObject(1, updateMetadataJson, java.sql.Types.OTHER);
        updateStmt.setString(2, transactionId);
        updateStmt.executeUpdate();
        conn.commit();

        return Response.ok().build();
      }
      return Response.status(Response.Status.NOT_FOUND).build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  private boolean alreadySharedWith(List<ShareLog> logs, String sharedWith) {
    if (logs != null) {
      for (ShareLog log : logs) {
        if (log.getSharedWith().equals(sharedWith)) {
          return true;
        }
      }
    }
    return false;
  }
}

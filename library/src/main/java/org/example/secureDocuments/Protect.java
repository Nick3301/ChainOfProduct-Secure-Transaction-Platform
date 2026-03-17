package org.example.secureDocuments;

import org.example.utils.CryptoUtils;
import org.example.utils.JsonUtils;
import org.example.utils.KeyUtils;

import java.security.PrivateKey;
import java.util.*;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Crypto library: protect a document by:
 *  - generating a fresh AES key K_tx
 *  - encrypting {transaction, integrity_hash} with AES(K_tx)
 *  - producing a secureDocument
 *  - computing the sender's agreement signature over secureDocument (outside of secureDocument)
 */
public class Protect {
    
    public static ProtectResult protect(
        Map<String, Object> document,
        String senderPrivateKeyPath
    ) throws Exception {
        
        // Check required keys
        Set<String> requiredKeys = new HashSet<>();
        requiredKeys.add("sender");
        requiredKeys.add("receiver");
        requiredKeys.add("timestamp");
        requiredKeys.add("seq_num");
        requiredKeys.add("content");
        
        if (!document.keySet().containsAll(requiredKeys)) {
            throw new IllegalArgumentException(
                "Invalid input format. The input file should have the following keys: " +
                "sender, receiver, timestamp, seq_num, content."
            );
        }

        // Load private key
        PrivateKey senderPrivateKey = KeyUtils.loadPrivateKeyFromFile(senderPrivateKeyPath);

        // Generate fresh symmetric key K_tx for this document
        KeyGenerator keyGen = KeyGenerator.getInstance(CryptoUtils.AES_ALGORITHM);
        keyGen.init(256);
        SecretKey transactionKey = keyGen.generateKey();

        // Create payload with transaction and integrity hash
        String originalContent = (String) document.get("content");
        String contentHash = CryptoUtils.generateSHA256Hash(originalContent);
        
        // Create payload containing both transaction and hash
        Map<String, Object> payload = new HashMap<>();
        payload.put("transaction", originalContent);
        payload.put("integrity_hash", contentHash);
        
        // Convert payload to JSON and encrypt with AES
        String payloadJson = JsonUtils.toSortedJson(payload);
        String contentCipher = CryptoUtils.encryptAES(transactionKey, payloadJson);

        // Create secure document
        Map<String, Object> secureDocument = new HashMap<>();
        secureDocument.put("sender", document.get("sender"));
        secureDocument.put("receiver", document.get("receiver"));
        secureDocument.put("timestamp",  String.valueOf(document.get("timestamp")));
        secureDocument.put("seq_num",  String.valueOf(document.get("seq_num")));
        secureDocument.put("content_cipher", contentCipher);

        // Sort the document for consistent ordering
        secureDocument = JsonUtils.sortDocument(secureDocument);

        // Sender's agreement signature over secureDocument
        String jsonString = JsonUtils.toSortedJson(secureDocument);
        String senderSignature = CryptoUtils.signData(senderPrivateKey, jsonString);

        return new ProtectResult(secureDocument, senderSignature, transactionKey);
    }
}

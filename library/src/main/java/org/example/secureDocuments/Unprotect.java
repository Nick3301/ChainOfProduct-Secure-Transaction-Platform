package org.example.secureDocuments;

import org.example.utils.CryptoUtils;
import org.example.utils.JsonUtils;
import org.example.utils.KeyUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.security.PublicKey;
import javax.crypto.SecretKey;

public class Unprotect {
    
    public static Map<String, Object> unprotect(
        Map<String, Object> secureDocument,
        SecretKey transactionKey
    ) throws Exception {
        
        // Check required fields in secure document
        Set<String> requiredFields = new HashSet<>();
        requiredFields.add("sender");
        requiredFields.add("receiver");
        requiredFields.add("timestamp");
        requiredFields.add("seq_num");
        requiredFields.add("content_cipher");
        
        if (!secureDocument.keySet().containsAll(requiredFields)) {
            throw new IllegalArgumentException(
                "Invalid secure document format. Missing required fields."
            );
        }

        // Decrypt content using AES
        String encryptedContent = (String) secureDocument.get("content_cipher");
        String decryptedPayloadJson = CryptoUtils.decryptAES(transactionKey, encryptedContent);
        
        // Parse decrypted payload using utility class
        String transaction = JsonUtils.extractJsonField(decryptedPayloadJson, "transaction");
        String storedHash = JsonUtils.extractJsonField(decryptedPayloadJson, "integrity_hash");
        
        if (transaction == null || storedHash == null) {
            throw new IllegalArgumentException("Invalid payload format. Missing transaction or integrity_hash.");
        }

        // Verify integrity by comparing hashes using utility class
        String computedHash = CryptoUtils.generateSHA256Hash(transaction);
        if (!computedHash.equals(storedHash)) {
            throw new SecurityException("Document integrity verification failed. Content has been tampered with.");
        }

        // Create and return original document structure
        Map<String, Object> originalDocument = new HashMap<>();
        originalDocument.put("sender", secureDocument.get("sender"));
        originalDocument.put("receiver", secureDocument.get("receiver"));
        originalDocument.put("timestamp", secureDocument.get("timestamp"));
        originalDocument.put("seq_num", secureDocument.get("seq_num"));
        originalDocument.put("content", transaction);

        return originalDocument;
    }

    /**
     * Variant: first verify a given signature with a given public key,
     * then decrypt and verify the inner integrity hash.
    */
    public static Map<String, Object> unprotect(
        Map<String, Object> secureDocument,
        SecretKey transactionKey,
        String signature,
        String signerPublicKeyPath
    ) throws Exception {
        boolean ok = Check.check(secureDocument, signature, signerPublicKeyPath);
        if (!ok) {
            throw new SecurityException("Signature verification failed");
        }
        return unprotect(secureDocument, transactionKey);
    }
}

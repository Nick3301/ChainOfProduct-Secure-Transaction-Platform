package org.example.secureDocuments;

import org.example.utils.CryptoUtils;
import org.example.utils.JsonUtils;
import org.example.utils.KeyUtils;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.security.PublicKey;

public class Check {
    
    public static boolean check(
        Map<String, Object> secureDocument,
        String signature,
        String signerPublicKeyPath
    ) throws Exception {
        
        // Check required fields in secure document
        Set<String> requiredFields = new HashSet<>();
        requiredFields.add("sender");
        requiredFields.add("receiver");
        requiredFields.add("timestamp");
        requiredFields.add("seq_num");
        requiredFields.add("content_cipher");
        
        // Validate document structure
        if (!secureDocument.keySet().equals(requiredFields)) {
            throw new IllegalArgumentException("Invalid secure document structure");
        }

        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Signature cannot be null or empty");
        }

        // Load sender's public key for RSA signature verification
        PublicKey signerPublicKey = KeyUtils.loadPublicKeyFromFile(signerPublicKeyPath);

        // Convert to JSON and verify RSA signature
        String jsonForVerification = JsonUtils.toSortedJson(secureDocument);
        
        return CryptoUtils.verifySignature(signerPublicKey, jsonForVerification, signature);
    }
}
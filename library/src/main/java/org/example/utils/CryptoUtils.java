package org.example.utils;

import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec; 

/**
 * Utility class for hybrid cryptographic operations (AES + RSA)
 */
public class CryptoUtils {
    
    // Algorithm constants
    public static final String AES_ALGORITHM = "AES";
    public static final String AES_TRANSFORMATION = "AES/CBC/PKCS5Padding";
    public static final String RSA_ALGORITHM = "RSA";
    public static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    public static final String HASH_ALGORITHM = "SHA-256";
    public static final String ENCODING = "UTF-8";
    public static final int IV_SIZE = 16; // 128 bits for AES
    
    /**
     * Encrypts plaintext using AES symmetric key
     */
    public static String encryptAES(SecretKey secretKey, String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        
        // Generate random IV
        byte[] iv = new byte[IV_SIZE];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(ENCODING));
        
        // Prepend IV to encrypted data
        byte[] combined = new byte[IV_SIZE + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, IV_SIZE);
        System.arraycopy(encrypted, 0, combined, IV_SIZE, encrypted.length);
        
        return Base64.getEncoder().encodeToString(combined);
    }
    
    /**
     * Decrypts ciphertext using AES symmetric key
     */
    public static String decryptAES(SecretKey secretKey, String ciphertext) throws Exception {
        byte[] combined = Base64.getDecoder().decode(ciphertext);
        
        // Extract IV from the beginning
        byte[] iv = new byte[IV_SIZE];
        byte[] encrypted = new byte[combined.length - IV_SIZE];
        System.arraycopy(combined, 0, iv, 0, IV_SIZE);
        System.arraycopy(combined, IV_SIZE, encrypted, 0, encrypted.length);
        
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
        byte[] decrypted = cipher.doFinal(encrypted);
        
        return new String(decrypted, ENCODING);
    }
    
    /**
     * Creates digital signature using RSA private key
     */
    public static String signData(PrivateKey privateKey, String data) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data.getBytes(ENCODING));
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }
    
    /**
     * Verifies digital signature using RSA public key
     */
    public static boolean verifySignature(PublicKey publicKey, String data, String signatureB64) throws Exception {
        byte[] signatureBytes;
        try{
            signatureBytes = Base64.getDecoder().decode(signatureB64);
        }
        catch (IllegalArgumentException e){
            throw new IllegalArgumentException("Signature string is not valid Base64", e);
        }
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(data.getBytes(ENCODING));
        
        return signature.verify(signatureBytes);
    }
    
    /**
     * Generates SHA-256 hash of input data
     */
    public static String generateSHA256Hash(String data) throws Exception {
        MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
        byte[] dataBytes = data.getBytes(ENCODING);
        messageDigest.update(dataBytes);
        byte[] digest = messageDigest.digest();
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Encrypts a symmetric AES key using RSA public key
     */
    public static String encryptKeyRSA(PublicKey publicKey, SecretKey secretKey) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(secretKey.getEncoded());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * Decrypts a symmetric AES key using RSA private key
     */
    public static SecretKey decryptKeyRSA(PrivateKey privateKey, String encKeyB64) throws Exception {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] encrypted = Base64.getDecoder().decode(encKeyB64);
        byte[] keyBytes = cipher.doFinal(encrypted);
        return new SecretKeySpec(keyBytes, AES_ALGORITHM);
    }
}
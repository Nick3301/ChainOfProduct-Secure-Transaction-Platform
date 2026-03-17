package org.example.utils;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class for key management (both symmetric and asymmetric)
 */
public class KeyUtils {

    private static final String KEYS_DIR = "../keys";
    
    /**
     * Loads AES secret key from binary file
     */
    public static SecretKey loadSecretKeyFromFile(String keyPath) throws Exception {
        byte[] keyBytes = FileUtils.readFile(keyPath);
        return new SecretKeySpec(keyBytes, CryptoUtils.AES_ALGORITHM);
    }
    
    /**
     * Loads RSA private key from binary file (PKCS8 format)
     */
    public static PrivateKey loadPrivateKeyFromFile(String privateKeyPath) throws Exception {
        byte[] privEncoded = FileUtils.readFile(privateKeyPath);
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privEncoded);
        KeyFactory keyFactory = KeyFactory.getInstance(CryptoUtils.RSA_ALGORITHM);
        return keyFactory.generatePrivate(privSpec);
    }
    
    /**
     * Loads RSA public key from binary file (X.509 format)
     */
    public static PublicKey loadPublicKeyFromFile(String publicKeyPath) throws Exception {
        byte[] pubEncoded = FileUtils.readFile(publicKeyPath);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFactory = KeyFactory.getInstance(CryptoUtils.RSA_ALGORITHM);
        return keyFactory.generatePublic(pubSpec);
    }

    // Derive paths from company ID
    public static String getPrivateKeyPathForCompany(String companyId) {
        return KEYS_DIR + "/" + companyId + "_private.der";
    }

    public static String getPublicKeyPathForCompany(String companyId) {
        return KEYS_DIR + "/" + companyId + "_public.der";
    }

    /**
     * Build an RSA PublicKey from a Base64-encoded DER string.
     */
    public static PublicKey loadPublicKeyFromBase64(String publicKeyB64) throws Exception {
        byte[] pubEncoded = Base64.getDecoder().decode(publicKeyB64);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubEncoded);
        KeyFactory keyFactory = KeyFactory.getInstance(CryptoUtils.RSA_ALGORITHM);
        return keyFactory.generatePublic(pubSpec);
    }
}
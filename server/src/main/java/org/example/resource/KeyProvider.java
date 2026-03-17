package org.example.resource;

import io.github.cdimascio.dotenv.Dotenv;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * KeyProvider is the class used to load the generated key from the .env file and sign it so that
 * it can be used to generate the authorization tokens
 */
public class KeyProvider {

  private static SecretKey key;

  public static synchronized SecretKey get() {
    if (key == null) {
      String secretString = null;

      try {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        secretString = dotenv.get("JWT_SECRET");
      } catch (Exception e) {
        System.out.println("[KeyProvider] .env file not found or invalid.");
      }

      if (secretString != null && !secretString.isEmpty()) {
        try {
          byte[] decodedKey = Base64.getDecoder().decode(secretString);
          key = Keys.hmacShaKeyFor(decodedKey);
          System.out.println("[KeyProvider] Loaded JWT Key from configuration.");
        } catch (Exception e) {
          System.err.println("[KeyProvider] Key found but invalid Base64. Falling back to random.");
        }
      }

      if (key == null) {
        System.err.println("[KeyProvider] WARNING: No JWT_SECRET found in .env or System. Using temporary " +
            "random key.");
        key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
      }
    }
    return key;
  }
}
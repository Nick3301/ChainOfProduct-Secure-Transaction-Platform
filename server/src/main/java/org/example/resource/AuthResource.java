package org.example.resource;

import io.jsonwebtoken.Jwts;
import jakarta.ws.rs.core.Response;
import org.example.api.AuthApi;
import org.example.database.Database;
import com.google.gson.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;

/**
 * AuthResource is the class that implements the execution of the authentication requests - login
 * and register.
 */
public class AuthResource implements AuthApi {

  private static final int ITERATIONS = 600000;
  private static final int KEY_LENGTH = 256;
  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";


  private String hashPassword(String password) throws NoSuchAlgorithmException, InvalidKeySpecException {
    SecureRandom random = new SecureRandom();
    byte[] salt = new byte[16];
    random.nextBytes(salt);

    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
    byte[] hash = factory.generateSecret(spec).getEncoded();

    // Store salt and hash together, separated by :
    return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
  }

  private boolean verifyPassword(String password, String storedHash) throws NoSuchAlgorithmException, InvalidKeySpecException {
    String[] parts = storedHash.split(":");
    if (parts.length != 2) {
      return false;
    }

    byte[] salt = Base64.getDecoder().decode(parts[0]);
    byte[] hash = Base64.getDecoder().decode(parts[1]);

    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
    SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
    byte[] testHash = factory.generateSecret(spec).getEncoded();

    // Constant-time comparison
    int diff = hash.length ^ testHash.length;
    for (int i = 0; i < hash.length && i < testHash.length; i++) {
      diff |= hash[i] ^ testHash[i];
    }
    return diff == 0;
  }

  @Override
  public Response register(String username, String password) {
    System.out.println("[AuthResource] Register request received for user: '" + username + "'");
    try (Connection conn = Database.getConnection()) {

      // verify if the username is already taken
      String checkSQL = "SELECT COUNT(*) FROM users WHERE username = ?";
      PreparedStatement checkStmt = conn.prepareStatement(checkSQL);
      checkStmt.setString(1, username);
      ResultSet rs = checkStmt.executeQuery();
      if (rs.next() && rs.getInt(1) > 0) {
        return Response.status(Response.Status.CONFLICT).entity("Username already exists.").build();
      }

      // hash the password before storing using PBKDF2
      String hashedPassword = hashPassword(password);

      String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
      PreparedStatement stmt = conn.prepareStatement(sql);
      stmt.setString(1, username);
      stmt.setString(2, hashedPassword);
      stmt.executeUpdate();

      return Response.ok("User created").build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(500).entity("DB Error").build();
    }
  }

  @Override
  public Response login(String username, String password) {
    System.out.println("[AuthResource] Login request received for user: '" + username + "'");
    try (Connection conn = Database.getConnection()) {
      String sql = "SELECT password FROM users WHERE username = ?";
      PreparedStatement stmt = conn.prepareStatement(sql);
      stmt.setString(1, username);

      ResultSet rs = stmt.executeQuery();

      if (rs.next()) { // user is found
        String storedPassword = rs.getString(1);
        if (verifyPassword(password, storedPassword)) {
          // TODO add JSON building as in the labs code
          String token = Jwts.builder()
              .subject(username)
              .issuedAt(new Date())
              .expiration(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
              .signWith(KeyProvider.get())
              .compact();

          JsonObject jsonObject = new JsonObject();
          jsonObject.addProperty("token", token);

          Gson gson = new Gson();
          String jsonString = gson.toJson(jsonObject);

          return Response.ok(jsonString).build();
        }
      }
      return Response.status(401).entity("Invalid credentials").build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(500).build();
    }
  }
}

package org.example.clients;

import com.google.gson.Gson;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.context.ClientContext;
import org.example.objects.GroupMembershipStatus;
import org.glassfish.jersey.gson.JsonGsonFeature;

import java.util.Base64;
import java.util.List;

public class GroupServerClient {

  private static final String BASE_PATH = "/group-server";

  private final ClientContext context;
  private final WebTarget baseTarget;

  public GroupServerClient(ClientContext context) {
    this.context = context;
    Client client = ClientBuilder.newClient().register(JsonGsonFeature.class);
    this.baseTarget = client.target(context.getGroupServerUri()).path(BASE_PATH);
  }

  /**
   * Create a new group with an initial member list.
   */
  public void createGroup(String groupName, List<String> members) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[GROUP SERVER] Not logged in");
      return;
    }

    WebTarget target = baseTarget.path("groups").path(groupName);
    System.out.println("[GROUP SERVER] POST " + target.getUri());

    try (Response response = target.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .post(Entity.entity(members, MediaType.APPLICATION_JSON))) {

      int status = response.getStatus();
      String body = response.hasEntity() ? response.readEntity(String.class) : "";

      if (status == 201 || status == 200) {
        System.out.println("[GROUP SERVER] Group created successfully.");
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Response: " + body);
        }
      } else {
        System.out.println("[GROUP SERVER] Failed to create group. HTTP " + status);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Server said: " + body);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Retrieve the Group Server's public key (Base64 string).
   */
  public String getGroupServerPublicKey() {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[GROUP SERVER] Not logged in");
      return null;
    }

    WebTarget target = baseTarget.path("public-key");
    System.out.println("[GROUP SERVER] GET " + target.getUri());

    try (Response response = target.request(MediaType.TEXT_PLAIN)
        .header("Authorization", "Bearer " + token)
        .get()) {

      int status = response.getStatus();
      String body = response.hasEntity() ? response.readEntity(String.class) : "";

      if (status == 200) {
        // Body is the raw Base64 string (or JSON string); if it has quotes, strip them.
        String keyB64 = body.trim();
        if (keyB64.startsWith("\"") && keyB64.endsWith("\"") && keyB64.length() >= 2) {
          keyB64 = keyB64.substring(1, keyB64.length() - 1);
        }
        System.out.println("[GROUP SERVER] Public key fetched from group server.");
        return keyB64;
      } else {
        System.out.println("[GROUP SERVER] Failed to fetch public key. HTTP " + status);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Server said: " + body);
        }
        return null;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Check membership of a user in a group.
   */
  public GroupMembershipStatus isMember(String groupName, String username) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[GROUP SERVER] Not logged in");
      return null;
    }

    WebTarget target = baseTarget.path("groups").path(groupName).path("members").path(username);

    try (Response response = target.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .get()) {

      int status = response.getStatus();
      if (status == 200) {
        GroupMembershipStatus statusObj = response.readEntity(GroupMembershipStatus.class);
        System.out.println("[GROUP SERVER] Membership: user " + username + " in " + groupName +
            " -> " + statusObj.isMember());
        return statusObj;
      } else {
        String body = response.hasEntity() ? response.readEntity(String.class) : "";
        System.out.println("[GROUP SERVER] Failed to check membership. HTTP " + status);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Server said: " + body);
        }
        return null;
      }

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Add a member to an existing group.
   */
  public void addMember(String groupName, String username) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[GROUP SERVER] Not logged in");
      return;
    }

    WebTarget target = baseTarget
        .path("groups").path(groupName)
        .path("members").path(username);

    System.out.println("[GROUP SERVER] POST " + target.getUri());

    try (Response response = target.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .post(null)) {

      int status = response.getStatus();
      String body = response.hasEntity() ? response.readEntity(String.class) : "";
      if (status == 200) {
        System.out.println("[GROUP SERVER] Added " + username + " to group " + groupName);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Response: " + body);
        }
      } else {
        System.out.println("[GROUP SERVER] Failed to add member. HTTP " + status);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Server said: " + body);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Remove a member from an existing group.
   */
  public void removeMember(String groupName, String username) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[GROUP SERVER] Not logged in");
      return;
    }

    WebTarget target = baseTarget
        .path("groups").path(groupName)
        .path("members").path(username);

    System.out.println("[GROUP SERVER] DELETE " + target.getUri());

    try (Response response = target.request(MediaType.APPLICATION_JSON)
        .header("Authorization", "Bearer " + token)
        .delete()) {

      int status = response.getStatus();
      String body = response.hasEntity() ? response.readEntity(String.class) : "";
      if (status == 200) {
        System.out.println("[GROUP SERVER] Removed " + username + " from group " + groupName);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Response: " + body);
        }
      } else {
        System.out.println("[GROUP SERVER] Failed to remove member. HTTP " + status);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Server said: " + body);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Ask the group server to decrypt (unwrap) an encrypted key.
   * Returns the transaction key as raw bytes (decoded from Base64).
   */
  public byte[] decryptTransactionKey(String encryptedPayloadB64) {
    String token = context.getJwtToken();
    if (token == null) {
      System.out.println("[GROUP SERVER] Not logged in");
      return null;
    }

    String username = context.getCurrentUser();
    if (username == null || username.isEmpty()) {
      System.out.println("[GROUP SERVER] Username not available in context");
      return null;
    }

    WebTarget target = baseTarget.path("decrypt").queryParam("username", username);

    try (Response response = target.request(MediaType.TEXT_PLAIN)
        .header("Authorization", "Bearer " + token)
        .post(Entity.entity(encryptedPayloadB64, MediaType.TEXT_PLAIN))) {

      int status = response.getStatus();
      String body = response.hasEntity() ? response.readEntity(String.class) : "";

      if (status == 200) {
        // body is base64-encoded transaction key
        String keyB64 = body.trim();
        if (keyB64.startsWith("\"") && keyB64.endsWith("\"") && keyB64.length() >= 2) {
          keyB64 = keyB64.substring(1, keyB64.length() - 1);
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyB64);
        System.out.println("[GROUP SERVER] Group key decrypted successfully");
        return keyBytes;
      } else {
        System.out.println("[GROUP SERVER] Failed to decrypt group key. HTTP " + status);
        if (!body.isEmpty()) {
          System.out.println("[GROUP SERVER] Server said: " + body);
        }
        return null;
      }

    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
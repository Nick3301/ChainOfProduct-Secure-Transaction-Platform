package org.example.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import org.example.api.GroupServerAPI;
import org.example.storage.GroupStore;
import org.example.utils.CryptoUtils;
import org.example.utils.KeyUtils;
import org.example.objects.GroupMembershipStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Base64;
import java.util.List;

public class GroupServerResource implements GroupServerAPI {

  private static final String KEYS_DIRECTORY = "../keys/";
  private final GroupStore groupStore;

  public GroupServerResource() {
    this.groupStore = new GroupStore();
  }

  @Override
  public Response createGroup(String groupName, List<String> usernames) {
    System.out.println("[GroupResource] Create group request for group: '" + groupName + "'");
    try {
      // Validate inputs
      if (groupName == null || groupName.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\": \"Group name cannot be empty\"}")
            .build();
      }

      if (usernames == null || usernames.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\": \"Usernames list cannot be empty\"}")
            .build();
      }

      // Store the group members using GroupStore
      groupStore.addGroup(groupName, usernames);

      // Return success response
      String response = String.format(
          "{\"message\": \"Group created successfully\", \"groupName\": \"%s\"}",
          groupName
      );
      return Response.status(Response.Status.CREATED)
          .entity(response)
          .build();

    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\": \"" + e.getMessage() + "\"}")
          .build();
    } catch (IOException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\": \"Failed to store group data: " + e.getMessage() + "\"}")
          .build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\": \"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }

  @Override
  public Response getGroupServerPublicKey() {
    System.out.println("[GroupResource] Get group server public key request");
    try {
      // Read public key from file
      Path pubPath = Paths.get(KEYS_DIRECTORY, "group_server_public.der");

      if (!Files.exists(pubPath)) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("{\"error\": \"Public key not found\"}")
            .build();
      }

      PublicKey publicKey = KeyUtils.loadPublicKeyFromFile(pubPath.toString());
      String pubB64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

      return Response.status(Response.Status.OK)
          .entity(pubB64)
          .build();

    } catch (IOException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\": \"Failed to read public key: " + e.getMessage() + "\"}")
          .build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\": \"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }

  @Override
  public Response isMember(String groupName, String username) {
    System.out.println("[GroupResource] Is member request for user: '" + username + "' and group: '" + groupName + "'");
    try {
      if (groupName == null || groupName.isEmpty()
          || username == null || username.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Group name and username cannot be empty\"}")
            .build();
      }

      boolean member = groupStore.isMember(username, groupName);
      GroupMembershipStatus resp = new GroupMembershipStatus(groupName, username, member);
      return Response.ok(resp).build();

    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }

  @Override
  public Response addMember(String groupName, String username) {
    try {
      if (groupName == null || groupName.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Group name cannot be empty\"}")
            .build();
      }

      if (username == null || username.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"username cannot be empty\"}")
            .build();
      }

      groupStore.addMember(username, groupName);
      String response = String.format(
          "{\"message\":\"User '%s' added to group '%s' successfully\"}",
          username, groupName
      );
      return Response.status(Response.Status.OK)
          .entity(response)
          .build();

    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"" + e.getMessage() + "\"}")
          .build();
    } catch (IOException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Failed to add member: " + e.getMessage() + "\"}")
          .build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }

  @Override
  public Response removeMember(String groupName, String username) {
    try {
      if (groupName == null || groupName.isEmpty()
          || username == null || username.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Group name and username cannot be empty\"}")
            .build();
      }

      groupStore.removeMember(username, groupName);
      String response = String.format(
          "{\"message\":\"User '%s' removed from group '%s' successfully\"}",
          username, groupName
      );
      return Response.status(Response.Status.OK)
          .entity(response)
          .build();

    } catch (IllegalArgumentException e) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity("{\"error\":\"" + e.getMessage() + "\"}")
          .build();
    } catch (IOException e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Failed to remove member: " + e.getMessage() + "\"}")
          .build();
    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }

  @Override
  public Response decrypt(String username, String encryptedPayload) {
    System.out.println("[GroupResource] Decrypt request for user: '" + username + "'");
    try {
      if (username == null || username.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Username cannot be empty\"}")
            .build();
      }

      if (encryptedPayload == null || encryptedPayload.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Encrypted payload cannot be empty\"}")
            .build();
      }

      // Load Group Server private key
      Path privPath = Paths.get(KEYS_DIRECTORY, "group_server_private.der");
      java.security.PrivateKey groupServerPrivateKey = KeyUtils.loadPrivateKeyFromFile(privPath.toString());
      if (groupServerPrivateKey == null) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("{\"error\":\"Group Server private key not found\"}")
            .build();
      }

      // Decrypt the payload using Group Server's private key
      byte[] encryptedBytes = Base64.getDecoder().decode(encryptedPayload);
      javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(javax.crypto.Cipher.DECRYPT_MODE, groupServerPrivateKey);
      byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
      String decryptedJson = new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);

      // Parse JSON to extract groupName and transactionKey
      com.google.gson.Gson gson = new com.google.gson.Gson();
      @SuppressWarnings("unchecked")
      java.util.Map<String, String> payload = gson.fromJson(decryptedJson, java.util.Map.class);
      
      String groupName = payload.get("groupName");
      String transactionKeyB64 = payload.get("transactionKey");

      if (groupName == null || groupName.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Group name not found in payload\"}")
            .build();
      }

      if (transactionKeyB64 == null || transactionKeyB64.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Transaction key not found in payload\"}")
            .build();
      }

      // Check if caller is a member of the group
      if (!groupStore.isMember(username, groupName)) {
        return Response.status(Response.Status.FORBIDDEN)
            .entity("{\"error\":\"Caller '" + username + "' is not a member of group '" + groupName +
                "'\"}")
            .build();
      }

      // Return the transaction key (already in Base64)
      return Response.ok(transactionKeyB64).build();

    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }

  @Override
  public Response isInGroups(String username, List<String> groupIds) {
    System.out.println("[GroupResource] Checking if user: '" + username + "' is in groups: " +  groupIds.toString());
    try {
      if (groupIds == null || groupIds.isEmpty()
          || username == null || username.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\":\"Group list and username cannot be empty\"}")
            .build();
      }

      String groupName = groupStore.isInGroups(username, groupIds);
      if (groupName == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity("{\"error\":\"User is not in any of the given groups\"").build();
      }
      return Response.ok(groupName).build();

    } catch (Exception e) {
      e.printStackTrace();
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity("{\"error\":\"Internal server error: " + e.getMessage() + "\"}")
          .build();
    }
  }
}

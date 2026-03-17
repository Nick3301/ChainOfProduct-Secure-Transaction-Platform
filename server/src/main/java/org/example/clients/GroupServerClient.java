package org.example.clients;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.gson.JsonGsonFeature;

import java.net.URI;
import java.util.List;

public class GroupServerClient {

  private static final URI GROUP_SERVER_URI = URI.create("https://192.168.4.20:8081/");
  private static final String BASE_PATH = "/group-server";
  private final WebTarget baseTarget;

  public GroupServerClient() {
    Client client = ClientBuilder.newClient().register(JsonGsonFeature.class);
    this.baseTarget = client.target(GROUP_SERVER_URI).path(BASE_PATH);
  }

  public GroupResponse isInGroups(String username, List<String> groups) {
    System.out.println("[GroupServerClient] Sending request to Group Server: isInGroups for user: '" + username + "'");

    WebTarget target = baseTarget.path("/groups/check-user").queryParam("username", username);

    try (Response response = target.request(MediaType.APPLICATION_JSON_TYPE)
        .post(Entity.entity(groups, MediaType.APPLICATION_JSON))) {
      int status = response.getStatus();
      if (status == 200) {
        String group = response.readEntity(String.class);
        System.out.println("[GroupServerClient] Member found in group '" + group + "'");
        return new GroupResponse(true, group);
      } else {
        System.out.println("[GroupServerClient] Member not found in any group");
        return new GroupResponse(false, null);
      }
    } catch (Exception e) {
      e.printStackTrace();
      return new GroupResponse(false, null);
    }
  }

  public record GroupResponse(boolean found, String group) {
  }
}

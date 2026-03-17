package org.example.clients;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.context.ClientContext;
import org.glassfish.jersey.gson.JsonGsonFeature;

import java.io.IOException;

public class AuthorizationClient {

  private static final String AUTH_PATH = "/auth";
  private final ClientContext context;

  private final WebTarget baseTarget;

  public AuthorizationClient(ClientContext context) {
    this.context = context;

    Client client = ClientBuilder.newClient().register(JsonGsonFeature.class);

    this.baseTarget = client.target(context.getServerUri()).path(AUTH_PATH);
  }

  public void register(String username, String password) throws IOException, InterruptedException {
    WebTarget registerTarget = baseTarget.path("register").queryParam("username", username)
        .queryParam("password", password);

    try (Response response = registerTarget.request(MediaType.APPLICATION_JSON)
        .post(Entity.json(""))) { // Sending empty body since data is in params

      System.out.println("[REGISTER] Status: " + response.getStatus());

      if (response.getStatus() == 200) {
        System.out.println("[REGISTER] Success!");
      } else {
        // Safely read the error message
        String errorMsg = response.readEntity(String.class);
        System.out.println("[REGISTER] Failed: " + errorMsg);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void login(String username, String password) throws IOException, InterruptedException {
    WebTarget loginTarget = baseTarget.path("login").queryParam("username", username)
        .queryParam("password", password);

    try (Response response = loginTarget.request(MediaType.APPLICATION_JSON)
        .post(Entity.json(""))) { // Send empty body if params are in URL

      if (response.getStatus() == 200) {
        TokenResponse wrapper = response.readEntity(TokenResponse.class);
        context.setJwtToken(wrapper.token);
        context.setCurrentUser(username);
        System.out.println("[LOGIN] Success!");
      } else {
        System.out.println("[LOGIN] Failed: " + response.getStatus());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static class TokenResponse {
    String token;

    public TokenResponse() {
    }
  }
}

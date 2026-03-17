package org.example.context;

import java.net.URI;

public class ClientContext {

  private static final URI SERVER_URI = URI.create("https://192.168.0.30:8443/"); // client-server gateway's ip
  private static final URI GROUP_SERVER_URI = URI.create("https://192.168.0.30:8081/");
  private String jwtToken;
  private String currentUser;

  public ClientContext() {
    this.jwtToken = null;
  }

  public URI getServerUri() {
    return SERVER_URI;
  }

  public URI getGroupServerUri() {
    return GROUP_SERVER_URI;
  }

  public String getJwtToken() {
    return jwtToken;
  }

  public void setJwtToken(String jwtToken) {
    this.jwtToken = jwtToken;
  }

  public String getCurrentUser() {
    return currentUser;
  }

  public void setCurrentUser(String currentUser) {
    this.currentUser = currentUser;
  }
}

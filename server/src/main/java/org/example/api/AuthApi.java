package org.example.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Interface that defines the endpoints related to authenticate the user
 */
@Path("/auth")
public interface AuthApi {

  public static final String PASSWORD = "password";
  public static final String USERNAME = "username";

  @POST
  @Path("/register")
  public Response register(@QueryParam(USERNAME) String username,
                           @QueryParam(PASSWORD) String password);

  @POST
  @Path("/login")
  @Produces(MediaType.APPLICATION_JSON)
  public Response login(@QueryParam(USERNAME) String username,
                        @QueryParam(PASSWORD) String password);
}

package org.example.resource;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import io.jsonwebtoken.Jwts;

import java.io.IOException;

/**
 * AuthFilter class is used to automatically intercept requests to every endpoint. The cases of
 * authorization requests - login or register - are ignored.
 * For every other endpoint related to the server operations, this class checks if the
 * authorization token is valid before the resource executes
 */
@Provider
public class AuthFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String path = requestContext.getUriInfo().getPath();

    if (path.startsWith("auth/") || path.equals("auth")) {
      return;
    }

    String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      requestContext.abortWith(Response.status(401).entity("Missing Token").build());
      return;
    }

    String token = authHeader.substring("Bearer ".length());
    try {
      String username = Jwts.parser()
          .verifyWith(KeyProvider.get())
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();

      // store username in context so endpoints can use it, might be needed to verify signatures
      // and if user is authorized to edit document (buyer or seller)
      requestContext.setProperty("user", username);

    } catch (Exception e) {
      requestContext.abortWith(Response.status(401).entity("Invalid Token").build());
    }
  }
}
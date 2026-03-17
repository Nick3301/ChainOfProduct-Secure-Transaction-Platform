package org.example.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Interface that defines which endpoints the Group Server server supports
 */
@Path("/group-server")
public interface GroupServerAPI {

  public static final String USERNAME = "username";
  public static final String GROUPNAME = "groupName";

  /**
   * Method to store a group with access to the transaction
   *
   * @param groupName the name of the group being created
   * @param usernames the list of usernames that will have access to the transaction
   * @return Response with the status code of the operation and the respective message
   */
  @POST
  @Path("/groups/{" + GROUPNAME + "}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response createGroup(@PathParam(GROUPNAME) String groupName,
                              List<String> usernames);

  /**
   * Check if a given username is a member of the group.
   *
   * @param groupName the name of the group to check the members
   * @param username  the username to check membership status
   * @return Response with the status code of the operation and the respective message
   */
  @GET
  @Path("/groups/{" + GROUPNAME + "}/members/{" + USERNAME + "}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response isMember(@PathParam(GROUPNAME) String groupName,
                           @PathParam(USERNAME) String username);

  /**
   * Add a member to an existing group.
   *
   * @param groupName the name of the group
   * @param username  the username to add to the group
   * @return Response with the status code of the operation and the respective message
   */
  @POST
  @Path("/groups/{" + GROUPNAME + "}/members/{" + USERNAME + "}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response addMember(@PathParam(GROUPNAME) String groupName,
                            @PathParam(USERNAME) String username);

  /**
   * Remove a member from an existing group.
   *
   * @param groupName the name of the group
   * @param username  the username to remove from the group
   * @return Response with the status code of the operation and the respective message
   */
  @DELETE
  @Path("/groups/{" + GROUPNAME + "}/members/{" + USERNAME + "}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response removeMember(@PathParam(GROUPNAME) String groupName,
                               @PathParam(USERNAME) String username);

  /**
   * Method to retrieve the public key of the group server
   *
   * @return Response with status of success and the public key, or
   * status and message of error
   */
  @GET
  @Path("/public-key")
  @Produces(MediaType.TEXT_PLAIN)
  public Response getGroupServerPublicKey();

  /**
   * Decrypt using the group's private key, if and only if the caller is a member of that group.
   * The encrypted payload contains both the groupName and transactionKey as a JSON object.
   * 
   * @param username the username of the caller (query parameter)
   * @param encryptedPayload the encrypted JSON payload containing groupName and transactionKey
   * @return Response with the status code of the operation and the transactionKey
   */
  @POST
  @Path("/decrypt")
  @Consumes(MediaType.TEXT_PLAIN)
  @Produces(MediaType.TEXT_PLAIN)
  public Response decrypt(@QueryParam(USERNAME) String username,
                          String encryptedPayload);

  /**
   * Verify if a given member is part of at least one of the groups which are received as a list
   *
   * @param userName   the username of the member to verify
   * @param groupNames the list of groups in which to verify the presence of the members
   * @return Response with OK status in case the member is in one of the groups, error 404 otherwise
   */
  @POST
  @Path("/groups/check-user")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response isInGroups(@QueryParam(USERNAME) String userName, List<String> groupNames);
}

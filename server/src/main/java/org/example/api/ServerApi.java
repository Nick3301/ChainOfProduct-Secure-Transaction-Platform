package org.example.api;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.objects.*;

/**
 * Interface that defines which endpoints the server supports related to transaction operations
 */
@Path("/chain-of-product")
public interface ServerApi {

  public static final String TRANSACTIONID = "transactionId";

  /**
   * Method to store transactions in the database
   *
   * @param context  current context of the server
   * @param transaction the transaction to be stored
   * @return Response with the status code of the operation and the respective message
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response storeTransaction(@Context ContainerRequestContext context,
                                EncryptedTransaction transaction);

  /**
   * Method to obtain transactions from the database
   *
   * @param context    current context of the server
   * @param transactionId the ID of the transaction to be obtained
   * @return Response with the transaction in case of success or an error with the status code and
   * message
   */
  @GET
  @Path("/{" + TRANSACTIONID + "}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response fetchTransaction(@Context ContainerRequestContext context,
                                @PathParam(TRANSACTIONID) String transactionId);

  /**
   * Method to add missing signatures to transactions, in case the buyer or seller haven't
   * already signed
   *
   * @param context    current context of the server
   * @param transactionId the ID of the transaction to be signed
   * @param signature  the signature of the party trying to sign
   * @return Response with the status code of the operation and the respective message
   */
  @POST
  @Path("/sign/" + "{" + TRANSACTIONID + "}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response signTransaction(@Context ContainerRequestContext context,
                               @PathParam(TRANSACTIONID) String transactionId, String signature);

  /**
   * Method to add permission of access to access a transaction to a third party
   *
   * @param context    current context of the server
   * @param transactionId the ID of the transaction to which there will be added the permisison
   * @param shareLog   the ShareLog object to add to the EncryptedTransaction object
   * @return Response with the status code of the operation and the respective message
   */
  @POST
  @Path("/share/" + "{" + TRANSACTIONID + "}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response shareWithThirdParty(@Context ContainerRequestContext context,
                                      @PathParam(TRANSACTIONID) String transactionId, ShareRequest shareRequest);

  /**
   * Method to add permission of access to access a transaction to a group
   *
   * @param context    current context of the server
   * @param transactionId the ID of the transaction to which there will be added the permisison
   * @param shareLog   the ShareLog object to add to the EncryptedTransaction object
   * @return Response with the status code of the operation and the respective message
   */
  @POST
  @Path("/group-share/" + "{" + TRANSACTIONID + "}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public Response shareWithGroup(@Context ContainerRequestContext context,
                                      @PathParam(TRANSACTIONID) String transactionId, ShareRequest shareRequest);

}

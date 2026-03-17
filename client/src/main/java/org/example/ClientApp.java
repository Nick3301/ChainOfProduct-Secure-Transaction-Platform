package org.example;

import org.example.clients.AuthorizationClient;
import org.example.clients.GroupServerClient;
import org.example.clients.TransactionsClient;
import org.example.context.ClientContext;
import org.example.objects.GroupMembershipStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Simple CLI wrapper around ApiClient.
 * Handles user interaction and delegates HTTP work to ApiClient.
 */
public class ClientApp {

  public static void main(String[] args) throws Exception {
    System.setProperty("javax.net.ssl.trustStore", "../certs/clienttruststore.jks");
    System.setProperty("javax.net.ssl.trustStorePassword", "changeme");
    System.setProperty("javax.net.ssl.trustStoreType", "JKS");

    ClientContext context = new ClientContext();
    AuthorizationClient authorizationClient = new AuthorizationClient(context);
    TransactionsClient transactionsClient = new TransactionsClient(context);
    GroupServerClient groupClient = new GroupServerClient(context);

    try (Scanner sc = new Scanner(System.in)) {
      while (true) {
        System.out.println("""

                           Commands: 
                           register, login, store, fetch, sign, share, share-group,
                           create-group, group-add-member, group-remove-member, group-server-pubkey, group-ismember, exit""");
        System.out.print("> ");
        String cmd = sc.nextLine().trim();

        try {
          switch (cmd) {
            case "register" -> {
              System.out.print("Username: ");
              String regUser = sc.nextLine().trim();
              System.out.print("Password: ");
              String regPass = sc.nextLine().trim();
              authorizationClient.register(regUser, regPass);
            }
            case "login" -> {
              System.out.print("Username: ");
              String loginUser = sc.nextLine().trim();
              System.out.print("Password: ");
              String loginPass = sc.nextLine().trim();
              authorizationClient.login(loginUser, loginPass);
            }
            case "store" -> {
              System.out.print("Path to JSON transaction file: ");
              String filepath = sc.nextLine().trim();
              String myCompanyId = context.getCurrentUser();
              if (myCompanyId == null) {
                System.out.println("[STORE] You must be logged in.");
                break;
              }
              transactionsClient.storeTransaction(filepath, myCompanyId);
            }
            case "fetch" -> {
              System.out.print("Transaction ID: ");
              String txId = sc.nextLine().trim();
              String myCompanyId = context.getCurrentUser();
              if (myCompanyId == null) {
                System.out.println("[FETCH] You must be logged in.");
                break;
              }
              transactionsClient.fetchAndDecrypt(txId, myCompanyId);
            }
            case "sign" -> {
              System.out.print("Transaction ID: ");
              String txId = sc.nextLine().trim();
              String myCompanyId = context.getCurrentUser();
              if (myCompanyId == null) {
                System.out.println("[SIGN] You must be logged in.");
                break;
              }
              transactionsClient.signTransaction(txId, myCompanyId);
            }
            case "share" -> {
              System.out.print("Transaction ID: ");
              String txId = sc.nextLine().trim();
              String sharedBy = context.getCurrentUser();
              if (sharedBy == null) {
                System.out.println("[SHARE] You must be logged in.");
                break;
              }
              System.out.print("Share with (target company ID): ");
              String sharedWith = sc.nextLine().trim();
              transactionsClient.shareWithThirdParty(txId, sharedBy, sharedWith);
            }
            case "share-group" -> {
              System.out.print("Transaction ID: ");
              String txId = sc.nextLine().trim();
              String sharedBy = context.getCurrentUser();
              if (sharedBy == null) {
                System.out.println("[SHARE-GROUP] You must be logged in.");
                break;
              }
              System.out.print("Share with group (group name): ");
              String groupName = sc.nextLine().trim();
              transactionsClient.shareWithGroup(txId, sharedBy, groupName);
            }
            case "create-group" -> {
              System.out.print("Group name: ");
              String groupName = sc.nextLine().trim();
              System.out.print("Members (comma-separated usernames): ");
              String line = sc.nextLine().trim();
              List<String> members = Arrays.stream(line.split(","))
                                           .map(String::trim)
                                           .filter(s -> !s.isEmpty())
                                           .toList();
              groupClient.createGroup(groupName, members);
            }
            case "group-add-member" -> {
              System.out.print("Group name: ");
              String groupName = sc.nextLine().trim();
              System.out.print("Username to add: ");
              String userToAdd = sc.nextLine().trim();
              groupClient.addMember(groupName, userToAdd);
            }
            case "group-remove-member" -> {
              System.out.print("Group name: ");
              String groupName = sc.nextLine().trim();
              System.out.print("Username to remove: ");
              String userToRemove = sc.nextLine().trim();
              groupClient.removeMember(groupName, userToRemove);
            }
            case "group-server-pubkey" -> {
              String pubKey = groupClient.getGroupServerPublicKey();
              if (pubKey != null) {
                System.out.println("Group Server public key (Base64 DER):");
                System.out.println(pubKey);
              }
            }
            case "group-ismember" -> {
              System.out.print("Group name: ");
              String groupName = sc.nextLine().trim();
              System.out.print("Username: ");
              String user = sc.nextLine().trim();
              GroupMembershipStatus s = groupClient.isMember(groupName, user);
              if (s != null) {
                System.out.println("Member status for '" + user + "' in group '" +
                    groupName + "': " + s.isMember());
              }
            }
            case "exit" -> {
              System.out.println("Bye.");
              return;
            }
            default -> System.out.println("Unknown command.");
          }
        } catch (Exception e) {
          System.err.println("Error: " + e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }
}

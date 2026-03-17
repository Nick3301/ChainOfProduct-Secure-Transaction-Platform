package org.example;

import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.gson.JsonGsonFeature;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.example.resources.GroupServerResource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * Class responsible to start the CA server
 */
public class GroupServer {

  private static final String BASE_URI = "https://192.168.4.20:8081/";
  private static final String KEYS_DIRECTORY = "../keys/";

  public static void main(String[] args) {

    try {
      System.out.println("[GroupServer] Loading keystore...");
      char[] password = "changeme".toCharArray();
      KeyStore ks = KeyStore.getInstance("PKCS12");
      // Make sure this file exists in your certs folder relative to execution
      ks.load(new FileInputStream("../certs/group_server.p12"), password);

      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks, password);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), null, null);

      // Generate RSA key pair for the group server
      generateGroupServerKeyPair();
      final ResourceConfig config = new ResourceConfig();
      config.register(GroupServerResource.class);
      config.register(JsonGsonFeature.class);

      final HttpServer server = JdkHttpServerFactory.createHttpServer(
          URI.create(BASE_URI),
          config,
          sslContext // SSL Enabled!
      );

      System.out.println("[GroupServer] Server started with endpoints available at " + BASE_URI);

      // Shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("[GroupServer] Stopping server...");
        server.stop(0);
        System.out.println("[GroupServer] Server stopped.");
      }));

      // Keep server running
      Thread.currentThread().join();

    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  private static void generateGroupServerKeyPair() throws Exception {
    Path keysDir = Paths.get(KEYS_DIRECTORY);
    Files.createDirectories(keysDir);

    Path publicKeyPath = Paths.get(KEYS_DIRECTORY, "group_server_public.der");
    Path privateKeyPath = Paths.get(KEYS_DIRECTORY, "group_server_private.der");

    // Check if keys already exist
    if (Files.exists(publicKeyPath) && Files.exists(privateKeyPath)) {
      System.out.println("[GroupServer] Key pair already exists. Skipping generation.");
      return;
    }

    System.out.println("[GroupServer] Generating RSA key pair...");
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
    keyGen.initialize(2048);
    KeyPair keyPair = keyGen.generateKeyPair();

    byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
    byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();

    Files.write(publicKeyPath, publicKeyBytes);
    Files.write(privateKeyPath, privateKeyBytes);

    System.out.println("[GroupServer] Key pair generated and saved to " + KEYS_DIRECTORY);
  }
}
package org.example;

import java.net.URI;

import com.sun.net.httpserver.HttpServer;
import org.example.resource.AuthFilter;
import org.example.resource.AuthResource;
import org.example.resource.ServerResource;
import org.glassfish.jersey.gson.JsonGsonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Class responsible to start the server
 */
public class Server {

  private static final String BASE_URI = "https://192.168.3.20:8443/";

  public static void main(String[] args) {
    try {
      System.out.println("[TransactionServer] Loading keystore...");

      char[] password = "changeme".toCharArray();
      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(new FileInputStream("../certs/server.p12"), password);

      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks, password);

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(kmf.getKeyManagers(), null, null);

      final ResourceConfig config = new ResourceConfig();
      config.register(ServerResource.class);
      config.register(AuthResource.class);
      config.register(AuthFilter.class);

      config.register(JsonGsonFeature.class); // JSON support to receiving objects

      final HttpServer server = JdkHttpServerFactory.createHttpServer(
          URI.create(BASE_URI),
          config,
          sslContext // Pass SSLContext
      );

      System.setProperty("javax.net.ssl.keyStore", "../certs/dbclient.p12");
      System.setProperty("javax.net.ssl.keyStorePassword", "changeme");
      System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");

      System.setProperty("javax.net.ssl.trustStore", "../certs/transaction_server_truststore.jks");
      System.setProperty("javax.net.ssl.trustStorePassword", "changeme");
      System.setProperty("javax.net.ssl.trustStoreType", "JKS");

      System.out.println("[TransactionServer] Server started with endpoints available at " + BASE_URI);

      // Shutdown hook
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("[TransactionServer] Stopping server...");
        server.stop(0);
        System.out.println("[TransactionServer] Server stopped.");
      }));

      // Keep server running
      Thread.currentThread().join();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
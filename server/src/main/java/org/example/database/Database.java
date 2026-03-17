package org.example.database;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * This class establishes the connection to the database and allows other classes to retrieve the
 * existing connection instead of needing to create a new one on each transaction
 */
public class Database {

  private static String URL;
  private static String USER;
  private static String PASS;

  static {
    try {
      // Load driver
      Class.forName("org.postgresql.Driver");
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }

    try {
      Dotenv dotenv = Dotenv.load();
      URL = dotenv.get("DB_URL");
      USER = dotenv.get("DB_USER");
      PASS = dotenv.get("DB_PASSWORD");
    } catch (Exception e) {
      System.out.println("[Database] .env file not found or invalid.");
    }
  }

  public static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(URL, USER, PASS);
  }
}
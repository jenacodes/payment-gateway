package com.payment.gateway.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    public static Connection connect (String host, int dbPort, String dbUsername, String dbPassword, String dbName) throws SQLException {

        String url = "jdbc:postgresql://" + host + ":" + dbPort + "/" + dbName;

        return DriverManager.getConnection(url, dbUsername, dbPassword);

    }
}

package com.payment.gateway.db;

import com.payment.gateway.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnection {

    private static HikariDataSource dataSource;

    public static void initPool(AppConfig config){

        // Initialize HikariCP connection pool
        HikariConfig hikariConfig = new HikariConfig();
        // Set the JDBC URL, username, and password for the database connection
        String url = "jdbc:postgresql://" + config.host() + ":" + config.dbPort() + "/" + config.dbName();


        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(config.dbUsername());
        hikariConfig.setPassword(config.dbPassword());

        // Set additional HikariCP configuration options as needed
        dataSource = new HikariDataSource(hikariConfig);


    }

    public static Connection connect () throws SQLException{
        return dataSource.getConnection();
    }


//    public static Connection connect (String host, int dbPort, String dbUsername, String dbPassword, String dbName) throws SQLException {
//
//        String url = "jdbc:postgresql://" + host + ":" + dbPort + "/" + dbName;
//
//        return DriverManager.getConnection(url, dbUsername, dbPassword);
//
//    }
}

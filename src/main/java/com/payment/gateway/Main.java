package com.payment.gateway;
import com.payment.gateway.config.AppConfig;
import com.payment.gateway.db.DatabaseConnection;
import com.payment.gateway.server.Server;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {
    public static void main(String[] args) throws SQLException, IOException {

        Dotenv dotenv = Dotenv.load();
        String dbUsername = dotenv.get("DB_USERNAME");
        String dbPassword = dotenv.get("DB_PASSWORD");
        String dbName = dotenv.get("DB_NAME");
        String host = dotenv.get("HOST");
        String dbPort = dotenv.get("PORT");
        String serverPortNo = dotenv.get("SERVER_PORT");

        int severPort = parseEnvInt(serverPortNo, 8080);
        int dbPortNo = parseEnvInt(dbPort, 5432);

        AppConfig config = new AppConfig(host, dbPortNo, dbUsername, dbPassword, dbName, severPort);

        try (Connection databaseConnection = DatabaseConnection.connect(config.host(), config.dbPort(), config.dbUsername(), config.dbPassword(), config.dbName())) {

            if (databaseConnection != null && databaseConnection.isClosed()) {
                System.out.println("Connection is closed.");
            } else if (databaseConnection == null) {
                System.out.println("Connection is null.");
            } else {
                System.out.println("Connection is active.");
            }
        }
        Server.start(severPort, config);
    }


    private static int parseEnvInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println("Invalid integer format for value: " + value + ". Using default: " + defaultValue);
            return defaultValue;
        }
    }
}

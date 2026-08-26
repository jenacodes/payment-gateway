package com.payment.gateway.handlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.config.AppConfig;
import com.payment.gateway.db.DatabaseConnection;
import com.payment.gateway.models.PaymentRecord;
import com.payment.gateway.models.PaymentRequest;
import com.payment.gateway.models.PaymentState;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;

//This class handles the /authorize endpoint, it accepts a POST request with a JSON body containing orderId, customerId, amount and currency.
// It generates a payment reference and inserts a new record into the payments table with state PENDING.
// After successful insertion, it updates the state to AUTHORIZED and returns the payment record as JSON in the response.

public class AuthorizeHandler implements HttpHandler {

    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthorizeHandler(AppConfig config) {
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        //Check Method if not POST
        String requestMethod = exchange.getRequestMethod();
        if (!Objects.equals(requestMethod, "POST")) {
            String response = "Method Not Allowed";
            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(405, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            return;
        }


        // Read request body and convert to PaymentRequest object
        InputStream inputStream = exchange.getRequestBody();
        String requestBody = new String(inputStream.readAllBytes());

        //JSON to PaymentRequest object
        PaymentRequest paymentRequest = objectMapper.readValue(requestBody, PaymentRequest.class);

        UUID paymentReference = UUID.randomUUID();
        PaymentState state = PaymentState.PENDING;

        // Insert payment record into database
        String query = "INSERT INTO payments (order_id, customer_id, amount, currency, payment_reference, state)\n" +
                "VALUES (?, ?, ?, ?, ?, ?)";

        //open connection insert the parameterized query, retrieve the id generated via RETURN_GENERATED KEYS
        int key = 0;
        try (
                // Open database connection
                Connection dbConnection = DatabaseConnection.connect(config.host(), config.dbPort(), config.dbUsername(), config.dbPassword(), config.dbName());
                // Insert query to the db
                PreparedStatement st = dbConnection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            // Set parameters for the prepared statement
            st.setInt(1, paymentRequest.orderId());
            st.setInt(2, paymentRequest.customerId());
            st.setBigDecimal(3, paymentRequest.amount());
            st.setString(4, paymentRequest.currency());
            st.setObject(5, paymentReference);
            st.setString(6, state.toString().toLowerCase());

            st.executeUpdate();
            try (ResultSet generatedKeys = st.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    key = generatedKeys.getInt(1);
                    // Update the state to AUTHORIZED after successful insertion
                    try (PreparedStatement updateSt = dbConnection.prepareStatement("UPDATE payments SET state = ? WHERE id = ?")) {

                        updateSt.setString(1, PaymentState.AUTHORIZED.toString().toLowerCase());
                        updateSt.setInt(2, key);
                        updateSt.executeUpdate();

                    }
                    System.out.println("Generated Key: " + key);
                }
            }


        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Create PaymentRecord object to return in response
        PaymentState finalState = PaymentState.AUTHORIZED;
        PaymentRecord paymentRecord = new PaymentRecord(key, paymentReference, paymentRequest.orderId(), paymentRequest.customerId(), paymentRequest.amount(), paymentRequest.currency(), finalState);

        // Send response
        String responseBody = objectMapper.writeValueAsString(paymentRecord);
        byte[] responseBytes = responseBody.getBytes();
        exchange.sendResponseHeaders(201, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }

    }
}

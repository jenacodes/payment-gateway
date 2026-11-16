package com.payment.gateway.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.config.AppConfig;
import com.payment.gateway.db.DatabaseConnection;
import com.payment.gateway.models.PaymentRecord;
import com.payment.gateway.models.PaymentRequest;
import com.payment.gateway.models.PaymentState;
import com.payment.gateway.services.Idempotency.IdempotencyResults;
import com.payment.gateway.services.Idempotency.IdempotencyService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;

//This class handles the /authorize endpoint. It checks for the Idempotency-Key header, parses the request body, and inserts a new payment record into the database.
// It also handles idempotency by checking if the request has already been processed with the same key and returns appropriate responses based on the idempotency result.
public class AuthorizeHandler implements HttpHandler {

    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthorizeHandler(AppConfig config) {
        this.config = config;
    }

    //This method handles the idempotency results and sends appropriate HTTP responses based on the result type.
    // It returns true if a response was sent, indicating that further processing should be stopped, or false if processing should continue.
    private static boolean handleIdempotencyResult(IdempotencyResults results, HttpExchange exchange) {
        try {
            switch (results) {
                case IdempotencyResults.KeyMismatch ignored ->  {
                    String response = "IDEMPOTENCY_KEY_CONFLICT: The request payload does not match the original request for this idempotency key.";
                    byte[] responseBytes = response.getBytes();
                    exchange.sendResponseHeaders(409, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    return true;
                }
                case IdempotencyResults.Pending ignored -> {
                    String response = "IDEMPOTENCY_KEY_PENDING: The request is still being processed. Please retry later.";
                    byte[] responseBytes = response.getBytes();
                    exchange.sendResponseHeaders(409, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    return true;
                }
                case IdempotencyResults.Completed completed -> {
                    String response = completed.responseBody();
                    byte[] responseBytes = response.getBytes();
                    exchange.sendResponseHeaders(completed.responseStatus(), responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    return true;
                }
                case IdempotencyResults.NewKey ignored -> {
                    // Continue processing
                    return false;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow POST requests for the /authorize endpoint
        if (!Objects.equals(exchange.getRequestMethod(), "POST")) {
            String response = "Method Not Allowed";
            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(405, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            return;
        }

        // Check Idempotency-Key header if it exists, if no then stop the request and return a 400 error
        Headers req = exchange.getRequestHeaders();
        String idempotencyKey = req.getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            String response = "Missing required Idempotency-Key header";
            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(400, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
            return;
        }

        System.out.println(req);

        // Parse request body to PaymentRequest object
        InputStream inputStream = exchange.getRequestBody();
        String requestBody = new String(inputStream.readAllBytes());
        PaymentRequest paymentRequest = objectMapper.readValue(requestBody, PaymentRequest.class);

        UUID paymentReference = UUID.randomUUID();
        PaymentState state = PaymentState.PENDING;


        int key = 0;
        int responseStatusCode = 201;
        String responseBody;

        // Insert payment record into the database
        String insertQuery = "INSERT INTO payments (order_id, customer_id, amount, currency, payment_reference, state) VALUES (?, ?, ?, ?, ?, ?)";

        try (

                Connection dbConnection = DatabaseConnection.connect())
                {

                    System.out.println(dbConnection);

            // Idempotency check before inserting
            IdempotencyResults result = IdempotencyService.checkAndClaim(dbConnection, idempotencyKey, paymentRequest);
            if (handleIdempotencyResult(result, exchange)) {
                return;
            }

            try(
                    PreparedStatement st = dbConnection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS)
                    ){

                    // Insert payment record
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
                            // Update state to AUTHORIZED
                            try (PreparedStatement updateSt = dbConnection.prepareStatement("UPDATE payments SET state = ? WHERE id = ?")) {
                                updateSt.setString(1, PaymentState.AUTHORIZED.toString().toLowerCase());
                                updateSt.setInt(2, key);
                                updateSt.executeUpdate();
                            }
                        }
                    }
            }
                    // Build response
                    PaymentRecord paymentRecord = new PaymentRecord(
                            key,
                            paymentReference,
                            paymentRequest.orderId(),
                            paymentRequest.customerId(),
                            paymentRequest.amount(),
                            paymentRequest.currency(),
                            PaymentState.AUTHORIZED
                    );

            System.out.println(paymentRecord);

             responseBody = objectMapper.writeValueAsString(paymentRecord);

             System.out.println(responseBody);
             // Update idempotency key status to COMPLETED with response details and return the response
             String completedResponseQuery = "UPDATE idempotency_keys SET status = ?, response_status = ?, response_body = ?::jsonb WHERE idempotency_key = ?";

            try (PreparedStatement updateSt = dbConnection.prepareStatement(completedResponseQuery)) {
                updateSt.setString(1, "COMPLETED");
                updateSt.setInt(2, responseStatusCode);
                updateSt.setObject(3, responseBody);
                updateSt.setString(4, idempotencyKey);
                updateSt.executeUpdate();


            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        // Send response to client
        byte[] responseBytes = responseBody.getBytes();
        exchange.sendResponseHeaders(responseStatusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}

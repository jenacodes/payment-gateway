package com.payment.gateway.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.gateway.config.AppConfig;
import com.payment.gateway.db.DatabaseConnection;
import com.payment.gateway.models.PaymentState;
import com.payment.gateway.services.PaymentTransition.PaymentTransitioner;
import com.payment.gateway.services.PaymentTransition.TransitionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

//This handles the /capture, /void and /refund endpoints. It splits the paths and retrieves the payment reference and action.
// It then calls the PaymentTransitioner to perform the action and returns the result as JSON in the response.
// it constructs/sends responses with appropriate HTTP status codes based on the outcome of the action, including success, not found, and wrong state scenarios.

public class PaymentActionHandler implements HttpHandler {

    private final AppConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentActionHandler(AppConfig config) {
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        //Check the HTTP Method is POST
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

        //Get the path, split it, pull out the reference string and the action string
        String path = exchange.getRequestURI().getPath();

        String[] arr = path.split("/");

        String referenceString = arr[2];
        String actionString = arr[3];


        UUID uuid = UUID.fromString(referenceString);

        PaymentState currentState;
        PaymentState targetState;

        //Check the action string and set the current state and target state accordingly, if the action is unknown return a 400 error
        switch (actionString) {
            case "capture":
                currentState = PaymentState.AUTHORIZED;
                targetState = PaymentState.CAPTURED;

                break;
            case "void":
                currentState = PaymentState.AUTHORIZED;
                targetState = PaymentState.VOIDED;
                break;

            case "refund":
                currentState = PaymentState.CAPTURED;
                targetState = PaymentState.REFUNDED;
                break;

            default:
                String response = "Unknown action: " + actionString;
                byte[] responseBytes = response.getBytes();
                exchange.sendResponseHeaders(400, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                return;
        }
//Open database connection and retrieve the payment record, check the current state and update to the target state if valid
        try (Connection dbConnection = DatabaseConnection.connect()) {

            TransitionResult result = PaymentTransitioner.attemptTransition(dbConnection, uuid, currentState, targetState);

            //Return the result as JSON in the response, with appropriate HTTP status codes based on the outcome
            switch (result.outcome()) {
                case SUCCESS:
                    String successResponse = objectMapper.writeValueAsString(result.paymentRecord());
                    byte[] responseBytes = successResponse.getBytes();
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                    return;
                case NOT_FOUND:
                    String notFoundResponse = result.detail();
                    byte[] notFoundResponseBytes = notFoundResponse.getBytes();
                    exchange.sendResponseHeaders(404, notFoundResponseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(notFoundResponseBytes);
                    }
                    return;

                case WRONG_STATE:
                    String wrongStateResponse = result.detail();
                    byte[] wrongStateResponseBytes = wrongStateResponse.getBytes();
                    exchange.sendResponseHeaders(409, wrongStateResponseBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(wrongStateResponseBytes);
                    }
            }


        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }


}

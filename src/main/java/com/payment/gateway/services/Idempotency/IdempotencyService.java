package com.payment.gateway.services.Idempotency;

import com.payment.gateway.models.PaymentRequest;
import com.payment.gateway.utils.PayloadHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


// This service handles the idempotency logic for payment requests.
// It checks if a request with the same idempotency key has already been processed, and if so, returns the result of that request instead of processing it again.
// It hashes the request payload to ensure that the same request is being made with the same data, and it stores the idempotency key, payload hash, and status in a database table.
public class IdempotencyService {
    public static IdempotencyResults checkAndClaim(Connection dbConnection, String idempotencyKey, PaymentRequest request) throws SQLException {

        // Hash the payload to ensure that the same request is being made with the same data
        String payloadHash = PayloadHasher.hash(request);

        // Insert the idempotency key, payload hash and status into the database, if it doesn't already exist
        String sql = "INSERT INTO idempotency_keys (idempotency_key, payload_hash, status)\n" + "VALUES (?, ?, ?) ON CONFLICT (idempotency_key) DO NOTHING";

        // Execute the insert statement
        try (PreparedStatement statement = dbConnection.prepareStatement(sql)) {
            statement.setString(1, idempotencyKey);
            statement.setString(2, payloadHash);
            statement.setString(3, "PENDING");

            int rowsAffected = statement.executeUpdate();

            if (rowsAffected == 1){
                return new IdempotencyResults.NewKey();
            }
            // If the insert did not affect any rows, it means the idempotency key already exists. We need to check if the payload hash matches the existing record.
            String checkSql = "SELECT payload_hash, status, response_status, response_body FROM idempotency_keys WHERE idempotency_key = ?";

            // Check the existing record for the idempotency key
            try (PreparedStatement selectStatement = dbConnection.prepareStatement(checkSql)){
                selectStatement.setString(1, idempotencyKey);
                ResultSet resultSet = selectStatement.executeQuery();

                // If the record exists, we need to check if the payload hash matches the existing record. If it does, we return the status of the existing record. If it doesn't, we return a key mismatch error.
                if (resultSet.next()){
                    String hash = resultSet.getString("payload_hash");
                    String status = resultSet.getString("status");
                    int responseStatus = resultSet.getInt("response_status");
                    String responseBody = resultSet.getString("response_body");

                    if (!hash.equals(payloadHash)) {
                        return new IdempotencyResults.KeyMismatch();
                    } else if (status.equals("PENDING")) {
                        return new IdempotencyResults.Pending();
                    }else {
                        return new IdempotencyResults.Completed(responseStatus, responseBody);
                    }
                }else {
                    throw new IllegalStateException("Idempotency key was not found after insert conflict, this should never happen");
                }
            }
        }

    }
}




package com.payment.gateway.service;

import com.payment.gateway.models.PaymentRecord;
import com.payment.gateway.models.PaymentState;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

// This class handles the transition of payment states in the database.
// It attempts to update the state of a payment record based on the provided current and target states.
// If the transition is successful, it returns the updated payment record.
// If the transition fails due to a state mismatch or if the payment record is not found, it provides appropriate feedback.
// it uses SQL queries to perform the update and diagnostic checks, ensuring that the state transition is atomic and consistent.

public class PaymentTransitioner {
    public static TransitionResult attemptTransition(Connection dbConnection, UUID paymentReference, PaymentState currentState, PaymentState targetState) throws SQLException {


        // Update the payment state in the database if the current state matches the expected state and immediately return the updated record
        String updateQuery = "UPDATE payments SET state = ? WHERE payment_reference =  ? AND state = ? RETURNING *";

        // Diagnostic query to check the current state of the payment if the update fails
        String diagnosticQuery = "SELECT state FROM payments WHERE payment_reference = ?";

        try (     // Insert query to the db
                  PreparedStatement st = dbConnection.prepareStatement(updateQuery)) {
            st.setString(1, targetState.toString().toLowerCase());
            st.setObject(2, paymentReference);
            st.setString(3, currentState.toString().toLowerCase());

            ResultSet result = st.executeQuery();

            //If the update was successful, return the updated record
            if (result.next()) {
                int id = result.getInt("id");
                UUID payment_reference = (UUID) result.getObject("payment_reference");
                int orderId = result.getInt("order_id");
                int customerId = result.getInt("customer_id");
                BigDecimal amount = result.getBigDecimal("amount");
                String currency = result.getString("currency");
                PaymentState state = PaymentState.valueOf(result.getString("state").toUpperCase());

                PaymentRecord paymentRecord = new PaymentRecord(id, payment_reference, orderId, customerId, amount, currency, state);

                return new TransitionResult(TransitionOutcome.SUCCESS, null, paymentRecord);
            }
        }

        // If the update failed, check the current state of the payment in the database
        try (PreparedStatement statement = dbConnection.prepareStatement(diagnosticQuery)) {

            statement.setObject(1, paymentReference);

            ResultSet result = statement.executeQuery();
            //if the payment is found, return the actual state, otherwise return NOT_FOUND
            if (result.next()) {
                String actualState = result.getString("state");
                return new TransitionResult(TransitionOutcome.WRONG_STATE, "Payment is currently in state: " + actualState, null);
            } else {
                return new TransitionResult(TransitionOutcome.NOT_FOUND, "No Payment exists with that reference", null);


            }

        }
    }
}

package com.payment.gateway.services.Idempotency;

// This interface represents the possible results of an idempotency check in a payment gateway service.
// It uses sealed interfaces and records to define the different outcomes of the idempotency check because each outcome needs to only carry the fields that are actually relevant to it, rather than one record trying to hold every possible field for every possible case.
// PENDING: The idempotency key is still being processed, and the request should be retried later.
// NEW_KEY: The idempotency key is new, and the request can be processed.
// KEY_MISMATCH: The idempotency key exists, but the payload hash does not match the existing record, indicating a potential conflict or error.
// COMPLETED: The idempotency key exists, and the request has already been processed. It includes the response status and body from the previous


public sealed interface IdempotencyResults {
    record Pending() implements IdempotencyResults {}
    record NewKey() implements IdempotencyResults {}
    record KeyMismatch() implements IdempotencyResults {}
    record Completed(int responseStatus, String responseBody) implements IdempotencyResults {}

}

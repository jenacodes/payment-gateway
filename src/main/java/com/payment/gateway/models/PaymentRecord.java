package com.payment.gateway.models;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRecord (int id, UUID paymentReference, int orderId, int customerId, BigDecimal amount, String currency, PaymentState state) {
}

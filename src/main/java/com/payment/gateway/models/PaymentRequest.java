package com.payment.gateway.models;
import java.math.BigDecimal;

public record PaymentRequest(int orderId, int customerId, BigDecimal amount, String currency) {}

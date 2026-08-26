package com.payment.gateway.service;

import com.payment.gateway.models.PaymentRecord;

public record TransitionResult(TransitionOutcome outcome, String detail, PaymentRecord paymentRecord) {
}

package com.payment.gateway.services.PaymentTransition;

import com.payment.gateway.models.PaymentRecord;

public record TransitionResult(TransitionOutcome outcome, String detail, PaymentRecord paymentRecord) {
}

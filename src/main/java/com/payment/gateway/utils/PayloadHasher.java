package com.payment.gateway.utils;

import com.payment.gateway.models.PaymentRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

//This class is responsible for hashing the payload of a PaymentRequest object. It provides a static method to compute the hash based on the orderId, customerId, amount, and currency of the request.
// The hash is computed using the SHA-256 algorithm and returned as a hexadecimal string.

public class PayloadHasher {

    // Private constructor to prevent instantiation of this utility class
    private PayloadHasher(){
        throw new UnsupportedOperationException("This is a utility class and it cannot be instantiated");
    }

    // This method computes the SHA-256 hash of the canonical string representation of a PaymentRequest object.
    public static String hash(PaymentRequest request){
        int orderId = request.orderId();
        int customerId = request.customerId();
        BigDecimal amount = request.amount().setScale(2, RoundingMode.UNNECESSARY);
        String currency = request.currency();

        String canonicalString = String.join("|", String.valueOf(orderId), String.valueOf(customerId), String.valueOf(amount),currency);

        //Complete the hash computation
        byte [] digestByte;
        try {
            byte [] canonicalStringBytes = canonicalString.getBytes(StandardCharsets.UTF_8);
            // Create MessageDigest instance for SHA-256
            MessageDigest md = MessageDigest.getInstance("SHA-256");

            digestByte = md.digest(canonicalStringBytes);

            return HexFormat.of().formatHex(digestByte);
        }

        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

    }

}

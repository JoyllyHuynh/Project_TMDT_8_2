package com.example.demo.util;

import com.example.demo.dto.order.CheckoutRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public class RequestFingerprintUtil {

    private RequestFingerprintUtil() {
    }

    public static String generate(CheckoutRequest request) {
        StringBuilder data = new StringBuilder();

        appendField(data, normalize(request.getProductId()));
        appendField(data, normalize(request.getQuantity()));
        appendField(data, normalize(request.getRecipientName()));
        appendField(data, normalize(request.getRecipientPhone()));
        appendField(data, normalize(request.getShippingAddress()));
        appendField(data, normalize(request.getBuyerNote()));
        appendField(data, normalize(request.getShippingMethod()));
        appendField(data, normalize(request.getPaymentMethod()));
        appendField(data, normalizeVoucher(request.getPlatformCode()));
        appendField(data, normalizeVoucher(request.getSellerCode()));
        appendField(data, normalizeVoucher(request.getShippingCode()));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    data.toString().getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", e
            );
        }
    }

    private static void appendField(StringBuilder data, String value) {
        if (value == null) {
            data.append("-1:");
        } else {
            data.append(value.length())
                    .append(":")
                    .append(value);
        }
    }

    private static String normalize(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private static String normalizeVoucher(String code) {
        return code == null
                ? null
                : code.trim().toUpperCase(Locale.ROOT);
    }
}
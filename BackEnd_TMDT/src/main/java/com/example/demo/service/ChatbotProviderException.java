package com.example.demo.service;

public class ChatbotProviderException extends RuntimeException {

    private final String safeDiagnostic;

    public ChatbotProviderException(String message) {
        this(message, message, null);
    }

    public ChatbotProviderException(String message, Throwable cause) {
        this(message, message, cause);
    }

    public ChatbotProviderException(String message, String safeDiagnostic) {
        this(message, safeDiagnostic, null);
    }

    public ChatbotProviderException(String message, String safeDiagnostic, Throwable cause) {
        super(message, cause);
        this.safeDiagnostic = safeDiagnostic == null || safeDiagnostic.isBlank()
                ? "No safe provider diagnostic is available."
                : safeDiagnostic;
    }

    public String getSafeDiagnostic() {
        return safeDiagnostic;
    }
}

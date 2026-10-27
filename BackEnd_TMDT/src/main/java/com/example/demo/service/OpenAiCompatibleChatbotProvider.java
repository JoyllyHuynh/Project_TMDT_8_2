package com.example.demo.service;

import com.example.demo.config.ChatbotProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleChatbotProvider implements ChatbotAiProvider {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_ERROR_BODY_LENGTH = 16_384;
    private static final int MAX_DIAGNOSTIC_FIELD_LENGTH = 160;
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)bearer\\s+[^\\s,;]+");
    private static final Pattern SECRET_ASSIGNMENT_PATTERN =
            Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)\\b(?:sk|rk|pk)-[a-z0-9_-]{8,}\\b");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern SAFE_CODE_PATTERN = Pattern.compile("[^A-Za-z0-9_.-]");

    private final ChatbotProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @Override
    public ChatCompletion complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        if (!isConfigured()) {
            throw new ChatbotProviderException(
                    "Chatbot provider is not configured.",
                    "configuration: missing CHATBOT_API_KEY"
            );
        }

        int timeoutSeconds = Math.max(1, Math.min(30, properties.getTimeoutSeconds()));
        URI endpoint = resolveEndpoint();
        String serializedRequest = serializeRequest(messages, tools);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(serializedRequest))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw providerHttpException(response.statusCode(), response.body());
            }
            return parseCompletion(response.body());
        } catch (HttpTimeoutException exception) {
            throw new ChatbotProviderException(
                    "Chatbot provider request timed out.",
                    "timeout: provider did not respond within the configured timeout",
                    exception
            );
        } catch (ConnectException | UnknownHostException exception) {
            throw new ChatbotProviderException(
                    "Could not connect to chatbot provider.",
                    "connection: provider host could not be reached",
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatbotProviderException(
                    "Chatbot provider request was interrupted.",
                    "interrupted: provider request was interrupted",
                    exception
            );
        } catch (IOException exception) {
            throw new ChatbotProviderException(
                    "Chatbot provider request failed.",
                    "connection: I/O failure while communicating with provider",
                    exception
            );
        }
    }

    private URI resolveEndpoint() {
        String baseUrl = properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                ? "https://api.openai.com/v1"
                : properties.getBaseUrl().trim();
        try {
            URI base = URI.create(baseUrl);
            if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null
                    || base.getUserInfo() != null
                    || base.getQuery() != null
                    || base.getFragment() != null) {
                throw new IllegalArgumentException("Invalid provider base URL.");
            }
            String normalizedBase = base.toString().replaceAll("/+$", "");
            return URI.create(normalizedBase + "/chat/completions");
        } catch (IllegalArgumentException exception) {
            throw new ChatbotProviderException(
                    "Chatbot provider configuration is invalid.",
                    "configuration: CHATBOT_API_BASE_URL must be an HTTP(S) base URL without credentials or query parameters",
                    exception
            );
        }
    }

    private String serializeRequest(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        String model = properties.getModel();
        if (model == null || model.isBlank()) {
            throw new ChatbotProviderException(
                    "Chatbot provider model is not configured.",
                    "configuration: CHATBOT_MODEL is empty"
            );
        }
        Map<String, Object> requestBody = Map.of(
                "model", model.trim(),
                "messages", messages,
                "tools", tools,
                "tool_choice", "auto",
                "temperature", 0.2,
                "max_tokens", 700
        );
        try {
            return objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException exception) {
            throw new ChatbotProviderException(
                    "Could not serialize chatbot provider request.",
                    "request: could not serialize the provider payload",
                    exception
            );
        }
    }

    private ChatbotProviderException providerHttpException(int status, String body) {
        ProviderError error = readProviderError(body);
        String diagnostic = "http " + status
                + " (code=" + error.code()
                + ", type=" + error.type()
                + "): " + safeProviderMessage(status, error.message());
        return new ChatbotProviderException(
                "Chatbot provider returned HTTP " + status + ".",
                diagnostic
        );
    }

    private ProviderError readProviderError(String body) {
        if (body == null || body.isBlank() || body.length() > MAX_ERROR_BODY_LENGTH) {
            return ProviderError.unknown();
        }
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            return new ProviderError(
                    safeProviderField(error.path("code").asText("unknown")),
                    safeProviderField(error.path("type").asText("unknown")),
                    sanitizeProviderMessage(error.path("message").asText(""))
            );
        } catch (JsonProcessingException exception) {
            return ProviderError.unknown();
        }
    }

    private String safeProviderField(String value) {
        String safe = SAFE_CODE_PATTERN.matcher(value == null ? "" : value).replaceAll("");
        return truncate(safe.isBlank() ? "unknown" : safe, MAX_DIAGNOSTIC_FIELD_LENGTH);
    }

    private String sanitizeProviderMessage(String value) {
        String safe = value == null ? "" : value
                .replace(properties.getApiKey(), "[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        safe = BEARER_PATTERN.matcher(safe).replaceAll("Bearer [redacted]");
        safe = SECRET_ASSIGNMENT_PATTERN.matcher(safe).replaceAll("$1=[redacted]");
        safe = TOKEN_PATTERN.matcher(safe).replaceAll("[redacted-token]");
        safe = EMAIL_PATTERN.matcher(safe).replaceAll("[redacted-email]");
        return truncate(safe, MAX_DIAGNOSTIC_FIELD_LENGTH);
    }

    private String safeProviderMessage(int status, String providerMessage) {
        if (providerMessage != null && !providerMessage.isBlank()) {
            return providerMessage;
        }
        return switch (status) {
            case 401 -> "authentication failed; verify CHATBOT_API_KEY";
            case 403 -> "provider denied access; verify account permissions";
            case 404 -> "provider endpoint or configured model was not found";
            case 429 -> "provider rate limit or usage quota was reached";
            default -> status >= 500 ? "provider server error" : "provider rejected the request";
        };
    }

    private String truncate(String value, int maximumLength) {
        if (value.length() <= maximumLength) return value;
        return value.substring(0, maximumLength) + "...";
    }

    private ChatCompletion parseCompletion(String responseBody) {
        JsonNode response;
        try {
            response = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new ChatbotProviderException(
                    "Chatbot provider returned invalid JSON.",
                    "response: provider returned invalid JSON",
                    exception
            );
        }
        if (response == null || !response.isObject()) {
            throw new ChatbotProviderException(
                    "Chatbot provider returned invalid JSON.",
                    "response: provider returned an empty or non-object JSON response"
            );
        }
        JsonNode message = response.path("choices").path(0).path("message");
        if (!message.isObject()) {
            throw new ChatbotProviderException(
                    "Chatbot provider returned an invalid response.",
                    "response: expected choices[0].message in provider response"
            );
        }
        Map<String, Object> assistantMessage = objectMapper.convertValue(message, MAP_TYPE);
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray()) {
            for (JsonNode call : calls) {
                String id = call.path("id").asText("");
                String name = call.path("function").path("name").asText("");
                String arguments = call.path("function").path("arguments").asText("{}");
                if (!id.isBlank() && !name.isBlank()) {
                    toolCalls.add(new ToolCall(id, name, arguments));
                }
            }
        }
        return new ChatCompletion(
                assistantMessage,
                message.path("content").asText(""),
                List.copyOf(toolCalls)
        );
    }

    private record ProviderError(String code, String type, String message) {
        private static ProviderError unknown() {
            return new ProviderError("unknown", "unknown", "");
        }
    }
}

package com.example.demo.service;

import com.example.demo.config.ChatbotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleChatbotProviderTest {

    @Test
    void callsConfiguredProviderAndParsesToolCallsWithoutExternalNetwork() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = server((exchange) -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, """
                    {"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
                      {"id":"call-1","type":"function","function":{"name":"search_faq","arguments":"{\\"query\\":\\"đổi trả\\"}"}}
                    ]}}]}
                    """);
        });
        try {
            OpenAiCompatibleChatbotProvider provider = provider(server, "local-test-key", 2);

            var completion = provider.complete(
                    List.of(Map.of("role", "user", "content", "Chính sách đổi trả?")),
                    List.of(Map.of("type", "function"))
            );

            assertEquals("Bearer local-test-key", authorization.get());
            assertFalse(requestBody.get().contains(authorization.get().substring("Bearer ".length())));
            var requestJson = new ObjectMapper().readTree(requestBody.get());
            assertEquals("gpt-4o-mini", requestJson.path("model").asText());
            assertEquals("auto", requestJson.path("tool_choice").asText());
            assertEquals("function", requestJson.path("tools").path(0).path("type").asText());
            assertEquals("search_faq", completion.toolCalls().get(0).name());
            assertEquals("{\"query\":\"đổi trả\"}", completion.toolCalls().get(0).arguments());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsProviderHttpStatusesAndSanitizesErrorDiagnostics() throws Exception {
        AtomicReference<Integer> status = new AtomicReference<>(401);
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = server(exchange -> respond(exchange, status.get(), body.get()));
        try {
            OpenAiCompatibleChatbotProvider provider = provider(server, "local-test-key", 2);
            for (int httpStatus : List.of(401, 403, 404, 429, 500, 503)) {
                status.set(httpStatus);
                body.set("""
                        {"error":{"code":"code-%d","type":"test_type","message":"Rejected local-test-key; Bearer private-token; user@example.com"}}
                        """.formatted(httpStatus));

                ChatbotProviderException exception = assertThrows(
                        ChatbotProviderException.class,
                        () -> provider.complete(messages(), tools())
                );

                assertTrue(exception.getSafeDiagnostic().contains("http " + httpStatus));
                assertTrue(exception.getSafeDiagnostic().contains("code=code-" + httpStatus));
                assertTrue(exception.getSafeDiagnostic().contains("type=test_type"));
                assertFalse(exception.getSafeDiagnostic().contains("local-test-key"));
                assertFalse(exception.getSafeDiagnostic().contains("private-token"));
                assertFalse(exception.getSafeDiagnostic().contains("user@example.com"));
                assertTrue(exception.getSafeDiagnostic().length() < 600);
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void truncatesLongProviderErrorMessage() throws Exception {
        String longMessage = "x".repeat(2000);
        HttpServer server = server(exchange -> respond(
                exchange,
                500,
                "{\"error\":{\"code\":\"server_error\",\"type\":\"server_error\",\"message\":\""
                        + longMessage + "\"}}"
        ));
        try {
            ChatbotProviderException exception = assertThrows(
                    ChatbotProviderException.class,
                    () -> provider(server, "local-test-key", 2).complete(messages(), tools())
            );
            assertTrue(exception.getSafeDiagnostic().length() < 600);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void distinguishesInvalidProviderJson() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200, "not json"));
        try {
            ChatbotProviderException exception = assertThrows(
                    ChatbotProviderException.class,
                    () -> provider(server, "local-test-key", 2).complete(messages(), tools())
            );
            assertTrue(exception.getSafeDiagnostic().contains("invalid JSON"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void classifiesTimeoutAndConnectionFailure() throws Exception {
        HttpServer slowServer = server(exchange -> {
            try {
                Thread.sleep(1800);
                respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"late\"}}]}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The client has timed out and closed the local test connection.
            }
        });
        try {
            ChatbotProviderException timeout = assertThrows(
                    ChatbotProviderException.class,
                    () -> provider(slowServer, "local-test-key", 1).complete(messages(), tools())
            );
            assertTrue(timeout.getSafeDiagnostic().startsWith("timeout:"));
        } finally {
            slowServer.stop(0);
        }

        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))) {
            closedPort = socket.getLocalPort();
        }
        ChatbotProperties properties = properties("http://127.0.0.1:" + closedPort + "/v1", "local-test-key", 2);
        ChatbotProviderException connection = assertThrows(
                ChatbotProviderException.class,
                () -> new OpenAiCompatibleChatbotProvider(properties, new ObjectMapper()).complete(messages(), tools())
        );
        assertTrue(connection.getSafeDiagnostic().startsWith("connection:"));
    }

    @Test
    void missingApiKeyDoesNotMakeARequestAndInvalidBaseUrlIsConfigurationError() {
        ChatbotProperties missingKey = properties("https://api.openai.com/v1", "", 2);
        OpenAiCompatibleChatbotProvider unconfigured = new OpenAiCompatibleChatbotProvider(missingKey, new ObjectMapper());
        assertFalse(unconfigured.isConfigured());
        assertTrue(assertThrows(
                ChatbotProviderException.class,
                () -> unconfigured.complete(messages(), tools())
        ).getSafeDiagnostic().contains("missing CHATBOT_API_KEY"));

        ChatbotProperties invalidUrl = properties("file:///tmp/provider", "local-test-key", 2);
        assertTrue(assertThrows(
                ChatbotProviderException.class,
                () -> new OpenAiCompatibleChatbotProvider(invalidUrl, new ObjectMapper()).complete(messages(), tools())
        ).getSafeDiagnostic().startsWith("configuration:"));
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", handler);
        server.start();
        return server;
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private OpenAiCompatibleChatbotProvider provider(HttpServer server, String apiKey, int timeoutSeconds) {
        return new OpenAiCompatibleChatbotProvider(
                properties("http://127.0.0.1:" + server.getAddress().getPort() + "/v1", apiKey, timeoutSeconds),
                new ObjectMapper()
        );
    }

    private ChatbotProperties properties(String baseUrl, String apiKey, int timeoutSeconds) {
        ChatbotProperties properties = new ChatbotProperties();
        properties.setApiKey(apiKey);
        properties.setBaseUrl(baseUrl);
        properties.setModel("gpt-4o-mini");
        properties.setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private List<Map<String, Object>> messages() {
        return List.of(Map.of("role", "user", "content", "hello"));
    }

    private List<Map<String, Object>> tools() {
        return List.of(Map.of("type", "function", "function", Map.of(
                "name", "search_faq",
                "parameters", Map.of("type", "object", "properties", Map.of())
        )));
    }
}

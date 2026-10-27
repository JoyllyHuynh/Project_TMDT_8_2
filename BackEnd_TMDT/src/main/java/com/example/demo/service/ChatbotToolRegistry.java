package com.example.demo.service;

import com.example.demo.dto.chatbot.ChatbotLink;
import com.example.demo.dto.chatbot.ChatbotOrderSummary;
import com.example.demo.dto.chatbot.ChatbotProductCard;
import com.example.demo.dto.chatbot.ChatbotToolResult;
import com.example.demo.dto.catalog.CategoryResponse;
import com.example.demo.dto.product.PageResponse;
import com.example.demo.dto.product.ProductResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.FaqKnowledgeService.FaqEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatbotToolRegistry {

    private static final int PRODUCT_RESULT_LIMIT = 5;

    private final ProductService productService;
    private final CatalogService catalogService;
    private final OrderService orderService;
    private final FaqKnowledgeService faqKnowledgeService;
    private final ObjectMapper objectMapper;

    public ChatbotToolResult execute(String name, String rawArguments) {
        JsonNode arguments = parseArguments(rawArguments);
        return switch (name) {
            case "search_products" -> searchProducts(arguments);
            case "get_product_details" -> getProductDetails(arguments);
            case "get_categories" -> getCategories();
            case "lookup_my_orders" -> lookupMyOrders();
            case "search_faq" -> searchFaq(arguments);
            default -> ChatbotToolResult.text("Công cụ này không khả dụng.");
        };
    }

    public List<Map<String, Object>> definitions() {
        Map<String, Object> string = Map.of("type", "string");
        Map<String, Object> integer = Map.of("type", "integer");
        Map<String, Object> number = Map.of("type", "number");
        return List.of(
                function("search_products", "Tìm tối đa 5 sản phẩm đang được công khai trong catalog theo từ khóa và bộ lọc.",
                        schema(Map.of(
                                "keyword", string,
                                "categoryId", integer,
                                "minPrice", number,
                                "maxPrice", number,
                                "minCondition", integer,
                                "maxCondition", integer,
                                "sortBy", Map.of("type", "string", "enum", List.of("newest", "price")),
                                "direction", Map.of("type", "string", "enum", List.of("asc", "desc"))
                        ), List.of())),
                function("get_product_details", "Lấy chi tiết một sản phẩm đang được công khai bằng ID catalog.",
                        schema(Map.of("id", integer), List.of("id"))),
                function("get_categories", "Liệt kê các danh mục sản phẩm hiện có.",
                        schema(Map.of(), List.of())),
                function("lookup_my_orders", "Tra cứu tối đa 5 đơn gần nhất của người dùng đang xác thực; không nhận user ID.",
                        schema(Map.of(), List.of())),
                function("search_faq", "Tìm câu trả lời trong kho nội dung ComicWorm đã xác minh; nếu không có kết quả thì nói rõ chưa có thông tin.",
                        schema(Map.of("query", string), List.of("query")))
        );
    }

    private ChatbotToolResult searchProducts(JsonNode arguments) {
        if (!optionalText(arguments, "keyword")
                || !optionalInteger(arguments, "categoryId")
                || !optionalNumber(arguments, "minPrice")
                || !optionalNumber(arguments, "maxPrice")
                || !optionalInteger(arguments, "minCondition")
                || !optionalInteger(arguments, "maxCondition")
                || !optionalEnum(arguments, "sortBy", List.of("newest", "price"))
                || !optionalEnum(arguments, "direction", List.of("asc", "desc"))) {
            return ChatbotToolResult.text("Bộ lọc sản phẩm không hợp lệ.");
        }
        String keyword = text(arguments, "keyword", 120);
        Integer categoryId = integer(arguments, "categoryId");
        BigDecimal minPrice = decimal(arguments, "minPrice");
        BigDecimal maxPrice = decimal(arguments, "maxPrice");
        Integer minCondition = integer(arguments, "minCondition");
        Integer maxCondition = integer(arguments, "maxCondition");
        String sortBy = enumValue(arguments, "sortBy", List.of("newest", "price"), "newest");
        String direction = enumValue(arguments, "direction", List.of("asc", "desc"), "desc");
        if (categoryId != null && categoryId < 1
                || minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0
                || minCondition != null && (minCondition < 0 || minCondition > 100)
                || maxCondition != null && (maxCondition < 0 || maxCondition > 100)
                || minCondition != null && maxCondition != null && minCondition > maxCondition) {
            return ChatbotToolResult.text("Bộ lọc sản phẩm không hợp lệ.");
        }

        PageResponse<ProductResponse> page;
        try {
            page = productService.getProducts(
                    keyword, categoryId, minPrice, maxPrice, minCondition, maxCondition,
                    null, null, 0, PRODUCT_RESULT_LIMIT, sortBy, direction
            );
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) {
                return ChatbotToolResult.text("Không tìm thấy danh mục hoặc sản phẩm phù hợp.");
            }
            throw exception;
        }
        List<ChatbotProductCard> cards = page.getContent().stream().map(this::toCard).toList();
        if (cards.isEmpty()) {
            return ChatbotToolResult.text("Không tìm thấy sản phẩm công khai phù hợp trong catalog hiện tại.");
        }
        return new ChatbotToolResult(toJson(cards), cards, List.of(), false);
    }

    private ChatbotToolResult getProductDetails(JsonNode arguments) {
        Long id = longValue(arguments, "id");
        if (id == null || id < 1) {
            return ChatbotToolResult.text("ID sản phẩm không hợp lệ.");
        }
        try {
            ChatbotProductCard card = toCard(productService.getProduct(id));
            return new ChatbotToolResult(toJson(card), List.of(card), List.of(), false);
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND) {
                return ChatbotToolResult.text("Không tìm thấy sản phẩm công khai có ID này.");
            }
            throw exception;
        }
    }

    private ChatbotToolResult getCategories() {
        List<CategoryResponse> categories = catalogService.getCategories().stream().limit(20).toList();
        return ChatbotToolResult.text(toJson(categories));
    }

    private ChatbotToolResult lookupMyOrders() {
        try {
            SecurityUtils.currentUser();
        } catch (ApiException exception) {
            if (exception.getStatus() == HttpStatus.UNAUTHORIZED) {
                return new ChatbotToolResult(
                        "Cần đăng nhập bằng tài khoản đã đặt hàng để tra cứu đơn.",
                        List.of(),
                        List.of(),
                        true
                );
            }
            throw exception;
        }
        List<ChatbotOrderSummary> orders = orderService.getChatbotOrderSummaries();
        return orders.isEmpty()
                ? ChatbotToolResult.text("Tài khoản này chưa có đơn hàng.")
                : ChatbotToolResult.text(toJson(orders));
    }

    private ChatbotToolResult searchFaq(JsonNode arguments) {
        String query = text(arguments, "query", 150);
        if (query == null || query.isBlank()) {
            return ChatbotToolResult.text("Cần có nội dung tìm kiếm FAQ.");
        }
        List<FaqEntry> entries = faqKnowledgeService.search(query);
        return entries.isEmpty()
                ? ChatbotToolResult.text("Không có câu trả lời đã xác minh cho chủ đề này. Hãy nói rõ là chưa có thông tin chính thức.")
                : new ChatbotToolResult(
                        toJson(entries),
                        List.of(),
                        entries.stream()
                                .map(entry -> new ChatbotLink(entry.question(), entry.url()))
                                .toList(),
                        false
                );
    }

    private ChatbotProductCard toCard(ProductResponse product) {
        String imageUrl = product.getImageUrls() == null || product.getImageUrls().isEmpty()
                ? null
                : product.getImageUrls().get(0);
        return new ChatbotProductCard(
                product.getId(),
                product.getTitle(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getConditionPercent(),
                imageUrl,
                "/tin/html/product_details.html?id=" + product.getId()
        );
    }

    private JsonNode parseArguments(String rawArguments) {
        if (rawArguments != null && rawArguments.length() > 4000) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode arguments = objectMapper.readTree(rawArguments == null || rawArguments.isBlank() ? "{}" : rawArguments);
            return arguments != null && arguments.isObject() ? arguments : objectMapper.createObjectNode();
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private String text(JsonNode arguments, String name, int maximumLength) {
        JsonNode value = arguments.get(name);
        if (value == null || !value.isTextual()) return null;
        String text = value.asText().trim();
        return text.length() <= maximumLength ? text : text.substring(0, maximumLength);
    }

    private Integer integer(JsonNode arguments, String name) {
        JsonNode value = arguments.get(name);
        if (value == null || !value.canConvertToInt()) return null;
        return value.intValue();
    }

    private Long longValue(JsonNode arguments, String name) {
        JsonNode value = arguments.get(name);
        if (value == null || !value.canConvertToLong()) return null;
        return value.longValue();
    }

    private BigDecimal decimal(JsonNode arguments, String name) {
        JsonNode value = arguments.get(name);
        if (value == null || !value.isNumber()) return null;
        return value.decimalValue();
    }

    private String enumValue(JsonNode arguments, String name, List<String> allowed, String fallback) {
        String value = text(arguments, name, 20);
        if (value == null) return fallback;
        String normalized = value.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private boolean optionalText(JsonNode arguments, String name) {
        return arguments.get(name) == null || arguments.get(name).isTextual();
    }

    private boolean optionalInteger(JsonNode arguments, String name) {
        JsonNode value = arguments.get(name);
        return value == null || value.isIntegralNumber() && value.canConvertToInt();
    }

    private boolean optionalNumber(JsonNode arguments, String name) {
        JsonNode value = arguments.get(name);
        return value == null || value.isNumber();
    }

    private boolean optionalEnum(JsonNode arguments, String name, List<String> allowed) {
        JsonNode value = arguments.get(name);
        return value == null || value.isTextual()
                && allowed.contains(value.asText().toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> function(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function", Map.of(
                "name", name,
                "description", description,
                "parameters", parameters
        ));
    }

    private Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize chatbot tool result.", exception);
        }
    }
}

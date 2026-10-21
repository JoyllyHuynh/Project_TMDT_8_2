package com.example.demo.service;

import com.example.demo.dto.admin.AdminOrderResponse;
import com.example.demo.dto.admin.AdminProductResponse;
import com.example.demo.dto.admin.AdminRequests;
import com.example.demo.dto.admin.AdminUserResponse;
import com.example.demo.dto.order.UpdateOrderStatusRequest;
import com.example.demo.dto.product.PageResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import com.example.demo.model.OrderStatusHistory;
import com.example.demo.model.PaymentAllocation;
import com.example.demo.model.Product;
import com.example.demo.model.ProductImage;
import com.example.demo.model.Refund;
import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.DisputeFavor;
import com.example.demo.model.enums.ModerationStatus;
import com.example.demo.model.enums.OrderPaymentStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.RefundStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.AuthSessionRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.OrderStatusHistoryRepository;
import com.example.demo.repository.PaymentAllocationRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.RefundRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Các chức năng quản trị cơ bản của Admin Console:
 * quản lý người dùng, sản phẩm và đơn hàng toàn sàn.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PAGE_SIZE_MAX = 100;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final AuthSessionRepository authSessionRepository;
    private final RefundRepository refundRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final PasswordEncoder passwordEncoder;

    /* ======================================================================
       1. QUẢN LÝ NGƯỜI DÙNG
       ====================================================================== */

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> getUsers(
            String keyword,
            UserRole role,
            AccountStatus status,
            Boolean seller,
            Integer page,
            Integer size
    ) {
        Page<User> users = userRepository.search(
                normalize(keyword), role, status, seller, pageRequest(page, size)
        );
        UserStats stats = loadUserStats(users.getContent().stream().map(User::getId).toList());
        return PageResponse.from(users, user -> enrich(user, stats));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(Long id) {
        User user = findUser(id);
        return enrich(user, loadUserStats(List.of(user.getId())));
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getSellerRequests() {
        List<User> users = userRepository
                .findBySellerRequestedAtIsNotNullAndDeletedAtIsNullOrderByCreatedAtDesc();
        UserStats stats = loadUserStats(users.stream().map(User::getId).toList());
        return users.stream()
                .map(user -> enrich(user, stats))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getUserCounts() {
        long total = userRepository.countByDeletedAtIsNull();
        long sellers = userRepository.countBySellerAndDeletedAtIsNull(Boolean.TRUE);
        long locked = userRepository.countByAccountStatusAndDeletedAtIsNull(AccountStatus.LOCKED);
        Map<String, Long> counts = new HashMap<>();
        counts.put("total", total);
        counts.put("buyers", Math.max(0, total - sellers));
        counts.put("sellers", sellers);
        counts.put("locked", locked);
        // Chưa có API báo cáo (thuộc phân hệ kiểm duyệt) nên luôn là 0 cho tới khi tích hợp.
        counts.put("reported", 0L);
        counts.put("sellerRequests", userRepository.countBySellerRequestedAtIsNotNullAndDeletedAtIsNull());
        return counts;
    }

    @Transactional
    public AdminUserResponse createUser(AdminRequests.CreateUser request) {
        String fullName = request.fullName() == null ? "" : request.fullName().trim();
        String email = request.email() == null ? "" : request.email().trim();
        if (fullName.isEmpty() || email.isEmpty()) {
            throw ApiException.badRequest("Vui lòng nhập họ tên và email.");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("Email này đã được đăng ký.");
        }

        String password = request.password() == null || request.password().isBlank()
                ? generateTemporaryPassword()
                : request.password().trim();
        // Validate password strength when admin provides one
        if (request.password() != null && !request.password().isBlank()) {
            if (password.length() < 8) {
                throw ApiException.badRequest("Mật khẩu phải có ít nhất 8 ký tự.");
            }
            // Validate: at least 1 lowercase, 1 uppercase, 1 digit
            boolean hasLower = false, hasUpper = false, hasDigit = false;
            for (char c : password.toCharArray()) {
                if (Character.isLowerCase(c)) hasLower = true;
                else if (Character.isUpperCase(c)) hasUpper = true;
                else if (Character.isDigit(c)) hasDigit = true;
            }
            if (!(hasLower && hasUpper && hasDigit)) {
                throw ApiException.badRequest("Mật khẩu phải chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 số.");
            }
        }

        User user = new User();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        // Mặc định là người dùng thường; chỉ cấp ADMIN khi admin gửi role = ADMIN
        // (endpoint /api/admin/** đã được chặn bằng vai trò ADMIN ở SecurityConfig).
        user.setRole(request.role() == UserRole.ADMIN ? UserRole.ADMIN : UserRole.USER);
        user.setSeller(Boolean.TRUE.equals(request.seller()));
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);

        AdminUserResponse response = AdminUserResponse.from(user);
        response.setTemporaryPassword(password);
        return response;
    }


    @Transactional
    public AdminUserResponse updateUserStatus(Long id, AdminRequests.UpdateUserStatus request) {
        User user = findUser(id);
        if (request.status() == AccountStatus.LOCKED && SecurityUtils.currentUserId().equals(id)) {
            throw ApiException.badRequest("Bạn không thể khoá tài khoản của chính mình.");
        }
        if (request.status() == AccountStatus.LOCKED && user.getRole() == UserRole.ADMIN && isLastAdmin(user)) {
            throw ApiException.conflict("Không thể khoá tài khoản admin cuối cùng trong hệ thống.");
        }

        user.setAccountStatus(request.status());
        // Mở khoá thì xoá lý do khoá cũ để trạng thái tài khoản luôn nhất quán.
        user.setAccountStatusReason(request.status() == AccountStatus.LOCKED ? normalize(request.reason()) : null);
        user.setAccountStatusChangedAt(LocalDateTime.now());
        if (request.status() == AccountStatus.LOCKED) {
            // Khoá tài khoản thì đóng ngay mọi phiên đăng nhập đang mở.
            authSessionRepository.deleteByUser(user);
        }
        userRepository.save(user);
        return enrich(user, loadUserStats(List.of(user.getId())));
    }

    private boolean isLastAdmin(User user) {
        Long adminCount = userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN);
        return adminCount <= 1;
    }

    @Transactional
    public AdminUserResponse updateUserSeller(Long id, AdminRequests.UpdateSeller request) {
        User user = findUser(id);
        boolean seller = Boolean.TRUE.equals(request.seller());
        user.setSeller(seller);
        // Duyệt hay từ chối đều kết thúc yêu cầu đang chờ.
        user.setSellerRequestedAt(null);
        user.setSellerRequestStatus(seller ? "APPROVED" : "REJECTED");
        user.setSellerRequestNote(normalize(request.note()));
        user.setSellerRequestResolvedAt(LocalDateTime.now());
        userRepository.save(user);
        return enrich(user, loadUserStats(List.of(user.getId())));
    }

    /* ======================================================================
       2. QUẢN LÝ SẢN PHẨM & KIỂM DUYỆT
       ====================================================================== */

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> getProducts(
            String keyword,
            ModerationStatus moderationStatus,
            Boolean active,
            Integer page,
            Integer size
    ) {
        Page<Product> products = productRepository.search(
                normalize(keyword),
                null, null, null, null, null, null, null,
                active,
                moderationStatus,
                pageRequest(page, size)
        );
        return PageResponse.from(products, this::toProductResponse);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getProductCounts() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("total", productRepository.countByDeletedAtIsNull());
        counts.put("pending", productRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.PENDING));
        counts.put("approved", productRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.APPROVED));
        counts.put("rejected", productRepository.countByModerationStatusAndDeletedAtIsNull(ModerationStatus.REJECTED));
        counts.put("hidden", productRepository.countByActiveAndDeletedAtIsNull(Boolean.FALSE));
        counts.put("visible", productRepository.countByActiveAndDeletedAtIsNull(Boolean.TRUE));
        return counts;
    }

    @Transactional(readOnly = true)
    public AdminProductResponse getProduct(Long id) {
        Product product = findProduct(id);
        return toProductResponse(product);
    }

    @Transactional
    public AdminProductResponse moderateProduct(Long id, AdminRequests.ModerateProduct request) {
        Product product = findProduct(id);
        ModerationStatus status = request.status();
        if (status == null) {
            throw ApiException.badRequest("Trạng thái kiểm duyệt không hợp lệ.");
        }
        String reason = normalize(request.reason());
        if (status == ModerationStatus.REJECTED && reason == null) {
            throw ApiException.badRequest("Cần nhập lý do từ chối sản phẩm.");
        }

        product.setModerationStatus(status);
        // Chỉ lưu lý do khi từ chối, các trạng thái khác thì xoá lý do cũ.
        product.setModerationReason(status == ModerationStatus.REJECTED ? reason : null);
        product.setModeratedBy(currentAdmin());
        product.setModeratedAt(LocalDateTime.now());

        // Duyệt xong thì sản phẩm hiển thị lại trên sàn, từ chối hoặc chờ duyệt thì ẩn đi.
        product.setActive(status == ModerationStatus.APPROVED);

        productRepository.save(product);
        return toProductResponse(product);
    }

    @Transactional
    public AdminProductResponse updateVisibility(Long id, AdminRequests.UpdateVisibility request) {
        Product product = findProduct(id);
        if (Boolean.TRUE.equals(request.active()) && product.getModerationStatus() != ModerationStatus.APPROVED) {
            throw ApiException.badRequest("Chỉ hiện sản phẩm đã được phê duyệt. Hãy duyệt sản phẩm trước.");
        }
        product.setActive(Boolean.TRUE.equals(request.active()));
        productRepository.save(product);
        return toProductResponse(product);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProduct(id);
        // Luôn xoá mềm: sản phẩm biến mất khỏi sàn nhưng lịch sử đơn hàng vẫn còn
        // (OrderItem đã lưu sẵn tiêu đề, ảnh và đơn giá tại thời điểm mua).
        product.setActive(false);
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
    }

    /* ======================================================================
       3. QUẢN LÝ ĐƠN HÀNG
       ====================================================================== */

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> getOrders(
            String keyword,
            OrderStatus status,
            Boolean disputedOnly,
            Integer page,
            Integer size
    ) {
        String q = normalize(keyword);
        Page<Order> orders = Boolean.TRUE.equals(disputedOnly)
                ? orderRepository.searchDisputed(q, pageRequest(page, size))
                : orderRepository.search(q, status, pageRequest(page, size));
        return PageResponse.from(orders, this::toOrderResponse);
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse getOrderDetail(Long id) {
        Order order = findOrder(id);
        List<OrderItem> items = orderItemRepository.findByOrder(order);
        List<OrderStatusHistory> history =
                orderStatusHistoryRepository.findByOrderOrderByCreatedAtAsc(order);
        Refund refund = refundRepository.findFirstByOrderOrderByCreatedAtDesc(order).orElse(null);
        return AdminOrderResponse.from(order, items, history, refund);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getOrderCounts() {
        long processing = 0;
        long shipping = 0;
        long completed = 0;
        long cancelled = 0;
        long disputed = orderRepository.countByDisputedAtIsNotNullAndDisputeResolvedAtIsNull();

        for (Object[] row : orderRepository.countGroupedByStatus()) {
            OrderStatus status = (OrderStatus) row[0];
            long count = ((Number) row[1]).longValue();
            switch (status) {
                case WAITING_PAYMENT, WAITING_CONFIRM, PACKING -> processing += count;
                case SHIPPING, DELIVERED -> shipping += count;
                case COMPLETED -> completed += count;
                case CANCELLED -> cancelled += count;
                default -> { /* ignore other statuses */ }
            }
        }

        Map<String, Long> counts = new HashMap<>();
        counts.put("total", orderRepository.count());
        counts.put("processing", processing);
        counts.put("shipping", shipping);
        counts.put("completed", completed);
        counts.put("cancelled", cancelled);
        counts.put("disputed", disputed);
        return counts;
    }

    @Transactional
    public AdminOrderResponse updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = findOrder(id);
        OrderStatus next = request.getStatus();
        if (next == null) {
            throw ApiException.badRequest("Trạng thái đơn hàng không hợp lệ.");
        }
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("Đơn hàng đã kết thúc, không thể đổi trạng thái.");
        }
        if (next == order.getStatus()) {
            return toOrderResponse(order);
        }

        // Validate valid transition according to state machine
        if (!isValidTransition(order.getStatus(), next)) {
            throw ApiException.badRequest("Chuyển trạng thái từ " + order.getStatus() + " sang " + next + " không hợp lệ.");
        }

        OrderStatus previous = order.getStatus();
        String note = normalize(request.getNote());
        User admin = currentAdmin();

        applyStatus(order, next, note, admin);
        recordHistory(order, previous, order.getStatus(), note, admin);
        orderRepository.save(order);
        return toOrderResponse(order);
    }

    /** Kiểm tra chuyển đổi trạng thái hợp lệ theo state machine. */
    private boolean isValidTransition(OrderStatus from, OrderStatus to) {
        // COMPLETED and CANCELLED are terminal states
        if (from == OrderStatus.COMPLETED || from == OrderStatus.CANCELLED) {
            return false;
        }
        // Valid transitions
        switch (from) {
            case WAITING_PAYMENT:
                return to == OrderStatus.WAITING_CONFIRM || to == OrderStatus.CANCELLED;
            case WAITING_CONFIRM:
                return to == OrderStatus.PACKING || to == OrderStatus.CANCELLED;
            case PACKING:
                return to == OrderStatus.SHIPPING || to == OrderStatus.CANCELLED;
            case SHIPPING:
                return to == OrderStatus.DELIVERED || to == OrderStatus.CANCELLED;
            case DELIVERED:
                return to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED;
            default:
                return false;
        }
    }


    @Transactional
    public AdminOrderResponse openDispute(Long id, AdminRequests.OpenDispute request) {
        Order order = findOrder(id);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("Đơn hàng đã kết thúc nên không thể mở tranh chấp.");
        }
        if (order.getDisputedAt() != null && order.getDisputeResolvedAt() == null) {
            throw ApiException.conflict("Đơn hàng này đang có tranh chấp chờ xử lý.");
        }

        order.setDisputeReason(request.reason().trim());
        order.setDisputedAt(LocalDateTime.now());
        order.setDisputeFavor(null);
        order.setDisputeNote(null);
        order.setDisputeResolvedAt(null);
        orderRepository.save(order);
        return toOrderResponse(order);
    }@Transactional
    public AdminOrderResponse resolveDispute(Long id, AdminRequests.ResolveDispute request) {
        Order order = findOrder(id);
        if (order.getDisputedAt() == null || order.getDisputeResolvedAt() != null) {
            throw ApiException.badRequest("Đơn hàng không có tranh chấp đang chờ xử lý.");
        }
        if (request.favor() == null || normalize(request.note()) == null) {
            throw ApiException.badRequest("Cần chọn bên được lợi và nhập ghi chú quyết định.");
        }

        String note = normalize(request.note());
        User admin = currentAdmin();
        OrderStatus previous = order.getStatus();

        order.setDisputeFavor(request.favor());
        order.setDisputeNote(note);
        order.setDisputeResolvedAt(LocalDateTime.now());

        if (request.favor() == DisputeFavor.BUYER) {
            // Nghiêng về người mua: huỷ đơn, hoàn tồn kho và hoàn tiền nếu đã thanh toán.
            applyStatus(order, OrderStatus.CANCELLED, "Hoà giải tranh chấp: " + note, admin);
        } else if (request.favor() == DisputeFavor.SELLER) {
            // Nghiêng về người bán: ghi nhận kết quả nhưng Giữ nguyên trạng thái nếu đơn
            // đã PACKING/SHIPPING/DELIVERED (không lùi về PACKING)
            if (order.getStatus() == OrderStatus.WAITING_PAYMENT
                || order.getStatus() == OrderStatus.WAITING_CONFIRM
                || order.getStatus() == OrderStatus.PACKING) {
                // Chỉ đưa về PACKING nếu đơn đang ở trạng thái trước
                applyStatus(order, OrderStatus.PACKING, "Hoà giải tranh chấp: " + note, admin);
            }
            // Nếu đơn đã SHIPPING/DELIVERED/PACKING thì Giữ nguyên trạng thái,
            // chỉ ghi nhận kết quả tranh chấp
        }

        if (order.getStatus() != previous) {
            recordHistory(order, previous, order.getStatus(), "Hoà giải tranh chấp: " + note, admin);
        }
        orderRepository.save(order);
        return toOrderResponse(order);
    }

    /* ======================================================================
       HELPERS
       ====================================================================== */

    private void applyStatus(Order order, OrderStatus next, String note, User actor) {
        order.setStatus(next);
        if (next == OrderStatus.COMPLETED) {
            order.setCompletedAt(LocalDateTime.now());
        }
        if (next == OrderStatus.CANCELLED) {
            order.setCancelledBy(actor);
            order.setCancelReason(note);
            order.setCancelledAt(LocalDateTime.now());
            releaseStock(order);
            if (order.getPaymentStatus() == OrderPaymentStatus.PAID) {
                createRefundIfNeeded(order, actor);
            }
        }
    }

    /** Hoàn lại tồn kho khi đơn bị huỷ (chỉ làm một lần). */
    private void releaseStock(Order order) {
        if (order.getStockReleasedAt() != null) {
            return;
        }
        List<Product> productsToSave = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            if (product != null && item.getQuantity() != null) {
                int currentStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
                product.setStockQuantity(currentStock + item.getQuantity());
                productsToSave.add(product);
            }
        }
        if (!productsToSave.isEmpty()) {
            productRepository.saveAll(productsToSave);
        }
        order.setStockReleasedAt(LocalDateTime.now());
    }

    /** Tạo bản ghi hoàn tiền khi huỷ đơn PAID (idempotent). */
    private void createRefundIfNeeded(Order order, User admin) {
        if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            return;
        }
        // Đã có bản ghi hoàn tiền cho đơn này thì chỉ đồng bộ lại trạng thái thanh toán.
        if (refundRepository.findFirstByOrderOrderByCreatedAtDesc(order).isPresent()) {
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            return;
        }

        List<PaymentAllocation> allocations = paymentAllocationRepository.findByOrder(order);
        if (allocations.isEmpty()) {
            // Đơn đánh dấu PAID nhưng không có phân bổ thanh toán: chỉ cập nhật trạng thái,
            // không thể tạo refund vì bảng refunds bắt buộc phải có payment_id.
            order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
            return;
        }

        BigDecimal amount = allocations.stream()
                .map(PaymentAllocation::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.signum() <= 0 && order.getTotalAmount() != null) {
            amount = order.getTotalAmount();
        }

        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setPayment(allocations.get(0).getPayment());
        refund.setRefundCode("RF-" + order.getOrderCode());
        refund.setIdempotencyKey("ADMIN-CANCEL-" + order.getId());
        refund.setAmount(amount);
        refund.setStatus(RefundStatus.SUCCEEDED);
        refund.setCompletedAt(LocalDateTime.now());
        refundRepository.save(refund);

        // Set payment_status = REFUNDED
        order.setPaymentStatus(OrderPaymentStatus.REFUNDED);
    }

    private void recordHistory(Order order, OrderStatus previous, OrderStatus next, String note, User actor) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setPreviousStatus(previous);
        history.setNewStatus(next);
        history.setChangedBy(actor);
        history.setNote(note);
        orderStatusHistoryRepository.save(history);
    }

    private AdminProductResponse toProductResponse(Product product) {
        List<ProductImage> images = productImageRepository.findByProductOrderByDisplayOrderAsc(product);
        return AdminProductResponse.from(product, images);
    }

    private AdminOrderResponse toOrderResponse(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrder(order);
        return AdminOrderResponse.from(order, items);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng."));
    }

    private Product findProduct(Long id) {
        return productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng."));
    }

    private User currentAdmin() {
        return userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> ApiException.unauthorized("Bạn cần đăng nhập."));
    }

    private record UserStats(Map<Long, Long> orderStats, Map<Long, BigDecimal> orderAmounts,
                             Map<Long, Long> saleStats, Map<Long, BigDecimal> saleAmounts) {
    }

    private UserStats loadUserStats(List<Long> userIds) {
        Map<Long, Long> orderStats = new HashMap<>();
        Map<Long, BigDecimal> orderAmounts = new HashMap<>();
        Map<Long, Long> saleStats = new HashMap<>();
        Map<Long, BigDecimal> saleAmounts = new HashMap<>();

        if (userIds == null || userIds.isEmpty()) {
            return new UserStats(orderStats, orderAmounts, saleStats, saleAmounts);
        }

        // Chỉ tổng hợp cho đúng các user thuộc trang đang hiển thị (tránh N+1).
        for (Object[] row : orderRepository.totalSpentByBuyerInIds(userIds)) {
            Long userId = ((Number) row[0]).longValue();
            orderStats.put(userId, ((Number) row[1]).longValue());
            orderAmounts.put(userId, toDecimal(row[2]));
        }
        for (Object[] row : orderRepository.totalSoldBySellerInIds(userIds)) {
            Long userId = ((Number) row[0]).longValue();
            saleStats.put(userId, ((Number) row[1]).longValue());
            saleAmounts.put(userId, toDecimal(row[2]));
        }
        return new UserStats(orderStats, orderAmounts, saleStats, saleAmounts);
    }

    private AdminUserResponse enrich(User user, UserStats stats) {
        AdminUserResponse response = AdminUserResponse.from(user);
        Long bought = stats.orderStats().get(user.getId());
        Long sold = stats.saleStats().get(user.getId());
        BigDecimal boughtAmount = stats.orderAmounts().get(user.getId());
        BigDecimal soldAmount = stats.saleAmounts().get(user.getId());

        response.setOrderCount(bought != null ? bought : 0L);
        response.setOrderAmount(boughtAmount != null ? boughtAmount : BigDecimal.ZERO);
        response.setSaleCount(sold != null ? sold : 0L);
        response.setSaleAmount(soldAmount != null ? soldAmount : BigDecimal.ZERO);
        return response;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private PageRequest pageRequest(Integer page, Integer size) {
        int pageNumber = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size < 1 ? 20 : Math.min(size, PAGE_SIZE_MAX);
        return PageRequest.of(pageNumber, pageSize);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateTemporaryPassword() {
        StringBuilder builder = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }
}

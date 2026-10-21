package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.admin.AdminOrderResponse;
import com.example.demo.dto.admin.AdminProductResponse;
import com.example.demo.dto.admin.AdminRequests;
import com.example.demo.dto.admin.AdminUserResponse;
import com.example.demo.dto.order.UpdateOrderStatusRequest;
import com.example.demo.dto.product.PageResponse;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.ModerationStatus;
import com.example.demo.model.enums.OrderStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API quản trị cơ bản cho Admin Console.
 * Tất cả endpoint đều yêu cầu JWT hợp lệ có vai trò ADMIN (xem SecurityConfig).
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    /* ===================== NGƯỜI DÙNG ===================== */

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) Boolean seller,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getUsers(keyword, role, status, seller, page, pageSize)
        ));
    }

    @GetMapping("/users/counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUserCounts() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUserCounts()));
    }

    @GetMapping("/users/seller-requests")
    public ResponseEntity<ApiResponse<List<AdminUserResponse>>> getSellerRequests() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getSellerRequests()));
    }

    @PostMapping("/users")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createUser(
            @Valid @RequestBody AdminRequests.CreateUser request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã tạo tài khoản.", adminService.createUser(request)));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequests.UpdateUserStatus request
    ) {
        AdminUserResponse response = adminService.updateUserStatus(id, request);
        String message = request.status() == AccountStatus.LOCKED
                ? "Đã khoá tài khoản."
                : "Đã mở khoá tài khoản.";
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    @PatchMapping("/users/{id}/seller")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateUserSeller(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequests.UpdateSeller request
    ) {
        String message = Boolean.TRUE.equals(request.seller())
                ? "Đã cấp quyền người bán."
                : "Đã thu hồi quyền người bán.";
        return ResponseEntity.ok(ApiResponse.success(message, adminService.updateUserSeller(id, request)));
    }

    /* ===================== SẢN PHẨM ===================== */

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<AdminProductResponse>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ModerationStatus moderationStatus,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getProducts(keyword, moderationStatus, active, page, pageSize)
        ));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<AdminProductResponse>> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getProduct(id)));
    }

    @GetMapping("/products/counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getProductCounts() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getProductCounts()));
    }

    @PatchMapping("/products/{id}/moderation")
    public ResponseEntity<ApiResponse<AdminProductResponse>> moderateProduct(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequests.ModerateProduct request
    ) {
        String message = request.status() == ModerationStatus.APPROVED
                ? "Đã phê duyệt sản phẩm."
                : request.status() == ModerationStatus.REJECTED
                ? "Đã từ chối sản phẩm."
                : "Đã chuyển sản phẩm về trạng thái chờ duyệt.";
        return ResponseEntity.ok(ApiResponse.success(message, adminService.moderateProduct(id, request)));
    }

    @PatchMapping("/products/{id}/visibility")
    public ResponseEntity<ApiResponse<AdminProductResponse>> updateProductVisibility(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequests.UpdateVisibility request
    ) {
        String message = Boolean.TRUE.equals(request.active())
                ? "Đã hiện sản phẩm trở lại trên sàn."
                : "Đã ẩn sản phẩm khỏi sàn.";
        return ResponseEntity.ok(ApiResponse.success(message, adminService.updateVisibility(id, request)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        adminService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xoá sản phẩm khỏi sàn.", null));
    }

    /* ===================== ĐƠN HÀNG ===================== */

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<AdminOrderResponse>>> getOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false, defaultValue = "false") Boolean disputed,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getOrders(keyword, status, disputed, page, pageSize)
        ));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> getOrderDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getOrderDetail(id)));
    }

    @GetMapping("/orders/counts")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getOrderCounts() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getOrderCounts()));
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã cập nhật trạng thái đơn hàng.",
                adminService.updateOrderStatus(id, request)
        ));
    }

    @PatchMapping("/orders/{id}/dispute")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> openDispute(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequests.OpenDispute request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã mở tranh chấp cho đơn hàng.",
                adminService.openDispute(id, request)
        ));
    }

    @PatchMapping("/orders/{id}/dispute/resolve")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> resolveDispute(
            @PathVariable Long id,
            @Valid @RequestBody AdminRequests.ResolveDispute request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã xử lý tranh chấp.",
                adminService.resolveDispute(id, request)
        ));
    }
}

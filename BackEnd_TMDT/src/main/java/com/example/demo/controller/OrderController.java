package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.order.CheckoutRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/buy-now")
    public ResponseEntity<ApiResponse<OrderResponse>> buyNow(@Valid @RequestBody CheckoutRequest request) {
        OrderResponse response = orderService.buyNow(request);
        return ResponseEntity.ok(ApiResponse.success("Đặt hàng Mua Ngay thành công!", response));
    }
    // lich su mua hang
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        List<OrderResponse> orders = orderService.getMyOrders(status, keyword);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công!", orders));
    }
}
package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.order.CheckoutRequest;
import com.example.demo.dto.order.OrderResponse;
import com.example.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
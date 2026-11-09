
package com.example.demo.controller;

import com.example.demo.dto.payment.MockPaymentRequest;
import com.example.demo.dto.payment.MockPaymentResponse;
import com.example.demo.dto.ApiResponse;
import com.example.demo.service.MockPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MockPaymentController {

    private final MockPaymentService mockPaymentService;

    /**
     * API mô phỏng thanh toán.
     *
     * SUCCESS: Thanh toán thành công.
     * FAILED: Thanh toán thất bại.
     */
    @PostMapping("/mock/{paymentCode}")
    public ResponseEntity<ApiResponse<MockPaymentResponse>> processMockPayment(
            @PathVariable String paymentCode,
            @Valid @RequestBody MockPaymentRequest request
    ) {
        MockPaymentResponse response =
                mockPaymentService.processPayment(paymentCode, request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Xử lý thanh toán mô phỏng thành công.",
                        response
                )
        );
    }

    
    /**
     * Tạo giao dịch mới để người mua thanh toán lại.
     */
    @PostMapping("/retry/{paymentCode}")
    public ResponseEntity<ApiResponse<MockPaymentResponse>> retryPayment(
            @PathVariable String paymentCode
    ) {
        MockPaymentResponse response =
                mockPaymentService.retryPayment(paymentCode);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Tạo giao dịch thanh toán lại thành công.",
                        response
                )
        );
    }

}

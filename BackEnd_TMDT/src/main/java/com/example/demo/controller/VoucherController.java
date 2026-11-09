
package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.voucher.VoucherAvailableRequest;
import com.example.demo.dto.voucher.VoucherAvailableResponse;
import com.example.demo.dto.voucher.VoucherValidateRequest;
import com.example.demo.dto.voucher.VoucherValidateResponse;
import com.example.demo.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VoucherController {

    private final VoucherService voucherService;

    @PostMapping("/available")
    public ResponseEntity<ApiResponse<List<VoucherAvailableResponse>>> getAvailableVouchers(
            @Valid @RequestBody VoucherAvailableRequest request) {

        List<VoucherAvailableResponse> response =
                voucherService.getAvailableVouchers(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách voucher có thể áp dụng thành công!",
                        response
                )
        );
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<VoucherValidateResponse>> validateVouchers(
            @Valid @RequestBody VoucherValidateRequest request) {

        VoucherValidateResponse response =
                voucherService.validateVouchers(request);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Kiểm tra voucher thành công!",
                        response
                )
        );
    }
}

package com.example.demo.controller;

import com.example.demo.dto.voucher.VoucherManagementResponse;
import com.example.demo.security.UserPrincipal;
import com.example.demo.service.VoucherManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.example.demo.dto.voucher.VoucherManagementRequest;
import jakarta.validation.Valid;
import com.example.demo.dto.voucher.VoucherStatusRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VoucherManagementController {

    private final VoucherManagementService voucherManagementService;

    // ADMIN: Lấy tất cả voucher
    @GetMapping("/admin/vouchers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllVouchers() {
        List<VoucherManagementResponse> vouchers =
                voucherManagementService.getAllVouchers();

        return ResponseEntity.ok(vouchers);
    }

    // SELLER: Chỉ lấy voucher của chính mình
    @GetMapping("/seller/vouchers")
    @PreAuthorize("hasRole('USER') and @voucherManagementService.isSeller(authentication.principal.id)")
    public ResponseEntity<?> getSellerVouchers(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<VoucherManagementResponse> vouchers =
                voucherManagementService.getSellerVouchers(principal.getId());

        return ResponseEntity.ok(vouchers);
    }

    
    // SELLER: Tạo voucher mới
    @PostMapping("/seller/vouchers")
    @PreAuthorize("hasRole('USER') and @voucherManagementService.isSeller(authentication.principal.id)")
    public ResponseEntity<?> createSellerVoucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VoucherManagementRequest request
    ) {
        VoucherManagementResponse voucher =
                voucherManagementService.createSellerVoucher(
                        principal.getId(),
                        request
                );

        return ResponseEntity.status(201).body(voucher);
    }

    
    // SELLER: Cập nhật voucher
    @PutMapping("/seller/vouchers/{id}")
    @PreAuthorize("hasRole('USER') and @voucherManagementService.isSeller(authentication.principal.id)")
    public ResponseEntity<?> updateSellerVoucher(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VoucherManagementRequest request
    ) {
        VoucherManagementResponse voucher =
                voucherManagementService.updateSellerVoucher(
                        principal.getId(),
                        id,
                        request
                );

        return ResponseEntity.ok(voucher);
    }

    @PatchMapping("/seller/vouchers/{id}/status")
    @PreAuthorize("hasRole('USER') and @voucherManagementService.isSeller(authentication.principal.id)")
    public ResponseEntity<?> updateSellerVoucherStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody VoucherStatusRequest request
    ) {
        return ResponseEntity.ok(
                voucherManagementService.updateSellerVoucherStatus(
                        principal.getId(),
                        id,
                        request
                )
        );
    }

}
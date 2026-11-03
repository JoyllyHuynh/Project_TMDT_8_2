
package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.address.AddressRequest;
import com.example.demo.dto.address.AddressResponse;
import com.example.demo.security.SecurityUtils;
import com.example.demo.service.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;

    // Lấy danh sách địa chỉ của tài khoản hiện tại
    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses() {
        Long userId = SecurityUtils.currentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách địa chỉ thành công.",
                addressService.getMyAddresses(userId)
        ));
    }

    // Thêm địa chỉ
    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = SecurityUtils.currentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Thêm địa chỉ thành công.",
                addressService.createAddress(userId, request)
        ));
    }

    // Cập nhật địa chỉ
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request
    ) {
        Long userId = SecurityUtils.currentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Cập nhật địa chỉ thành công.",
                addressService.updateAddress(userId, id, request)
        ));
    }

    // Xóa địa chỉ
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.currentUserId();

        addressService.deleteAddress(userId, id);

        return ResponseEntity.ok(ApiResponse.success(
                "Xóa địa chỉ thành công.",
                null
        ));
    }

    // Chọn địa chỉ mặc định
    @PutMapping("/{id}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @PathVariable Long id
    ) {
        Long userId = SecurityUtils.currentUserId();

        return ResponseEntity.ok(ApiResponse.success(
                "Đặt địa chỉ mặc định thành công.",
                addressService.setDefaultAddress(userId, id)
        ));
    }
}

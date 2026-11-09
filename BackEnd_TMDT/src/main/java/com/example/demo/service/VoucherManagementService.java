package com.example.demo.service;

import com.example.demo.dto.voucher.VoucherManagementResponse;
import com.example.demo.dto.voucher.VoucherStatusRequest;
import com.example.demo.model.Voucher;
import com.example.demo.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.repository.UserRepository;
import java.util.List;
import com.example.demo.dto.voucher.VoucherManagementRequest;
import com.example.demo.exception.ApiException;
import com.example.demo.model.User;
import com.example.demo.model.enums.VoucherDiscountType;
import com.example.demo.model.enums.VoucherType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
@Service
@RequiredArgsConstructor
public class VoucherManagementService {

    private final VoucherRepository voucherRepository;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<VoucherManagementResponse> getAllVouchers() {
        return voucherRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<VoucherManagementResponse> getSellerVouchers(Long sellerId) {
        return voucherRepository.findAllBySeller_IdOrderByCreatedAtDesc(sellerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    
    @Transactional
    public VoucherManagementResponse createSellerVoucher(
            Long sellerId,
            VoucherManagementRequest request
    ) {
        // 1. Kiểm tra Seller tồn tại và có quyền bán hàng
        User seller = userRepository.findById(sellerId)
                .filter(user -> Boolean.TRUE.equals(user.getSeller()))
                .orElseThrow(() ->
                        ApiException.forbidden("Bạn không có quyền tạo voucher.")
                );

        // 2. Chuẩn hóa mã voucher
        String code = request.code().trim().toUpperCase(Locale.ROOT);

        if (voucherRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã voucher đã tồn tại.");
        }

        // 3. Kiểm tra hình thức giảm giá
        if (request.discountType() == VoucherDiscountType.PERCENTAGE) {
            if (request.discountValue().compareTo(new BigDecimal("100")) > 0) {
                throw ApiException.badRequest(
                        "Mức giảm phần trăm không được vượt quá 100%."
                );
            }
        }

        // 4. Kiểm tra mức giảm tối đa
        if (request.discountType() == VoucherDiscountType.FIXED
                && request.maxDiscountAmount() != null) {
            throw ApiException.badRequest(
                    "Voucher giảm tiền cố định không cần mức giảm tối đa."
            );
        }

        if (request.discountType() == VoucherDiscountType.PERCENTAGE
                && request.maxDiscountAmount() != null
                && request.maxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(
                    "Mức giảm tối đa phải lớn hơn 0."
            );
        }

        // 5. Kiểm tra thời gian
        LocalDateTime now = LocalDateTime.now();

        if (!request.startAt().isBefore(request.endAt())) {
            throw ApiException.badRequest(
                    "Thời gian bắt đầu phải trước thời gian kết thúc."
            );
        }

        // 6. Kiểm tra số lượt sử dụng
        if (request.usageLimit() != null
                && request.usageLimitPerUser() > request.usageLimit()) {
            throw ApiException.badRequest(
                    "Giới hạn sử dụng mỗi người không được vượt tổng lượt sử dụng."
            );
        }

        // 7. Tạo Entity
        Voucher voucher = new Voucher();

        voucher.setCode(code);
        voucher.setName(request.name().trim());
        voucher.setDescription(request.description());
        voucher.setVoucherType(VoucherType.SELLER);
        voucher.setDiscountType(request.discountType());
        voucher.setDiscountValue(request.discountValue());
        voucher.setMaxDiscountAmount(request.maxDiscountAmount());
        voucher.setMinOrderAmount(request.minOrderAmount());
        voucher.setSeller(seller);
        voucher.setUsageLimit(request.usageLimit());
        voucher.setUsedCount(0);
        voucher.setUsageLimitPerUser(request.usageLimitPerUser());
        voucher.setStartAt(request.startAt());
        voucher.setEndAt(request.endAt());
        voucher.setActive(true);

        // 8. Lưu database
        Voucher savedVoucher = voucherRepository.save(voucher);

        // 9. Trả về Response DTO
        return toResponse(savedVoucher);
    }

    
    @Transactional
    public VoucherManagementResponse updateSellerVoucher(
            Long sellerId,
            Long voucherId,
            VoucherManagementRequest request
    ) {
        // 1. Kiểm tra Seller
        User seller = userRepository.findById(sellerId)
                .filter(user -> Boolean.TRUE.equals(user.getSeller()))
                .orElseThrow(() ->
                        ApiException.forbidden("Bạn không có quyền cập nhật voucher.")
                );

        // 2. Tìm voucher và kiểm tra quyền sở hữu
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() ->
                        ApiException.notFound("Không tìm thấy voucher.")
                );

        if (voucher.getSeller() == null
                || !voucher.getSeller().getId().equals(sellerId)) {
            throw ApiException.forbidden(
                    "Bạn không có quyền cập nhật voucher này."
            );
        }

        // 3. Kiểm tra mã voucher trùng
        String code = request.code().trim().toUpperCase(Locale.ROOT);

        boolean duplicateCode = voucherRepository.existsByCodeIgnoreCase(code);

        if (duplicateCode && !code.equalsIgnoreCase(voucher.getCode())) {
            throw ApiException.conflict("Mã voucher đã tồn tại.");
        }

        // 4. Kiểm tra loại giảm giá
        if (request.discountType() == VoucherDiscountType.PERCENTAGE
                && request.discountValue().compareTo(new BigDecimal("100")) > 0) {
            throw ApiException.badRequest(
                    "Mức giảm phần trăm không được vượt quá 100%."
            );
        }

        // 5. Kiểm tra mức giảm tối đa
        if (request.discountType() == VoucherDiscountType.FIXED
                && request.maxDiscountAmount() != null) {
            throw ApiException.badRequest(
                    "Voucher giảm tiền cố định không cần mức giảm tối đa."
            );
        }

        if (request.discountType() == VoucherDiscountType.PERCENTAGE
                && request.maxDiscountAmount() != null
                && request.maxDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(
                    "Mức giảm tối đa phải lớn hơn 0."
            );
        }

        // 6. Kiểm tra thời gian
        if (!request.startAt().isBefore(request.endAt())) {
            throw ApiException.badRequest(
                    "Thời gian bắt đầu phải trước thời gian kết thúc."
            );
        }

        // 7. Kiểm tra giới hạn sử dụng
        if (request.usageLimit() != null
                && (request.usageLimit() < voucher.getUsedCount()
                || request.usageLimitPerUser() > request.usageLimit())) {
            throw ApiException.badRequest(
                    "Giới hạn sử dụng không hợp lệ."
            );
        }

        // 8. Cập nhật thông tin
        voucher.setCode(code);
        voucher.setName(request.name().trim());
        voucher.setDescription(request.description());
        voucher.setDiscountType(request.discountType());
        voucher.setDiscountValue(request.discountValue());
        voucher.setMaxDiscountAmount(request.maxDiscountAmount());
        voucher.setMinOrderAmount(request.minOrderAmount());
        voucher.setUsageLimit(request.usageLimit());
        voucher.setUsageLimitPerUser(request.usageLimitPerUser());
        voucher.setStartAt(request.startAt());
        voucher.setEndAt(request.endAt());

        // Không thay đổi usedCount, seller, active và voucherType

        return toResponse(voucherRepository.save(voucher));
    }


    private VoucherManagementResponse toResponse(Voucher voucher) {
        return VoucherManagementResponse.builder()
                .id(voucher.getId())
                .code(voucher.getCode())
                .name(voucher.getName())
                .description(voucher.getDescription())
                .voucherType(voucher.getVoucherType())
                .discountType(voucher.getDiscountType())
                .discountValue(voucher.getDiscountValue())
                .maxDiscountAmount(voucher.getMaxDiscountAmount())
                .minOrderAmount(voucher.getMinOrderAmount())
                .sellerId(
                        voucher.getSeller() != null
                                ? voucher.getSeller().getId()
                                : null
                )
                .sellerName(
                        voucher.getSeller() != null
                                ? voucher.getSeller().getFullName()
                                : null
                )
                .usageLimit(voucher.getUsageLimit())
                .usedCount(voucher.getUsedCount())
                .usageLimitPerUser(voucher.getUsageLimitPerUser())
                .startAt(voucher.getStartAt())
                .endAt(voucher.getEndAt())
                .active(voucher.getActive())
                .createdAt(voucher.getCreatedAt())
                .updatedAt(voucher.getUpdatedAt())
                .build();
    }

    public boolean isSeller(Long sellerId) {
        return userRepository.findById(sellerId)
                .map(user -> Boolean.TRUE.equals(user.getSeller()))
                .orElse(false);
    }

    @Transactional
    public VoucherManagementResponse updateSellerVoucherStatus(
            Long sellerId,
            Long voucherId,
            VoucherStatusRequest request
    ) {
        // Kiểm tra người dùng có phải Seller không
        userRepository.findById(sellerId)
                .filter(user -> Boolean.TRUE.equals(user.getSeller()))
                .orElseThrow(() ->
                        ApiException.forbidden("Bạn không có quyền cập nhật trạng thái voucher."));

        // Tìm voucher
        Voucher voucher = voucherRepository.findByIdForUpdate(voucherId)
                .orElseThrow(() ->
                        ApiException.notFound("Không tìm thấy voucher."));

        // Kiểm tra quyền sở hữu
        if (voucher.getSeller() == null
                || !voucher.getSeller().getId().equals(sellerId)) {
            throw ApiException.forbidden("Bạn không có quyền cập nhật voucher này.");
        }

        // Không cho kích hoạt voucher đã hết hạn
        if (Boolean.TRUE.equals(request.active())
                && voucher.getEndAt() != null
                && voucher.getEndAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Không thể kích hoạt voucher đã hết hạn.");
        }

        voucher.setActive(request.active());

        return toResponse(voucherRepository.save(voucher));
    }
}
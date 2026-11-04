package com.example.demo.dto.admin;

import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Dữ liệu người dùng hiển thị trên Admin Console (Quản lý tài khoản).
 */
@Getter
@Setter
@Builder
public class AdminUserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private UserRole role;
    private Boolean seller;
    private AccountStatus accountStatus;
    private String accountStatusReason;
    private LocalDateTime accountStatusChangedAt;
    private LocalDateTime sellerRequestedAt;
    private String sellerRequestStatus;
    private String sellerRequestNote;
    private LocalDateTime sellerRequestResolvedAt;
    private LocalDateTime createdAt;

    /** Số đơn đã mua và tổng tiền đã chi (để trống nếu chưa có giao dịch). */
    private Long orderCount;
    private BigDecimal orderAmount;

    /** Số đơn đã bán và tổng tiền nhận được. */
    private Long saleCount;
    private BigDecimal saleAmount;

    /** Chỉ trả về khi Admin tạo tài khoản: mật khẩu tạm để trao lại cho người dùng. */
    private String temporaryPassword;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .seller(user.getSeller())
                .accountStatus(user.getAccountStatus())
                .accountStatusReason(user.getAccountStatusReason())
                .accountStatusChangedAt(user.getAccountStatusChangedAt())
                .sellerRequestedAt(user.getSellerRequestedAt())
                .sellerRequestStatus(user.getSellerRequestStatus())
                .sellerRequestNote(user.getSellerRequestNote())
                .sellerRequestResolvedAt(user.getSellerRequestResolvedAt())
                .createdAt(user.getCreatedAt())
                .orderCount(0L)
                .orderAmount(BigDecimal.ZERO)
                .saleCount(0L)
                .saleAmount(BigDecimal.ZERO)
                .build();
    }
}

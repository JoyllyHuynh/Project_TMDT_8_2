package com.example.demo.dto.admin;

import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.DisputeFavor;
import com.example.demo.model.enums.ModerationStatus;
import com.example.demo.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Các request body của Admin Console, gom trong một file để giữ cấu trúc dto/admin gọn gàng.
 */
public final class AdminRequests {

    private AdminRequests() {
    }

    /** Khoá / mở khoá tài khoản người dùng. */
    public record UpdateUserStatus(@NotNull AccountStatus status, String reason) {
    }

    /** Cấp / thu hồi quyền người bán (duyệt đăng ký người bán). */
    public record UpdateSeller(@NotNull Boolean seller, String note) {
    }

    /** Admin tạo tài khoản thủ công; mật khẩu để trống thì hệ thống sinh mật khẩu tạm. */
    public record CreateUser(
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$", message = "Mật khẩu phải có ít nhất 8 ký tự, chứa ít nhất 1 chữ hoa, 1 chữ thường và 1 số.") String password,
            Boolean seller,
            UserRole role
    ) {
    }

    /** Duyệt / từ chối sản phẩm sau kiểm duyệt. */
    public record ModerateProduct(@NotNull ModerationStatus status, String reason) {
    }

    /** Ẩn / hiện sản phẩm trên sàn. */
    public record UpdateVisibility(@NotNull Boolean active) {
    }

    /** Admin mở tranh chấp cho đơn hàng (khi có khiếu nại từ người mua/người bán). */
    public record OpenDispute(@NotBlank String reason) {
    }

    /** Admin xử lý tranh chấp: chọn bên thắng và ghi chú quyết định. */
    public record ResolveDispute(@NotNull DisputeFavor favor, @NotBlank String note) {
    }
}

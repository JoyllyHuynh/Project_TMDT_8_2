
package com.example.demo.dto.voucher;

import com.example.demo.model.enums.VoucherDiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherManagementRequest(

        @NotBlank(message = "Mã voucher không được để trống")
        @Size(max = 50, message = "Mã voucher tối đa 50 ký tự")
        String code,

        @NotBlank(message = "Tên voucher không được để trống")
        @Size(max = 150, message = "Tên voucher tối đa 150 ký tự")
        String name,

        String description,

        @NotNull(message = "Hình thức giảm giá không được để trống")
        VoucherDiscountType discountType,

        @NotNull(message = "Giá trị giảm không được để trống")
        @DecimalMin(value = "0.01", message = "Giá trị giảm phải lớn hơn 0")
        BigDecimal discountValue,

        @DecimalMin(value = "0.00", message = "Mức giảm tối đa không hợp lệ")
        BigDecimal maxDiscountAmount,

        @NotNull(message = "Đơn hàng tối thiểu không được để trống")
        @DecimalMin(value = "0.00", message = "Đơn hàng tối thiểu không được âm")
        BigDecimal minOrderAmount,

        @Min(value = 1, message = "Số lượt sử dụng phải lớn hơn 0")
        Integer usageLimit,

        @NotNull(message = "Giới hạn sử dụng mỗi người không được để trống")
        @Min(value = 1, message = "Giới hạn sử dụng mỗi người phải lớn hơn 0")
        Integer usageLimitPerUser,

        @NotNull(message = "Thời gian bắt đầu không được để trống")
        LocalDateTime startAt,

        @NotNull(message = "Thời gian kết thúc không được để trống")
        LocalDateTime endAt

) {}

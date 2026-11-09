package com.example.demo.dto.voucher;

import jakarta.validation.constraints.NotNull;

public record VoucherStatusRequest(
        @NotNull Boolean active
) {
}
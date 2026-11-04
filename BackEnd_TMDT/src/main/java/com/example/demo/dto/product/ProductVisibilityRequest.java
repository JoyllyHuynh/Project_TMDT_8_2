package com.example.demo.dto.product;

import jakarta.validation.constraints.NotNull;

public record ProductVisibilityRequest(@NotNull Boolean active) {
}

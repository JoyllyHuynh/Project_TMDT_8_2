package com.example.demo.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MockPaymentRequest {

    @NotBlank
    private String action;
}
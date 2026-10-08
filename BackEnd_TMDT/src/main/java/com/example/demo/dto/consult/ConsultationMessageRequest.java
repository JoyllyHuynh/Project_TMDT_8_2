package com.example.demo.dto.consult;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsultationMessageRequest {

    @NotBlank
    private String content;
}

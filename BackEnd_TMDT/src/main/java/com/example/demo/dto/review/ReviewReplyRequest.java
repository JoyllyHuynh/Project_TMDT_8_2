package com.example.demo.dto.review;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewReplyRequest {

    @NotBlank
    private String content;
}

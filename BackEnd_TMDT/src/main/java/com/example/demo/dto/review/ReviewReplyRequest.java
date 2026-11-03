package com.example.demo.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewReplyRequest {

    @NotBlank
    @Size(max = 1000, message = "Phản hồi tối đa 1000 ký tự.")
    private String content;
}

package com.example.demo.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReviewRequest {

    @NotNull
    private Long orderItemId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @NotBlank(message = "Vui lòng viết nhận xét về tình trạng thực tế của truyện.")
    @Size(max = 1000, message = "Nhận xét tối đa 1000 ký tự.")
    private String content;

    @Size(max = 5, message = "Mỗi đánh giá tối đa 5 ảnh.")
    private List<String> imageUrls;
}

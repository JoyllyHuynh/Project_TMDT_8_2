package com.example.demo.dto.catalog;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PublisherRequest {

    @NotBlank
    private String name;

    private String slug;
}

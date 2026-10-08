package com.example.demo.dto.catalog;

import com.example.demo.model.Author;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthorResponse {

    private Integer id;
    private String name;

    public static AuthorResponse from(Author author) {
        return AuthorResponse.builder()
                .id(author.getId())
                .name(author.getName())
                .build();
    }
}

package com.example.demo.dto.catalog;

import com.example.demo.model.Publisher;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PublisherResponse {

    private Integer id;
    private String name;
    private String slug;

    public static PublisherResponse from(Publisher publisher) {
        return PublisherResponse.builder()
                .id(publisher.getId())
                .name(publisher.getName())
                .slug(publisher.getSlug())
                .build();
    }
}

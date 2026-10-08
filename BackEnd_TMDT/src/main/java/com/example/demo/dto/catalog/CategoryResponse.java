package com.example.demo.dto.catalog;

import com.example.demo.model.Category;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryResponse {

    private Integer id;
    private String name;
    private String slug;
    private Integer parentId;
    private String parentName;

    public static CategoryResponse from(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .parentId(category.getParent() == null ? null : category.getParent().getId())
                .parentName(category.getParent() == null ? null : category.getParent().getName())
                .build();
    }
}

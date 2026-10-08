package com.example.demo.service;

import com.example.demo.dto.catalog.AuthorResponse;
import com.example.demo.dto.catalog.CategoryResponse;
import com.example.demo.dto.catalog.PublisherResponse;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Author;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    public CategoryResponse getCategory(Integer id) {
        return CategoryResponse.from(categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thể loại.")));
    }

    public List<AuthorResponse> getAuthors(String keyword) {
        List<Author> authors = keyword == null || keyword.isBlank()
                ? authorRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                : authorRepository.findByNameContainingIgnoreCase(keyword.trim());
        return authors.stream().map(AuthorResponse::from).toList();
    }

    public AuthorResponse getAuthor(Integer id) {
        return AuthorResponse.from(authorRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tác giả.")));
    }

    public List<PublisherResponse> getPublishers() {
        return publisherRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(PublisherResponse::from)
                .toList();
    }

    public PublisherResponse getPublisher(Integer id) {
        return PublisherResponse.from(publisherRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà xuất bản.")));
    }
}

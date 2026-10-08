package com.example.demo.repository;

import com.example.demo.model.Publisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublisherRepository extends JpaRepository<Publisher, Integer> {

    Optional<Publisher> findBySlug(String slug);

    boolean existsBySlug(String slug);
}

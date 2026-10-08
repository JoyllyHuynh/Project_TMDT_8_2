package com.example.demo.repository;

import com.example.demo.model.Product;
import com.example.demo.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductOrderByDisplayOrderAsc(Product product);

    void deleteByProduct(Product product);
}

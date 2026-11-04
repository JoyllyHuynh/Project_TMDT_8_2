package com.example.demo.config;

import com.example.demo.service.ReviewImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ReviewImageStorage reviewImageStorage;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = reviewImageStorage.getRoot().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/reviews/**")
                .addResourceLocations(location);
    }
}

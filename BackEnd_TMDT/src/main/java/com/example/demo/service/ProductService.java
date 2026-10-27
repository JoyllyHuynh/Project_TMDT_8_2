package com.example.demo.service;

import com.example.demo.dto.product.PageResponse;
import com.example.demo.dto.product.ProductRequest;
import com.example.demo.dto.product.ProductResponse;
import com.example.demo.dto.product.ProductVisibilityRequest;
import com.example.demo.exception.ApiException;
import com.example.demo.model.Product;
import com.example.demo.model.ProductImage;
import com.example.demo.model.User;
import com.example.demo.model.enums.ListingType;
import com.example.demo.model.enums.ModerationStatus;
import com.example.demo.model.enums.UserRole;
import com.example.demo.repository.AuthorRepository;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.ProductImageRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PublisherRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.SecurityUtils;
import com.example.demo.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final AuthorRepository authorRepository;
    private final PublisherRepository publisherRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProducts(
            String keyword,
            Integer categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minCondition,
            Integer maxCondition,
            String sellerName,
            ListingType listingType,
            int page,
            int pageSize,
            String sort,
            String direction
    ) {
        validateFilters(minPrice, maxPrice, minCondition, maxCondition);
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw ApiException.notFound("Không tìm thấy thể loại.");
        }

        Pageable pageable = PageRequest.of(page, pageSize, resolveSort(sort, direction));
        Page<Product> products = productRepository.search(
                normalizeKeyword(keyword),
                categoryId,
                listingType,
                minPrice,
                maxPrice,
                minCondition,
                maxCondition,
                normalizeKeyword(sellerName),
                true,
                ModerationStatus.APPROVED,
                pageable
        );
        return PageResponse.from(products, this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .filter(item -> Boolean.TRUE.equals(item.getActive())
                        && item.getModerationStatus() == ModerationStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));
        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getMyProducts() {
        UserPrincipal principal = SecurityUtils.currentUser();
        User seller = userRepository.findById(principal.getId())
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản người bán."));
        return productRepository.findBySellerAndDeletedAtIsNullOrderByCreatedAtDesc(seller)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        UserPrincipal principal = SecurityUtils.currentUser();
        User seller = userRepository.findById(principal.getId())
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản người bán."));
        if (seller.getRole() != UserRole.ADMIN && !Boolean.TRUE.equals(seller.getSeller())) {
            throw ApiException.forbidden("Tài khoản chưa được kích hoạt quyền người bán.");
        }

        Product product = new Product();
        product.setSeller(seller);
        product.setSlug(createSlug(request.getTitle()));
        applyRequest(product, request);
        product.setActive(true);
        product.setModerationStatus(ModerationStatus.PENDING);
        Product saved = productRepository.save(product);
        saveImages(saved, request);
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));
        verifyOwnerOrAdmin(product);
        applyRequest(product, request);
        product.setModerationStatus(ModerationStatus.PENDING);
        Product saved = productRepository.save(product);
        saveImages(saved, request);
        return toResponse(saved);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));
        verifyOwnerOrAdmin(product);
        product.setActive(false);
        product.setDeletedAt(LocalDateTime.now());
    }

    @Transactional
    public ProductResponse updateVisibility(Long id, ProductVisibilityRequest request) {
        if (request == null || request.active() == null) {
            throw ApiException.badRequest("Trạng thái hiển thị không hợp lệ.");
        }
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm."));
        verifyOwnerOrAdmin(product);
        if (Boolean.TRUE.equals(request.active()) && product.getModerationStatus() != ModerationStatus.APPROVED) {
            throw ApiException.badRequest("Chỉ có thể hiển thị sản phẩm đã được duyệt.");
        }

        product.setActive(request.active());
        productRepository.save(product);
        return toResponse(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription().trim());
        product.setCategory(categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thể loại.")));
        product.setAuthor(request.getAuthorId() == null ? null : authorRepository.findById(request.getAuthorId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tác giả.")));
        product.setPublisher(request.getPublisherId() == null ? null : publisherRepository.findById(request.getPublisherId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà xuất bản.")));
        product.setVolumeNumbers(request.getVolumeNumbers());
        product.setEditionType(request.getEditionType());
        product.setPublicationYear(request.getPublicationYear());
        product.setConditionPercent(request.getConditionPercent());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setListingType(request.getListingType() == null ? ListingType.SELL : request.getListingType());
        product.setTradeWishNote(request.getTradeWishNote());
    }

    private void saveImages(Product product, ProductRequest request) {
        productImageRepository.deleteByProduct(product);
        if (request.getImageUrls() == null) return;

        for (int order = 0; order < request.getImageUrls().size(); order++) {
            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setImageUrl(request.getImageUrls().get(order).trim());
            image.setDisplayOrder(order);
            productImageRepository.save(image);
        }
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.from(
                product,
                productImageRepository.findByProductOrderByDisplayOrderAsc(product)
        );
    }

    private void verifyOwnerOrAdmin(Product product) {
        UserPrincipal principal = SecurityUtils.currentUser();
        if (principal.getRole() != UserRole.ADMIN && !product.getSeller().getId().equals(principal.getId())) {
            throw ApiException.forbidden("Bạn không có quyền quản lý sản phẩm này.");
        }
    }

    private Sort resolveSort(String sort, String direction) {
        Sort.Direction sortDirection = switch (direction == null ? "desc" : direction.toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw ApiException.badRequest("Thứ tự sắp xếp không hợp lệ.");
        };

        String property = switch (sort == null ? "newest" : sort.toLowerCase(Locale.ROOT)) {
            case "newest" -> "createdAt";
            case "price" -> "price";
            default -> throw ApiException.badRequest("Tiêu chí sắp xếp không hợp lệ.");
        };
        return Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private void validateFilters(
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minCondition,
            Integer maxCondition
    ) {
        if (minPrice != null && minPrice.signum() < 0
                || maxPrice != null && maxPrice.signum() < 0
                || minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw ApiException.badRequest("Khoảng giá không hợp lệ.");
        }
        if (minCondition != null && (minCondition < 0 || minCondition > 100)
                || maxCondition != null && (maxCondition < 0 || maxCondition > 100)
                || minCondition != null && maxCondition != null && minCondition > maxCondition) {
            throw ApiException.badRequest("Khoảng tình trạng không hợp lệ.");
        }
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private String createSlug(String title) {
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String base = normalized.isBlank() ? "product" : normalized;
        if (base.length() > 245) base = base.substring(0, 245).replaceAll("-$", "");
        return base + "-" + suffix;
    }
}

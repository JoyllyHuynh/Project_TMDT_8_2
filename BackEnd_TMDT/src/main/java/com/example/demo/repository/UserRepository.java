package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleAndDeletedAtIsNull(UserRole role);

    List<User> findByAccountStatusAndDeletedAtIsNull(AccountStatus status);

    /**
     * Tìm kiếm người dùng cho Admin Console: theo từ khoá (họ tên / email / SĐT),
     * vai trò, trạng thái tài khoản và cờ người bán.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:q IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.accountStatus = :status)
              AND (:seller IS NULL OR u.seller = :seller)
            ORDER BY u.createdAt DESC
            """)
    Page<User> search(
            @Param("q") String q,
            @Param("role") UserRole role,
            @Param("status") AccountStatus status,
            @Param("seller") Boolean seller,
            Pageable pageable
    );

    List<User> findBySellerRequestedAtIsNotNullAndDeletedAtIsNullOrderByCreatedAtDesc();

    long countByDeletedAtIsNull();

    long countBySellerAndDeletedAtIsNull(Boolean seller);

    long countByAccountStatusAndDeletedAtIsNull(AccountStatus status);

    long countBySellerRequestedAtIsNotNullAndDeletedAtIsNull();

    long countByRoleAndDeletedAtIsNull(UserRole role);

    long countByAccountStatusReasonAndDeletedAtIsNull(String reason);

    long countBySellerRequestStatusAndDeletedAtIsNull(String status);
}


package com.example.demo.repository;

import com.example.demo.model.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    // Lấy toàn bộ địa chỉ của một người dùng
    List<UserAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    // Tìm địa chỉ theo ID và ID người dùng
    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    // Tìm địa chỉ mặc định của người dùng
    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    // Kiểm tra người dùng đã có địa chỉ nào chưa
    boolean existsByUserId(Long userId);
}

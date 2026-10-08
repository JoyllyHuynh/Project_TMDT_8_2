package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.enums.AccountStatus;
import com.example.demo.model.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleAndDeletedAtIsNull(UserRole role);

    List<User> findByAccountStatusAndDeletedAtIsNull(AccountStatus status);
}

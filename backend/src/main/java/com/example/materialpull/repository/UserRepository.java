package com.example.materialpull.repository;

import com.example.materialpull.entity.UserEntity;
import com.example.materialpull.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
    boolean existsByUsername(String username);
    long countByRoleAndEnabledTrue(UserRole role);
}

package com.example.auth_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.UserContext;

public interface UserContextRepository extends JpaRepository<UserContext, UUID> {

	List<UserContext> findByUserId(UUID userId);

	Optional<UserContext> findByIdAndUserId(UUID id, UUID userId);

}

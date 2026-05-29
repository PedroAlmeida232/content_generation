package com.example.auth_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	Page<Project> findByUserId(UUID userId, Pageable pageable);

	Page<Project> findByUserIdAndStatusIgnoreCase(UUID userId, String status, Pageable pageable);

	java.util.Optional<Project> findByIdAndUserId(UUID id, UUID userId);

}

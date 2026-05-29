package com.example.auth_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	List<Project> findByUserId(UUID userId);

	List<Project> findByUserIdOrderByCreatedAtDesc(UUID userId);

	java.util.Optional<Project> findByIdAndUserId(UUID id, UUID userId);

}

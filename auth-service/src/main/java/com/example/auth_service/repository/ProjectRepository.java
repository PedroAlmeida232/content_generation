package com.example.auth_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	List<Project> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<Project> findByIdAndUserId(UUID id, UUID userId);

}

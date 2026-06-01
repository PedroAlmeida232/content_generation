package com.example.auth_service.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.auth_service.domain.ProjectSlide;

public interface ProjectSlideRepository extends JpaRepository<ProjectSlide, UUID> {

	List<ProjectSlide> findByProjectIdOrderBySlideOrderAsc(UUID projectId);

	java.util.Optional<ProjectSlide> findFirstByProjectIdOrderBySlideOrderAsc(UUID projectId);

	java.util.Optional<ProjectSlide> findByIdAndProjectId(UUID id, UUID projectId);

	void deleteByProjectId(UUID projectId);

}

package com.example.auth_service.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth_service.domain.Project;
import com.example.auth_service.domain.ProjectSlide;
import com.example.auth_service.domain.User;
import com.example.auth_service.dto.CreateProjectRequest;
import com.example.auth_service.dto.ProjectDetailResponse;
import com.example.auth_service.dto.ProjectSlideResponse;
import com.example.auth_service.dto.ProjectSummaryResponse;
import com.example.auth_service.exception.ProjectNotFoundException;
import com.example.auth_service.exception.UserNotFoundException;
import com.example.auth_service.mapper.ProjectMapper;
import com.example.auth_service.repository.ProjectRepository;
import com.example.auth_service.repository.ProjectSlideRepository;
import com.example.auth_service.repository.UserRepository;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectSlideRepository projectSlideRepository;
	private final UserRepository userRepository;
	private final ProjectMapper projectMapper;

	public ProjectService(
		ProjectRepository projectRepository,
		ProjectSlideRepository projectSlideRepository,
		UserRepository userRepository,
		ProjectMapper projectMapper
	) {
		this.projectRepository = projectRepository;
		this.projectSlideRepository = projectSlideRepository;
		this.userRepository = userRepository;
		this.projectMapper = projectMapper;
	}

	public List<ProjectSummaryResponse> getProjects(UUID userId) {
		return projectRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
			.map(projectMapper::toSummaryResponse)
			.toList();
	}

	@Transactional
	public ProjectDetailResponse createProject(UUID userId, CreateProjectRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new UserNotFoundException("User not found"));

		Project project = new Project();
		project.setUser(user);
		project.setName(request.title().trim());
		project.setDescription(normalizeNullable(request.description()));
		project.setStatus(resolveStatus(request.status()));

		Project savedProject = projectRepository.save(project);
		return toDetailResponse(savedProject, List.of());
	}

	public ProjectDetailResponse getProject(UUID userId, UUID projectId) {
		Project project = projectRepository.findByIdAndUserId(projectId, userId)
			.orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

		List<ProjectSlide> slides = projectSlideRepository.findByProjectIdOrderBySlideOrderAsc(projectId);
		return toDetailResponse(project, slides);
	}

	@Transactional
	public void deleteProject(UUID userId, UUID projectId) {
		Project project = projectRepository.findByIdAndUserId(projectId, userId)
			.orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));
		projectRepository.delete(project);
	}

	private ProjectDetailResponse toDetailResponse(Project project, List<ProjectSlide> slides) {
		ProjectSummaryResponse summary = projectMapper.toSummaryResponse(project);
		List<ProjectSlideResponse> slideResponses = slides.stream()
			.map(projectMapper::toSlideResponse)
			.toList();

		return new ProjectDetailResponse(
			summary.id(),
			summary.title(),
			summary.description(),
			summary.status(),
			summary.createdAt(),
			slideResponses
		);
	}

	private String resolveStatus(String status) {
		if (status == null || status.trim().isEmpty()) {
			return "draft";
		}

		return status.trim().toLowerCase(Locale.ROOT);
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

}

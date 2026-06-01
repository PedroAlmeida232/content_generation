package com.example.auth_service.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth_service.domain.Project;
import com.example.auth_service.domain.ProjectSlide;
import com.example.auth_service.domain.User;
import com.example.auth_service.dto.CreateProjectRequest;
import com.example.auth_service.dto.ProjectDetailResponse;
import com.example.auth_service.dto.ProjectPageResponse;
import com.example.auth_service.dto.ProjectSlideResponse;
import com.example.auth_service.dto.ProjectSummaryResponse;
import com.example.auth_service.dto.SaveProjectSlidesRequest;
import com.example.auth_service.dto.SaveProjectSlidesRequest.SaveProjectSlideItemRequest;
import com.example.auth_service.exception.InvalidProjectListRequestException;
import com.example.auth_service.exception.InvalidProjectSlidesException;
import com.example.auth_service.exception.ProjectNotFoundException;
import com.example.auth_service.exception.UserNotFoundException;
import com.example.auth_service.mapper.ProjectMapper;
import com.example.auth_service.repository.ProjectRepository;
import com.example.auth_service.repository.ProjectSlideRepository;
import com.example.auth_service.repository.UserRepository;

@Service
public class ProjectService {

	private static final int MAX_SIZE = 100;
	private static final Set<String> ALLOWED_STATUSES = Set.of(
		"draft",
		"generating",
		"done",
		"failed",
		"processing"
	);

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

	@Transactional(readOnly = true)
	public ProjectPageResponse getProjects(UUID userId, int page, int size, String status) {
		int normalizedPage = normalizePage(page);
		int normalizedSize = normalizeSize(size);
		String normalizedStatus = normalizeStatus(status);

		Pageable pageable = PageRequest.of(
			normalizedPage,
			normalizedSize,
			Sort.by(Sort.Direction.DESC, "createdAt")
		);

		Page<Project> projects = normalizedStatus == null
			? projectRepository.findByUserId(userId, pageable)
			: projectRepository.findByUserIdAndStatusIgnoreCase(userId, normalizedStatus, pageable);

		List<ProjectSummaryResponse> content = projects.getContent().stream()
			.map(project -> {
				ProjectSummaryResponse base = projectMapper.toSummaryResponse(project);
				String firstSlideImageUrl = projectSlideRepository
					.findFirstByProjectIdOrderBySlideOrderAsc(project.getId())
					.map(ProjectSlide::getImageUrl)
					.orElse(null);
				return new ProjectSummaryResponse(
					base.id(),
					base.title(),
					base.description(),
					base.status(),
					base.createdAt(),
					firstSlideImageUrl
				);
			})
			.toList();

		return new ProjectPageResponse(
			content,
			projects.getNumber(),
			projects.getSize(),
			projects.getTotalElements(),
			projects.getTotalPages(),
			projects.hasNext()
		);
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

	@Transactional
	public ProjectDetailResponse saveProjectSlides(
		UUID userId,
		UUID projectId,
		SaveProjectSlidesRequest request
	) {
		Project project = projectRepository.findByIdAndUserId(projectId, userId)
			.orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

		List<SaveProjectSlideItemRequest> slides = normalizeAndValidateSlides(request.slides());

		projectSlideRepository.deleteByProjectId(projectId);

		List<ProjectSlide> entities = slides.stream()
			.map(slide -> createSlideEntity(project, slide))
			.toList();

		List<ProjectSlide> savedSlides = projectSlideRepository.saveAll(entities);
		project.setStatus("done");
		Project savedProject = projectRepository.save(project);

		return toDetailResponse(savedProject, savedSlides);
	}

	private ProjectDetailResponse toDetailResponse(Project project, List<ProjectSlide> slides) {
		ProjectSummaryResponse summary = projectMapper.toSummaryResponse(project);
		List<ProjectSlideResponse> slideResponses = slides.stream()
			.sorted(Comparator.comparingInt(ProjectSlide::getSlideOrder))
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

	private int normalizePage(int page) {
		if (page < 0) {
			throw new InvalidProjectListRequestException("Page must be greater than or equal to 0");
		}

		return page;
	}

	private int normalizeSize(int size) {
		if (size < 1) {
			throw new InvalidProjectListRequestException("Size must be greater than or equal to 1");
		}

		if (size > MAX_SIZE) {
			throw new InvalidProjectListRequestException("Size must not exceed " + MAX_SIZE);
		}

		return size;
	}

	private String normalizeStatus(String status) {
		if (status == null || status.trim().isEmpty()) {
			return null;
		}

		String normalized = status.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_STATUSES.contains(normalized)) {
			throw new InvalidProjectListRequestException("Invalid project status: " + status);
		}

		return normalized;
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}

		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private List<SaveProjectSlideItemRequest> normalizeAndValidateSlides(
		List<SaveProjectSlideItemRequest> slides
	) {
		List<SaveProjectSlideItemRequest> orderedSlides = slides.stream()
			.sorted((left, right) -> Integer.compare(left.slideOrder(), right.slideOrder()))
			.toList();

		for (int index = 0; index < orderedSlides.size(); index++) {
			int expectedOrder = index + 1;
			int actualOrder = orderedSlides.get(index).slideOrder();
			if (actualOrder != expectedOrder) {
				throw new InvalidProjectSlidesException(
					"Slide order must be sequential starting at 1"
				);
			}
		}

		return orderedSlides;
	}

	private ProjectSlide createSlideEntity(
		Project project,
		SaveProjectSlideItemRequest slide
	) {
		ProjectSlide entity = new ProjectSlide();
		entity.setProject(project);
		entity.setSlideOrder(slide.slideOrder());
		entity.setImageUrl(slide.imageUrl().trim());
		entity.setCaption(slide.caption().trim());
		entity.setPromptUsed(slide.promptUsed().trim());
		return entity;
	}

}

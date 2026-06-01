package com.example.auth_service.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import com.example.auth_service.dto.ProjectDownloadFileResponse;
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
	public ProjectPageResponse getProjects(UUID userId, int page, int size, String status, String search) {
		int normalizedPage = normalizePage(page);
		int normalizedSize = normalizeSize(size);
		String normalizedStatus = normalizeStatus(status);
		String normalizedSearch = normalizeSearch(search);

		Pageable pageable = PageRequest.of(
			normalizedPage,
			normalizedSize,
			Sort.by(Sort.Direction.DESC, "createdAt")
		);

		Page<Project> projects = projectRepository.findByUserIdAndStatusAndSearch(
			userId,
			normalizedStatus,
			normalizedSearch,
			pageable
		);

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

	@Transactional(readOnly = true)
	public ProjectDownloadFileResponse downloadProjectSlide(UUID userId, UUID projectId, UUID slideId) {
		Project project = projectRepository.findByIdAndUserId(projectId, userId)
			.orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

		ProjectSlide slide = projectSlideRepository.findByIdAndProjectId(slideId, projectId)
			.orElseThrow(() -> new ProjectNotFoundException("Slide not found with id: " + slideId));

		DownloadedAsset asset = downloadAsset(
			slide.getImageUrl(),
			buildSlideBaseName(project.getName(), slide.getSlideOrder())
		);

		return new ProjectDownloadFileResponse(
			asset.content(),
			asset.contentType(),
			asset.filename()
		);
	}

	@Transactional(readOnly = true)
	public ProjectDownloadFileResponse downloadProjectZip(UUID userId, UUID projectId) {
		Project project = projectRepository.findByIdAndUserId(projectId, userId)
			.orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + projectId));

		List<ProjectSlide> slides = projectSlideRepository.findByProjectIdOrderBySlideOrderAsc(projectId);

		byte[] zipBytes = buildProjectZip(project, slides);
		String filename = buildProjectBaseName(project.getName()) + ".zip";
		return new ProjectDownloadFileResponse(zipBytes, "application/zip", filename);
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

	private String normalizeSearch(String search) {
		if (search == null) {
			return null;
		}

		String normalized = search.trim();
		return normalized.isEmpty() ? null : normalized;
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

	private byte[] buildProjectZip(Project project, List<ProjectSlide> slides) {
		try (ByteArrayOutputStream output = new ByteArrayOutputStream();
			ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
			String projectFolder = buildProjectBaseName(project.getName());

			String metadata = buildProjectMetadata(project, slides);
			addZipEntry(zip, projectFolder + "/project-info.txt", metadata.getBytes(StandardCharsets.UTF_8));

			for (ProjectSlide slide : slides) {
				DownloadedAsset asset = downloadAsset(
					slide.getImageUrl(),
					buildSlideBaseName(project.getName(), slide.getSlideOrder())
				);
				String entryName = projectFolder + "/" + asset.filename();
				addZipEntry(zip, entryName, asset.content());
			}

			zip.finish();
			return output.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to build project ZIP", e);
		}
	}

	private void addZipEntry(ZipOutputStream zip, String entryName, byte[] content) throws IOException {
		ZipEntry entry = new ZipEntry(entryName);
		zip.putNextEntry(entry);
		zip.write(content);
		zip.closeEntry();
	}

	private String buildProjectMetadata(Project project, List<ProjectSlide> slides) {
		StringBuilder builder = new StringBuilder();
		builder.append("Projeto: ").append(project.getName()).append('\n');
		builder.append("Status: ").append(project.getStatus()).append('\n');
		builder.append("Criado em: ").append(project.getCreatedAt()).append('\n');
		builder.append("Descricao: ").append(safeText(project.getDescription())).append('\n');
		builder.append("Slides: ").append(slides.size()).append('\n');
		builder.append('\n');
		for (ProjectSlide slide : slides) {
			builder.append("Slide ").append(slide.getSlideOrder()).append('\n');
			builder.append("Caption: ").append(safeText(slide.getCaption())).append('\n');
			builder.append("Prompt: ").append(safeText(slide.getPromptUsed())).append('\n');
			builder.append("Image URL: ").append(safeText(slide.getImageUrl())).append('\n');
			builder.append('\n');
		}
		return builder.toString();
	}

	private DownloadedAsset downloadAsset(String sourceUrl, String fallbackBaseName) {
		if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
			throw new IllegalStateException("Slide image URL is missing");
		}

		try {
			URL url = new URL(sourceUrl);
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(10000);
			connection.setReadTimeout(15000);

			byte[] content;
			try (InputStream inputStream = connection.getInputStream()) {
				content = inputStream.readAllBytes();
			}

			String contentType = connection.getContentType();
			String extension = resolveFileExtension(sourceUrl, contentType);
			return new DownloadedAsset(
				content,
				contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
				fallbackBaseName + extension
			);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to download slide asset from " + sourceUrl, e);
		}
	}

	private String resolveFileExtension(String sourceUrl, String contentType) {
		String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
		if (normalizedContentType.contains("png")) return ".png";
		if (normalizedContentType.contains("jpeg") || normalizedContentType.contains("jpg")) return ".jpg";
		if (normalizedContentType.contains("webp")) return ".webp";
		if (normalizedContentType.contains("gif")) return ".gif";

		try {
			String path = new URL(sourceUrl).getPath();
			int lastSlash = path.lastIndexOf('/');
			String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
			int dotIndex = fileName.lastIndexOf('.');
			if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
				return fileName.substring(dotIndex);
			}
		} catch (IOException ignored) {
			// Fall through to the default extension below.
		}

		return ".bin";
	}

	private String buildSlideBaseName(String projectName, int slideOrder) {
		return buildProjectBaseName(projectName) + "-slide-" + String.format("%02d", slideOrder);
	}

	private String buildProjectBaseName(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "project";
		}

		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("^-+|-+$", "");
		return normalized.isBlank() ? "project" : normalized;
	}

	private String safeText(String value) {
		return value == null ? "" : value;
	}

	private record DownloadedAsset(byte[] content, String contentType, String filename) {
	}

}

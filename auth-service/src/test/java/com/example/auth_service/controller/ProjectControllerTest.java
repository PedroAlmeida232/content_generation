package com.example.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.auth_service.domain.Project;
import com.example.auth_service.domain.ProjectSlide;
import com.example.auth_service.domain.User;
import com.example.auth_service.repository.ProjectRepository;
import com.example.auth_service.repository.ProjectSlideRepository;
import com.example.auth_service.repository.UserContextRepository;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.service.JwtService;

@SpringBootTest(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
	"JWT_SECRET=12345678901234567890123456789012",
	"JWT_EXPIRATION_MS=86400000"
})
@AutoConfigureMockMvc
class ProjectControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private UserContextRepository userContextRepository;

	@MockitoBean
	private ProjectRepository projectRepository;

	@MockitoBean
	private ProjectSlideRepository projectSlideRepository;

	@Test
	void projectsEndpointsRejectUnauthorizedRequests() throws Exception {
		mockMvc.perform(get("/projects"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/projects")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/projects/" + UUID.randomUUID()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/projects/" + UUID.randomUUID() + "/slides")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/projects/" + UUID.randomUUID()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void listProjectsReturnsProjectsForAuthenticatedUser() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project first = project(user, "First project", "First description", "draft");
		first.setId(UUID.randomUUID());
		first.setCreatedAt(LocalDateTime.of(2026, 5, 29, 10, 0));

		Project second = project(user, "Second project", "Second description", "done");
		second.setId(UUID.randomUUID());
		second.setCreatedAt(LocalDateTime.of(2026, 5, 29, 11, 0));

		when(projectRepository.findByUserIdAndStatusAndSearch(eq(userId), isNull(), isNull(), any(Pageable.class)))
			.thenReturn(new PageImpl<>(
				List.of(second, first),
				PageRequest.of(0, 10),
				2
			));

		mockMvc.perform(get("/projects")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content[0].title").value("Second project"))
			.andExpect(jsonPath("$.content[0].status").value("done"))
			.andExpect(jsonPath("$.content[1].title").value("First project"))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(10))
			.andExpect(jsonPath("$.totalElements").value(2))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	void listProjectsFiltersByStatusWhenProvided() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project doneProject = project(user, "Done project", "Done description", "done");
		doneProject.setId(UUID.randomUUID());
		doneProject.setCreatedAt(LocalDateTime.of(2026, 5, 29, 11, 0));

		when(projectRepository.findByUserIdAndStatusAndSearch(eq(userId), eq("done"), isNull(), any(Pageable.class)))
			.thenReturn(new PageImpl<>(
				List.of(doneProject),
				PageRequest.of(0, 10),
				1
			));

		mockMvc.perform(get("/projects?status=done&page=0&size=10")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Done project"))
			.andExpect(jsonPath("$.content[0].status").value("done"));
	}

	@Test
	void listProjectsSearchesByTextWhenProvided() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project matchingProject = project(user, "Launch campaign", "Landing page and email sequence", "draft");
		matchingProject.setId(UUID.randomUUID());
		matchingProject.setCreatedAt(LocalDateTime.of(2026, 5, 29, 12, 30));

		when(projectRepository.findByUserIdAndStatusAndSearch(
			eq(userId),
			isNull(),
			eq("launch"),
			any(Pageable.class)
		)).thenReturn(new PageImpl<>(
			List.of(matchingProject),
			PageRequest.of(0, 10),
			1
		));

		mockMvc.perform(get("/projects?q=launch&page=0&size=10")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].title").value("Launch campaign"));
	}

	@Test
	void listProjectsCombinesSearchAndStatusFilters() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project matchingProject = project(user, "Launch campaign", "Landing page and email sequence", "done");
		matchingProject.setId(UUID.randomUUID());
		matchingProject.setCreatedAt(LocalDateTime.of(2026, 5, 29, 12, 30));

		when(projectRepository.findByUserIdAndStatusAndSearch(
			eq(userId),
			eq("done"),
			eq("launch"),
			any(Pageable.class)
		)).thenReturn(new PageImpl<>(
			List.of(matchingProject),
			PageRequest.of(0, 10),
			1
		));

		mockMvc.perform(get("/projects?status=done&q=launch&page=0&size=10")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.content.length()").value(1))
			.andExpect(jsonPath("$.content[0].status").value("done"))
			.andExpect(jsonPath("$.content[0].title").value("Launch campaign"));
	}

	@Test
	void listProjectsRejectsInvalidStatusFilter() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(get("/projects?status=archived")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid project status: archived"));
	}

	@Test
	void listProjectsRejectsInvalidPageType() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(get("/projects?page=abc")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Invalid value for request parameter: page"))
			.andExpect(jsonPath("$.errors").isEmpty());
	}

	@Test
	void createProjectPersistsAndReturnsDetail() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
			Project project = invocation.getArgument(0);
			project.setId(projectId);
			project.setCreatedAt(LocalDateTime.of(2026, 5, 29, 12, 0));
			return project;
		});

		mockMvc.perform(post("/projects")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "title": "Campanha de maio",
				  "description": "Projeto principal do mês"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(projectId.toString()))
			.andExpect(jsonPath("$.title").value("Campanha de maio"))
			.andExpect(jsonPath("$.description").value("Projeto principal do mês"))
			.andExpect(jsonPath("$.status").value("draft"))
			.andExpect(jsonPath("$.slides").isArray())
			.andExpect(jsonPath("$.slides.length()").value(0));
	}

	@Test
	void createProjectRejectsInvalidPayload() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(post("/projects")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "title": "",
				  "description": "Projeto sem titulo"
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.errors.title").value("Project title is required"));
	}

	@Test
	void getProjectReturnsDetailWithOrderedSlides() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project with slides", "Description", "done");
		project.setId(projectId);
		project.setCreatedAt(LocalDateTime.of(2026, 5, 29, 13, 0));

		ProjectSlide slideOne = slide(project, 1, "https://cdn.example/1.png", "Slide 1", "Prompt 1");
		ProjectSlide slideTwo = slide(project, 2, "https://cdn.example/2.png", "Slide 2", "Prompt 2");

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
		when(projectSlideRepository.findByProjectIdOrderBySlideOrderAsc(projectId))
			.thenReturn(List.of(slideOne, slideTwo));

		mockMvc.perform(get("/projects/" + projectId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.title").value("Project with slides"))
			.andExpect(jsonPath("$.slides[0].slideOrder").value(1))
			.andExpect(jsonPath("$.slides[0].imageUrl").value("https://cdn.example/1.png"))
			.andExpect(jsonPath("$.slides[1].slideOrder").value(2));
	}

	@Test
	void getProjectReturnsNotFoundForAnotherUsersProject() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User otherUser = new User();
		otherUser.setId(otherUserId);

		Project project = project(otherUser, "Foreign project", "Description", "draft");
		project.setId(projectId);

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/projects/" + projectId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found with id: " + projectId));
	}

	@Test
	void downloadProjectSlideReturnsOwnedImageFile() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		UUID slideId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		Path tempImage = Files.createTempFile("project-slide-", ".png");
		byte[] imageBytes = new byte[] { 1, 2, 3, 4, 5 };
		Files.write(tempImage, imageBytes);

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project to download", "Description", "done");
		project.setId(projectId);

		ProjectSlide slide = slide(project, 1, tempImage.toUri().toString(), "Slide 1", "Prompt 1");
		slide.setId(slideId);

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
		when(projectSlideRepository.findByIdAndProjectId(slideId, projectId)).thenReturn(Optional.of(slide));

		byte[] responseBytes = mockMvc.perform(get("/projects/" + projectId + "/slides/" + slideId + "/download")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		assertArrayEquals(imageBytes, responseBytes);
	}

	@Test
	void downloadProjectZipReturnsArchiveWithMetadataAndSlides() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		Path slideOneFile = Files.createTempFile("slide-one-", ".png");
		Path slideTwoFile = Files.createTempFile("slide-two-", ".jpg");
		byte[] slideOneBytes = new byte[] { 10, 20, 30 };
		byte[] slideTwoBytes = new byte[] { 40, 50, 60, 70 };
		Files.write(slideOneFile, slideOneBytes);
		Files.write(slideTwoFile, slideTwoBytes);

		User user = new User();
		user.setId(userId);

		Project project = project(user, "ZIP Project", "Project description", "done");
		project.setId(projectId);

		ProjectSlide slideOne = slide(project, 1, slideOneFile.toUri().toString(), "Caption 1", "Prompt 1");
		slideOne.setId(UUID.randomUUID());
		ProjectSlide slideTwo = slide(project, 2, slideTwoFile.toUri().toString(), "Caption 2", "Prompt 2");
		slideTwo.setId(UUID.randomUUID());

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
		when(projectSlideRepository.findByProjectIdOrderBySlideOrderAsc(projectId))
			.thenReturn(List.of(slideOne, slideTwo));

		byte[] responseBytes = mockMvc.perform(get("/projects/" + projectId + "/download")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("application/zip")))
			.andReturn()
			.getResponse()
			.getContentAsByteArray();

		boolean foundMetadata = false;
		boolean foundSlideOne = false;
		boolean foundSlideTwo = false;

		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(responseBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				byte[] data = zip.readAllBytes();
				if (entry.getName().endsWith("project-info.txt")) {
					foundMetadata = new String(data).contains("ZIP Project");
				}
				if (entry.getName().endsWith("slide-01.png")) {
					foundSlideOne = java.util.Arrays.equals(slideOneBytes, data);
				}
				if (entry.getName().endsWith("slide-02.jpg")) {
					foundSlideTwo = java.util.Arrays.equals(slideTwoBytes, data);
				}
			}
		}

		assertTrue(foundMetadata);
		assertTrue(foundSlideOne);
		assertTrue(foundSlideTwo);
	}

	@Test
	void downloadProjectSlideReturnsNotFoundForAnotherProjectsSlide() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		UUID otherProjectId = UUID.randomUUID();
		UUID slideId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project", "Description", "done");
		project.setId(projectId);

		Project otherProject = project(user, "Other Project", "Description", "done");
		otherProject.setId(otherProjectId);

		ProjectSlide slide = slide(otherProject, 1, "https://cdn.example/1.png", "Slide 1", "Prompt 1");
		slide.setId(slideId);

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
		when(projectSlideRepository.findByIdAndProjectId(slideId, projectId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/projects/" + projectId + "/slides/" + slideId + "/download")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Slide not found with id: " + slideId));
	}

	@Test
	void saveProjectSlidesOverwritesAndReturnsUpdatedProject() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		UUID slideOneId = UUID.randomUUID();
		UUID slideTwoId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project with slides", "Description", "processing");
		project.setId(projectId);
		project.setCreatedAt(LocalDateTime.of(2026, 5, 29, 13, 0));

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));
		when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(projectSlideRepository.saveAll(any())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			List<ProjectSlide> slides = (List<ProjectSlide>) invocation.getArgument(0);
			slides.get(0).setId(slideOneId);
			slides.get(0).setGeneratedAt(LocalDateTime.of(2026, 5, 29, 14, 1));
			slides.get(1).setId(slideTwoId);
			slides.get(1).setGeneratedAt(LocalDateTime.of(2026, 5, 29, 14, 2));
			return slides;
		});

		mockMvc.perform(post("/projects/" + projectId + "/slides")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "slides": [
				    {
				      "slide_order": 2,
				      "image_url": "https://cdn.example/2.png",
				      "caption": "Slide 2",
				      "prompt_used": "Prompt 2"
				    },
				    {
				      "slide_order": 1,
				      "image_url": "https://cdn.example/1.png",
				      "caption": "Slide 1",
				      "prompt_used": "Prompt 1"
				    }
				  ]
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("done"))
			.andExpect(jsonPath("$.slides[0].slideOrder").value(1))
			.andExpect(jsonPath("$.slides[0].imageUrl").value("https://cdn.example/1.png"))
			.andExpect(jsonPath("$.slides[1].slideOrder").value(2))
			.andExpect(jsonPath("$.slides[1].imageUrl").value("https://cdn.example/2.png"));

		verify(projectSlideRepository).deleteByProjectId(projectId);
	}

	@Test
	void saveProjectSlidesRejectsInvalidOrderSequence() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project with slides", "Description", "processing");
		project.setId(projectId);

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));

		mockMvc.perform(post("/projects/" + projectId + "/slides")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "slides": [
				    {
				      "slide_order": 1,
				      "image_url": "https://cdn.example/1.png",
				      "caption": "Slide 1",
				      "prompt_used": "Prompt 1"
				    },
				    {
				      "slide_order": 3,
				      "image_url": "https://cdn.example/3.png",
				      "caption": "Slide 3",
				      "prompt_used": "Prompt 3"
				    }
				  ]
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Slide order must be sequential starting at 1"));
	}

	@Test
	void saveProjectSlidesRejectsEmptySlidesList() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project with slides", "Description", "processing");
		project.setId(projectId);

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));

		mockMvc.perform(post("/projects/" + projectId + "/slides")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "slides": []
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.errors.slides").exists());
	}

	@Test
	void deleteProjectRemovesOwnedProject() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		Project project = project(user, "Project to delete", "Description", "draft");
		project.setId(projectId);

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.of(project));

		mockMvc.perform(delete("/projects/" + projectId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		verify(projectRepository).delete(project);
		verifyNoInteractions(projectSlideRepository);
	}

	@Test
	void deleteProjectReturnsNotFoundForAnotherUsersProject() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID projectId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		when(projectRepository.findByIdAndUserId(projectId, userId)).thenReturn(Optional.empty());

		mockMvc.perform(delete("/projects/" + projectId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found with id: " + projectId));

		verifyNoInteractions(projectSlideRepository);
	}

	private static Project project(User user, String title, String description, String status) {
		Project project = new Project();
		project.setUser(user);
		project.setName(title);
		project.setDescription(description);
		project.setStatus(status);
		return project;
	}

	private static ProjectSlide slide(
		Project project,
		int order,
		String imageUrl,
		String caption,
		String promptUsed
	) {
		ProjectSlide slide = new ProjectSlide();
		slide.setProject(project);
		slide.setSlideOrder(order);
		slide.setImageUrl(imageUrl);
		slide.setCaption(caption);
		slide.setPromptUsed(promptUsed);
		slide.setGeneratedAt(LocalDateTime.of(2026, 5, 29, 14, 0).plusMinutes(order));
		return slide;
	}

}

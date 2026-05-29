package com.example.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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

		when(projectRepository.findByUserIdOrderByCreatedAtDesc(userId))
			.thenReturn(List.of(second, first));

		mockMvc.perform(get("/projects")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$[0].title").value("Second project"))
			.andExpect(jsonPath("$[0].status").value("done"))
			.andExpect(jsonPath("$[1].title").value("First project"));
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
			.andExpect(jsonPath("$.errors.title").exists());
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

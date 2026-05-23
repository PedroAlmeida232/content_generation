package com.example.auth_service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.auth_service.domain.User;
import com.example.auth_service.domain.UserContext;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.repository.UserContextRepository;
import com.example.auth_service.service.JwtService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
	"JWT_SECRET=12345678901234567890123456789012",
	"JWT_EXPIRATION_MS=86400000"
})
@AutoConfigureMockMvc

class AuthServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private UserRepository userRepository;

	@MockitoBean
	private UserContextRepository userContextRepository;

	@Test
	void healthEndpointReturnsOkWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void usersEndpointRejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void usersEndpointAllowsRequestWithValidToken() throws Exception {
		UUID userId = UUID.randomUUID();
		String email = "user@example.com";
		User user = new User();
		user.setId(userId);
		user.setEmail(email);
		// mock repository to return the user
		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		String token = jwtService.generateToken(userId, email);

		mockMvc.perform(get("/users/me")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.email").value(email));
	}

	@Test
	void protectedEndpointRejectsRequestWithInvalidToken() throws Exception {
		mockMvc.perform(get("/users/me")
			.header("Authorization", "Bearer invalid-token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void updateUserEndpointRejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(put("/users/me")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "updated@example.com",
				  "name": "Updated Name"
				}
				"""))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void updateUserEndpointAllowsRequestWithValidToken() throws Exception {
		UUID userId = UUID.randomUUID();
		String originalEmail = "user@example.com";
		User user = new User();
		user.setId(userId);
		user.setEmail(originalEmail);
		user.setName("Original Name");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmail("updated@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		String token = jwtService.generateToken(userId, originalEmail);

		mockMvc.perform(put("/users/me")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "updated@example.com",
				  "name": "Updated Name"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.email").value("updated@example.com"))
			.andExpect(jsonPath("$.name").value("Updated Name"));
	}

	@Test
	void updateUserEndpointRejectsInvalidPayload() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(put("/users/me")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "not-an-email",
				  "name": ""
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void updateUserEndpointRejectsDuplicateEmail() throws Exception {
		UUID userId = UUID.randomUUID();
		String originalEmail = "user@example.com";
		User user = new User();
		user.setId(userId);
		user.setEmail(originalEmail);
		user.setName("Original Name");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

		String token = jwtService.generateToken(userId, originalEmail);

		mockMvc.perform(put("/users/me")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "existing@example.com",
				  "name": "Updated Name"
				}
				"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Email already in use: existing@example.com"));
	}

	@Test
	void contextsEndpointsRejectUnauthorizedRequest() throws Exception {
		mockMvc.perform(get("/contexts"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/contexts"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/contexts/" + UUID.randomUUID()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(put("/contexts/" + UUID.randomUUID()))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/contexts/" + UUID.randomUUID()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void getContextsReturnsUserContextsList() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		UserContext ctx = new UserContext();
		ctx.setId(UUID.randomUUID());
		ctx.setUser(user);
		ctx.setContextKey("theme");
		ctx.setContextValue("dark");
		ctx.setCreatedAt(LocalDateTime.now());
		ctx.setUpdatedAt(LocalDateTime.now());

		when(userContextRepository.findByUserId(userId)).thenReturn(List.of(ctx));

		mockMvc.perform(get("/contexts")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].contextKey").value("theme"))
			.andExpect(jsonPath("$[0].contextValue").value("dark"));
	}

	@Test
	void getContextByIdReturnsContextForValidOwner() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		UserContext ctx = new UserContext();
		ctx.setId(contextId);
		ctx.setUser(user);
		ctx.setContextKey("theme");
		ctx.setContextValue("dark");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

		mockMvc.perform(get("/contexts/" + contextId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(contextId.toString()))
			.andExpect(jsonPath("$.contextKey").value("theme"));
	}

	@Test
	void getContextByIdReturnsNotFoundForAnotherUsersContext() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User otherUser = new User();
		otherUser.setId(otherUserId);

		UserContext ctx = new UserContext();
		ctx.setId(contextId);
		ctx.setUser(otherUser);
		ctx.setContextKey("theme");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

		mockMvc.perform(get("/contexts/" + contextId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Context not found with id: " + contextId));
	}

	@Test
	void createContextSavesAndReturnsCreatedContext() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userContextRepository.findByUserIdAndContextKey(userId, "theme")).thenReturn(Optional.empty());
		when(userContextRepository.save(any(UserContext.class))).thenAnswer(inv -> {
			UserContext c = inv.getArgument(0);
			c.setId(UUID.randomUUID());
			return c;
		});

		mockMvc.perform(post("/contexts")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "contextKey": "theme",
				  "contextValue": "dark"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.contextKey").value("theme"))
			.andExpect(jsonPath("$.contextValue").value("dark"));
	}

	@Test
	void createContextRejectsDuplicateKeyWithConflict() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		UserContext ctx = new UserContext();
		ctx.setId(UUID.randomUUID());
		ctx.setUser(user);
		ctx.setContextKey("theme");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userContextRepository.findByUserIdAndContextKey(userId, "theme")).thenReturn(Optional.of(ctx));

		mockMvc.perform(post("/contexts")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "contextKey": "theme",
				  "contextValue": "light"
				}
				"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Context key already exists: theme"));
	}

	@Test
	void createContextRejectsInvalidPayloadWithBadRequest() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(post("/contexts")
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "contextKey": "",
				  "contextValue": "dark"
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.errors.contextKey").exists());
	}

	@Test
	void updateContextUpdatesSuccessfully() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		UserContext ctx = new UserContext();
		ctx.setId(contextId);
		ctx.setUser(user);
		ctx.setContextKey("theme");
		ctx.setContextValue("dark");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(ctx));
		when(userContextRepository.save(any(UserContext.class))).thenAnswer(inv -> inv.getArgument(0));

		mockMvc.perform(put("/contexts/" + contextId)
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "contextKey": "theme",
				  "contextValue": "light"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contextKey").value("theme"))
			.andExpect(jsonPath("$.contextValue").value("light"));
	}

	@Test
	void updateContextRejectsKeyChangeToExistingKey() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		UserContext ctx = new UserContext();
		ctx.setId(contextId);
		ctx.setUser(user);
		ctx.setContextKey("theme");

		UserContext anotherCtx = new UserContext();
		anotherCtx.setId(UUID.randomUUID());
		anotherCtx.setUser(user);
		anotherCtx.setContextKey("fontSize");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(ctx));
		when(userContextRepository.findByUserIdAndContextKey(userId, "fontSize")).thenReturn(Optional.of(anotherCtx));

		mockMvc.perform(put("/contexts/" + contextId)
			.header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "contextKey": "fontSize",
				  "contextValue": "14px"
				}
				"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Context key already exists: fontSize"));
	}

	@Test
	void deleteContextRemovesSuccessfully() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User user = new User();
		user.setId(userId);

		UserContext ctx = new UserContext();
		ctx.setId(contextId);
		ctx.setUser(user);
		ctx.setContextKey("theme");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

		mockMvc.perform(delete("/contexts/" + contextId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());
	}

	@Test
	void deleteContextReturnsNotFoundForAnotherUsersContext() throws Exception {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		User otherUser = new User();
		otherUser.setId(otherUserId);

		UserContext ctx = new UserContext();
		ctx.setId(contextId);
		ctx.setUser(otherUser);
		ctx.setContextKey("theme");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(ctx));

		mockMvc.perform(delete("/contexts/" + contextId)
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Context not found with id: " + contextId));
	}

}

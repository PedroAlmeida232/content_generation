package com.example.auth_service;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.domain.User;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;
import com.example.auth_service.service.JwtService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
	"JWT_SECRET=12345678901234567890123456789012",
	"JWT_EXPIRATION_MS=86400000"
})
@AutoConfigureMockMvc
@Import(AuthServiceApplicationTests.ProtectedTestController.class)
class AuthServiceApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void healthEndpointReturnsOkWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void loginEndpointIsPublic() throws Exception {
		mockMvc.perform(post("/auth/login"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("public"));
	}

	@Test
	void registerEndpointIsPublic() throws Exception {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});

		mockMvc.perform(post("/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "user@example.com",
				  "password": "plain-text-password",
				  "name": "Pedro"
				}
				"""))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.email").value("user@example.com"))
			.andExpect(jsonPath("$.name").value("Pedro"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerEndpointRejectsDuplicateEmail() throws Exception {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		mockMvc.perform(post("/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "user@example.com",
				  "password": "plain-text-password",
				  "name": "Pedro"
				}
				"""))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Email already in use: user@example.com"));
	}

	@Test
	void registerEndpointRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "not-an-email",
				  "password": "123",
				  "name": ""
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists())
			.andExpect(jsonPath("$.errors.name").exists());
	}

	@Test
	void refreshEndpointRejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(post("/auth/refresh"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshEndpointAllowsRequestWithValidToken() throws Exception {
		String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com");

		mockMvc.perform(post("/auth/refresh")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.email").value("user@example.com"));
	}

	@Test
	void usersEndpointRejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(get("/users/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void usersEndpointAllowsRequestWithValidToken() throws Exception {
		String token = jwtService.generateToken(UUID.randomUUID(), "user@example.com");

		mockMvc.perform(get("/users/me")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.email").value("user@example.com"));
	}

	@Test
	void protectedEndpointRejectsRequestWithInvalidToken() throws Exception {
		mockMvc.perform(get("/users/me")
			.header("Authorization", "Bearer invalid-token"))
			.andExpect(status().isUnauthorized());
	}

	@RestController
	static class ProtectedTestController {

		@PostMapping("/auth/login")
		ResponseEntity<Map<String, String>> login() {
			return ResponseEntity.ok(Map.of("status", "public"));
		}

		@PostMapping("/auth/refresh")
		ResponseEntity<Map<String, String>> refresh(org.springframework.security.core.Authentication authentication) {
			JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
			return ResponseEntity.ok(Map.of(
				"email", principal.email(),
				"userId", principal.userId().toString()
			));
		}

		@GetMapping("/users/me")
		ResponseEntity<Map<String, String>> usersMe(org.springframework.security.core.Authentication authentication) {
			JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
			return ResponseEntity.ok(Map.of(
				"email", principal.email(),
				"userId", principal.userId().toString()
			));
		}

	}

}

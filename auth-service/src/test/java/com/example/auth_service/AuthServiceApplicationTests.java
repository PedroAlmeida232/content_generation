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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;
import com.example.auth_service.service.JwtService;

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
		mockMvc.perform(post("/auth/register"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("public"));
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

		@PostMapping("/auth/register")
		ResponseEntity<Map<String, String>> register() {
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

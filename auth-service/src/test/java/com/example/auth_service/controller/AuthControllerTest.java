package com.example.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.auth_service.domain.User;
import com.example.auth_service.repository.UserContextRepository;
import com.example.auth_service.repository.ProjectRepository;
import com.example.auth_service.repository.ProjectSlideRepository;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.service.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@SpringBootTest(properties = {
	"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
	"JWT_SECRET=12345678901234567890123456789012",
	"JWT_EXPIRATION_MS=86400000"
})
@AutoConfigureMockMvc
class AuthControllerTest {

	private static final String JWT_SECRET = "12345678901234567890123456789012";
	private static final long EXPIRATION_MS = 86_400_000L;

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
	void registerReturnsCreatedWithUserId() throws Exception {
		UUID userId = UUID.randomUUID();

		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(userId);
			return user;
		});

		mockMvc.perform(post("/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content(registerPayload("user@example.com", "plain-text-password", "Pedro")))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("user@example.com"))
			.andExpect(jsonPath("$.name").value("Pedro"))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerNormalizesEmailCase() throws Exception {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			user.setId(UUID.randomUUID());
			return user;
		});

		mockMvc.perform(post("/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content(registerPayload("USER@example.com", "plain-text-password", "Pedro")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("user@example.com"));

		verify(userRepository).existsByEmail("user@example.com");
	}

	@Test
	void registerRejectsDuplicateEmail() throws Exception {
		when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

		mockMvc.perform(post("/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content(registerPayload("user@example.com", "plain-text-password", "Pedro")))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Email already in use: user@example.com"));
	}

	@Test
	void registerRejectsInvalidPayload() throws Exception {
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
	void loginReturnsOkWithToken() throws Exception {
		User user = userWithEncodedPassword("user@example.com", "plain-text-password");

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		mockMvc.perform(post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginPayload("user@example.com", "plain-text-password")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.token").isString())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(EXPIRATION_MS))
			.andExpect(jsonPath("$.userId").value(user.getId().toString()))
			.andExpect(jsonPath("$.email").value("user@example.com"));
	}

	@Test
	void loginRejectsUnknownEmail() throws Exception {
		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

		mockMvc.perform(post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginPayload("user@example.com", "plain-text-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void loginRejectsWrongPassword() throws Exception {
		User user = userWithEncodedPassword("user@example.com", "different-password");

		when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

		mockMvc.perform(post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(loginPayload("user@example.com", "plain-text-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void loginRejectsInvalidPayload() throws Exception {
		mockMvc.perform(post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{
				  "email": "not-an-email",
				  "password": ""
				}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.errors.email").exists())
			.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void loginRejectsMalformedJsonBody() throws Exception {
		mockMvc.perform(post("/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ invalid-json }"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void refreshReturnsOkWithNewToken() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(post("/auth/refresh")
			.header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.token").isString())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(EXPIRATION_MS))
			.andExpect(jsonPath("$.userId").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("user@example.com"));
	}

	@Test
	void refreshRejectsRequestWithoutToken() throws Exception {
		mockMvc.perform(post("/auth/refresh"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRejectsInvalidToken() throws Exception {
		mockMvc.perform(post("/auth/refresh")
			.header("Authorization", "Bearer invalid.jwt.token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRejectsExpiredToken() throws Exception {
		String expiredToken = buildExpiredToken(JWT_SECRET);

		mockMvc.perform(post("/auth/refresh")
			.header("Authorization", "Bearer " + expiredToken))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRejectsAuthorizationWithoutBearerPrefix() throws Exception {
		UUID userId = UUID.randomUUID();
		String token = jwtService.generateToken(userId, "user@example.com");

		mockMvc.perform(post("/auth/refresh")
			.header("Authorization", token))
			.andExpect(status().isUnauthorized());
	}

	private static User userWithEncodedPassword(String email, String rawPassword) {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail(email);
		user.setPasswordHash(new BCryptPasswordEncoder().encode(rawPassword));
		return user;
	}

	private static String registerPayload(String email, String password, String name) {
		return """
			{
			  "email": "%s",
			  "password": "%s",
			  "name": "%s"
			}
			""".formatted(email, password, name);
	}

	private static String loginPayload(String email, String password) {
		return """
			{
			  "email": "%s",
			  "password": "%s"
			}
			""".formatted(email, password);
	}

	private static String buildExpiredToken(String secret) {
		SecretKey signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		Instant past = Instant.now().minusSeconds(3600);

		return Jwts.builder()
			.claim("userId", UUID.randomUUID().toString())
			.subject("user@example.com")
			.issuedAt(Date.from(past.minusSeconds(1800)))
			.expiration(Date.from(past))
			.signWith(signingKey)
			.compact();
	}

}

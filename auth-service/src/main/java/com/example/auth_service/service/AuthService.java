package com.example.auth_service.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.LoginRequest;
import com.example.auth_service.dto.LoginResponse;
import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.RegisterResponse;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.exception.InvalidCredentialsException;
import com.example.auth_service.repository.UserRepository;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public RegisterResponse register(RegisterRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new EmailAlreadyInUseException(normalizedEmail);
		}

		User user = new User();
		user.setEmail(normalizedEmail);
		user.setName(request.name().trim());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setIsActive(Boolean.TRUE);

		User savedUser = userRepository.save(user);

		return new RegisterResponse(
			savedUser.getId(),
			savedUser.getEmail(),
			savedUser.getName()
		);
	}

	public LoginResponse login(LoginRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		User user = userRepository.findByEmail(normalizedEmail)
			.orElseThrow(InvalidCredentialsException::new);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}

		String token = jwtService.generateToken(user.getId(), user.getEmail());

		return new LoginResponse(
			token,
			"Bearer",
			jwtService.getExpirationMs(),
			user.getId(),
			user.getEmail()
		);
	}

	public LoginResponse refresh(JwtPrincipal principal) {
		String token = jwtService.generateToken(principal.userId(), principal.email());

		return new LoginResponse(
			token,
			"Bearer",
			jwtService.getExpirationMs(),
			principal.userId(),
			principal.email()
		);
	}

}

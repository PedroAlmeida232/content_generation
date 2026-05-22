package com.example.auth_service.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.RegisterRequest;
import com.example.auth_service.dto.RegisterResponse;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
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

}

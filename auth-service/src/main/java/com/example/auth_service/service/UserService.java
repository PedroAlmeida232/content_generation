package com.example.auth_service.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.UpdateUserRequest;
import com.example.auth_service.dto.UserResponse;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.exception.UserNotFoundException;
import com.example.auth_service.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public UserResponse getUserProfile(UUID userId) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new UserNotFoundException("User not found"));
		return new UserResponse(user.getId(), user.getEmail(), user.getName());
	}

	@Transactional
	public UserResponse updateUserProfile(UUID userId, UpdateUserRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new UserNotFoundException("User not found"));

		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

		if (!user.getEmail().equalsIgnoreCase(normalizedEmail)) {
			if (userRepository.existsByEmail(normalizedEmail)) {
				throw new EmailAlreadyInUseException(normalizedEmail);
			}
			user.setEmail(normalizedEmail);
		}

		user.setName(request.name().trim());

		User savedUser = userRepository.save(user);

		return new UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getName());
	}

}

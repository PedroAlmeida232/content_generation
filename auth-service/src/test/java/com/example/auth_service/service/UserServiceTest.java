package com.example.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.mapstruct.factory.Mappers;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.UpdateUserRequest;
import com.example.auth_service.dto.UserResponse;
import com.example.auth_service.exception.EmailAlreadyInUseException;
import com.example.auth_service.exception.UserNotFoundException;
import com.example.auth_service.mapper.UserMapper;
import com.example.auth_service.repository.UserRepository;

class UserServiceTest {

	private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
	private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
	private final UserService userService = new UserService(userRepository, userMapper);

	@Test
	void getUserProfileReturnsUserProfileForValidId() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);
		user.setEmail("user@example.com");
		user.setName("Pedro");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UserResponse response = userService.getUserProfile(userId);

		assertEquals(userId, response.id());
		assertEquals("user@example.com", response.email());
		assertEquals("Pedro", response.name());
	}

	@Test
	void getUserProfileThrowsUserNotFoundExceptionWhenNotFound() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		assertThrows(UserNotFoundException.class, () -> userService.getUserProfile(userId));
	}

	@Test
	void updateUserProfileUpdatesSuccessfully() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);
		user.setEmail("user@example.com");
		user.setName("Pedro");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UpdateUserRequest request = new UpdateUserRequest("newemail@example.com", "New Name");
		when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);

		UserResponse response = userService.updateUserProfile(userId, request);

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertEquals("newemail@example.com", savedUser.getEmail());
		assertEquals("New Name", savedUser.getName());
		assertEquals("newemail@example.com", response.email());
		assertEquals("New Name", response.name());
	}

	@Test
	void updateUserProfileDoesNotCheckEmailUniquenessIfSameEmail() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);
		user.setEmail("user@example.com");
		user.setName("Pedro");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UpdateUserRequest request = new UpdateUserRequest("USER@example.com", "New Name");

		UserResponse response = userService.updateUserProfile(userId, request);

		verify(userRepository, never()).existsByEmail(any());
		verify(userRepository).save(any());
		assertEquals("user@example.com", response.email());
		assertEquals("New Name", response.name());
	}

	@Test
	void updateUserProfileThrowsEmailAlreadyInUseException() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);
		user.setEmail("user@example.com");
		user.setName("Pedro");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		UpdateUserRequest request = new UpdateUserRequest("existing@example.com", "New Name");
		when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

		assertThrows(EmailAlreadyInUseException.class, () -> userService.updateUserProfile(userId, request));
		verify(userRepository, never()).save(any());
	}

	@Test
	void updateUserProfileThrowsUserNotFoundExceptionWhenNotFound() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		UpdateUserRequest request = new UpdateUserRequest("new@example.com", "New Name");
		assertThrows(UserNotFoundException.class, () -> userService.updateUserProfile(userId, request));
		verify(userRepository, never()).save(any());
	}
}

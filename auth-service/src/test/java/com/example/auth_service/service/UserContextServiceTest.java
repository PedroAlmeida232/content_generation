package com.example.auth_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.auth_service.domain.User;
import com.example.auth_service.domain.UserContext;
import com.example.auth_service.dto.CreateContextRequest;
import com.example.auth_service.dto.UpdateContextRequest;
import com.example.auth_service.dto.UserContextDto;
import com.example.auth_service.exception.ContextAlreadyExistsException;
import com.example.auth_service.exception.ContextNotFoundException;
import com.example.auth_service.exception.UserNotFoundException;
import com.example.auth_service.mapper.UserContextMapper;
import com.example.auth_service.repository.UserContextRepository;
import com.example.auth_service.repository.UserRepository;

class UserContextServiceTest {

	private final UserContextRepository userContextRepository = org.mockito.Mockito.mock(UserContextRepository.class);
	private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
	private final UserContextMapper userContextMapper = UserContextMapper.INSTANCE;
	private final UserContextService userContextService = new UserContextService(userContextRepository, userRepository, userContextMapper);

	@Test
	void getContextsReturnsAllContextsForUser() {
		UUID userId = UUID.randomUUID();
		UserContext context1 = createMockContext(UUID.randomUUID(), userId, "key1", "val1");
		UserContext context2 = createMockContext(UUID.randomUUID(), userId, "key2", "val2");

		when(userContextRepository.findByUserId(userId)).thenReturn(List.of(context1, context2));

		List<UserContextDto> result = userContextService.getContexts(userId);

		assertEquals(2, result.size());
		assertEquals("key1", result.get(0).contextKey());
		assertEquals("key2", result.get(1).contextKey());
	}

	@Test
	void getContextReturnsContextWhenBelongsToUser() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, userId, "key", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));

		UserContextDto response = userContextService.getContext(userId, contextId);

		assertNotNull(response);
		assertEquals(contextId, response.id());
		assertEquals("key", response.contextKey());
		assertEquals("val", response.contextValue());
	}

	@Test
	void getContextThrowsContextNotFoundExceptionWhenNotFound() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();

		when(userContextRepository.findById(contextId)).thenReturn(Optional.empty());

		assertThrows(ContextNotFoundException.class, () -> userContextService.getContext(userId, contextId));
	}

	@Test
	void getContextThrowsContextNotFoundExceptionWhenBelongsToAnotherUser() {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, otherUserId, "key", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));

		assertThrows(ContextNotFoundException.class, () -> userContextService.getContext(userId, contextId));
	}

	@Test
	void createContextSavesAndReturnsContextSuccessfully() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userContextRepository.findByUserIdAndContextKey(userId, "newKey")).thenReturn(Optional.empty());
		when(userContextRepository.save(any(UserContext.class))).thenAnswer(invocation -> {
			UserContext context = invocation.getArgument(0);
			context.setId(UUID.randomUUID());
			return context;
		});

		CreateContextRequest request = new CreateContextRequest("newKey", "newValue");
		UserContextDto response = userContextService.createContext(userId, request);

		assertNotNull(response.id());
		assertEquals("newKey", response.contextKey());
		assertEquals("newValue", response.contextValue());

		ArgumentCaptor<UserContext> contextCaptor = ArgumentCaptor.forClass(UserContext.class);
		verify(userContextRepository).save(contextCaptor.capture());
		assertEquals(user, contextCaptor.getValue().getUser());
	}

	@Test
	void createContextThrowsUserNotFoundExceptionWhenUserDoesNotExist() {
		UUID userId = UUID.randomUUID();
		when(userRepository.findById(userId)).thenReturn(Optional.empty());

		CreateContextRequest request = new CreateContextRequest("key", "value");
		assertThrows(UserNotFoundException.class, () -> userContextService.createContext(userId, request));
		verify(userContextRepository, never()).save(any());
	}

	@Test
	void createContextThrowsContextAlreadyExistsExceptionWhenKeyAlreadyTaken() {
		UUID userId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);

		UserContext existingContext = createMockContext(UUID.randomUUID(), userId, "key", "val");

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(userContextRepository.findByUserIdAndContextKey(userId, "key")).thenReturn(Optional.of(existingContext));

		CreateContextRequest request = new CreateContextRequest("key", "value");
		assertThrows(ContextAlreadyExistsException.class, () -> userContextService.createContext(userId, request));
		verify(userContextRepository, never()).save(any());
	}

	@Test
	void updateContextUpdatesValueSuccessfullyWithoutChangingKey() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, userId, "key", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));
		when(userContextRepository.save(any(UserContext.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UpdateContextRequest request = new UpdateContextRequest("key", "newVal");
		UserContextDto response = userContextService.updateContext(userId, contextId, request);

		assertEquals("key", response.contextKey());
		assertEquals("newVal", response.contextValue());
		verify(userContextRepository, never()).findByUserIdAndContextKey(any(), any());
	}

	@Test
	void updateContextUpdatesKeySuccessfullyWhenNewKeyIsUnique() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, userId, "oldKey", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));
		when(userContextRepository.findByUserIdAndContextKey(userId, "newKey")).thenReturn(Optional.empty());
		when(userContextRepository.save(any(UserContext.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UpdateContextRequest request = new UpdateContextRequest("newKey", "val");
		UserContextDto response = userContextService.updateContext(userId, contextId, request);

		assertEquals("newKey", response.contextKey());
		verify(userContextRepository).findByUserIdAndContextKey(userId, "newKey");
	}

	@Test
	void updateContextThrowsContextNotFoundExceptionWhenNotFound() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();

		when(userContextRepository.findById(contextId)).thenReturn(Optional.empty());

		UpdateContextRequest request = new UpdateContextRequest("key", "val");
		assertThrows(ContextNotFoundException.class, () -> userContextService.updateContext(userId, contextId, request));
		verify(userContextRepository, never()).save(any());
	}

	@Test
	void updateContextThrowsContextNotFoundExceptionWhenBelongsToAnotherUser() {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, otherUserId, "key", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));

		UpdateContextRequest request = new UpdateContextRequest("key", "val");
		assertThrows(ContextNotFoundException.class, () -> userContextService.updateContext(userId, contextId, request));
		verify(userContextRepository, never()).save(any());
	}

	@Test
	void updateContextThrowsContextAlreadyExistsExceptionWhenNewKeyIsTaken() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, userId, "oldKey", "val");
		UserContext otherContext = createMockContext(UUID.randomUUID(), userId, "takenKey", "otherVal");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));
		when(userContextRepository.findByUserIdAndContextKey(userId, "takenKey")).thenReturn(Optional.of(otherContext));

		UpdateContextRequest request = new UpdateContextRequest("takenKey", "val");
		assertThrows(ContextAlreadyExistsException.class, () -> userContextService.updateContext(userId, contextId, request));
		verify(userContextRepository, never()).save(any());
	}

	@Test
	void deleteContextDeletesSuccessfully() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, userId, "key", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));

		userContextService.deleteContext(userId, contextId);

		verify(userContextRepository).delete(context);
	}

	@Test
	void deleteContextThrowsContextNotFoundExceptionWhenNotFound() {
		UUID userId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();

		when(userContextRepository.findById(contextId)).thenReturn(Optional.empty());

		assertThrows(ContextNotFoundException.class, () -> userContextService.deleteContext(userId, contextId));
		verify(userContextRepository, never()).delete(any(UserContext.class));
	}

	@Test
	void deleteContextThrowsContextNotFoundExceptionWhenBelongsToAnotherUser() {
		UUID userId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID contextId = UUID.randomUUID();
		UserContext context = createMockContext(contextId, otherUserId, "key", "val");

		when(userContextRepository.findById(contextId)).thenReturn(Optional.of(context));

		assertThrows(ContextNotFoundException.class, () -> userContextService.deleteContext(userId, contextId));
		verify(userContextRepository, never()).delete(any(UserContext.class));
	}

	private UserContext createMockContext(UUID contextId, UUID userId, String key, String val) {
		User user = new User();
		user.setId(userId);

		UserContext context = new UserContext();
		context.setId(contextId);
		context.setUser(user);
		context.setContextKey(key);
		context.setContextValue(val);
		context.setCreatedAt(LocalDateTime.now());
		context.setUpdatedAt(LocalDateTime.now());
		return context;
	}

}

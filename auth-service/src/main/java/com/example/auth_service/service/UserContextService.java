package com.example.auth_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class UserContextService {

	private final UserContextRepository userContextRepository;
	private final UserContextMapper userContextMapper;
	private final UserRepository userRepository;

	public UserContextService(UserContextRepository userContextRepository, UserRepository userRepository, UserContextMapper userContextMapper) {
		this.userContextRepository = userContextRepository;
		this.userRepository = userRepository;
		this.userContextMapper = userContextMapper;
	}

	public List<UserContextDto> getContexts(UUID userId) {
		return userContextRepository.findByUserId(userId).stream()
			.map(this::mapToDto)
			.toList();
	}

	public UserContextDto getContext(UUID userId, UUID contextId) {
		UserContext context = userContextRepository.findById(contextId)
			.filter(c -> c.getUser().getId().equals(userId))
			.orElseThrow(() -> new ContextNotFoundException("Context not found with id: " + contextId));
		return mapToDto(context);
	}

	@Transactional
	public UserContextDto createContext(UUID userId, CreateContextRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new UserNotFoundException("User not found"));

		String key = request.contextKey().trim();
		if (userContextRepository.findByUserIdAndContextKey(userId, key).isPresent()) {
			throw new ContextAlreadyExistsException("Context key already exists: " + key);
		}

		UserContext context = new UserContext();
		context.setUser(user);
		context.setContextKey(key);
		context.setContextValue(request.contextValue() != null ? request.contextValue().trim() : null);

		UserContext saved = userContextRepository.save(context);
		return mapToDto(saved);
	}

	@Transactional
	public UserContextDto updateContext(UUID userId, UUID contextId, UpdateContextRequest request) {
		UserContext context = userContextRepository.findById(contextId)
			.filter(c -> c.getUser().getId().equals(userId))
			.orElseThrow(() -> new ContextNotFoundException("Context not found with id: " + contextId));

		String key = request.contextKey().trim();
		if (!context.getContextKey().equals(key)) {
			if (userContextRepository.findByUserIdAndContextKey(userId, key).isPresent()) {
				throw new ContextAlreadyExistsException("Context key already exists: " + key);
			}
			context.setContextKey(key);
		}

		context.setContextValue(request.contextValue() != null ? request.contextValue().trim() : null);

		UserContext saved = userContextRepository.save(context);
		return mapToDto(saved);
	}

	@Transactional
	public void deleteContext(UUID userId, UUID contextId) {
		UserContext context = userContextRepository.findById(contextId)
			.filter(c -> c.getUser().getId().equals(userId))
			.orElseThrow(() -> new ContextNotFoundException("Context not found with id: " + contextId));
		userContextRepository.delete(context);
	}

	private UserContextDto mapToDto(UserContext context) {
		return userContextMapper.toDto(context);
	}

}

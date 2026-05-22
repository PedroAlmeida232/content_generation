package com.example.auth_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserContextDto(
    UUID id,
    UUID userId,
    String contextKey,
    String contextValue,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

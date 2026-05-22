package com.example.auth_service.dto;

import java.util.UUID;

public record UserDto(
    UUID id,
    String email,
    String fullName
) {}

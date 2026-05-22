package com.example.auth_service.dto;

import java.util.UUID;

/**
 * DTO utilizado para retornar informações do perfil do usuário autenticado.
 * Campos sensíveis como {@code passwordHash} são excluídos intencionalmente.
 */
public record UserResponse(UUID id, String email, String name) {}

package com.example.auth_service.mapper;

import org.mapstruct.Mapper;

import com.example.auth_service.domain.UserContext;
import com.example.auth_service.dto.UserContextResponse;

@Mapper(componentModel = "spring")
public interface UserContextMapper {

	UserContextResponse toResponse(UserContext context);

}

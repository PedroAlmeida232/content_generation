package com.example.auth_service.mapper;

import org.mapstruct.Mapper;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.UserResponse;

@Mapper(componentModel = "spring")
public interface UserMapper {

	UserResponse toResponse(User user);

}

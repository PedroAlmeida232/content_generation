package com.example.auth_service.mapper;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    @Mapping(source = "name", target = "fullName")
    UserDto toDto(User user);

    // Optional reverse mapping if needed
    @Mapping(source = "fullName", target = "name")
    User toEntity(UserDto dto);
}

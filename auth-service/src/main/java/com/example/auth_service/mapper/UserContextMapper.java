package com.example.auth_service.mapper;

import com.example.auth_service.domain.UserContext;
import com.example.auth_service.dto.UserContextDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserContextMapper {
    UserContextMapper INSTANCE = Mappers.getMapper(UserContextMapper.class);

    @Mapping(source = "user.id", target = "userId")
    UserContextDto toDto(UserContext entity);

    // Reverse mapping if needed
    @Mapping(source = "userId", target = "user.id")
    UserContext toEntity(UserContextDto dto);
}

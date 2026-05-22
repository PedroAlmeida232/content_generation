package com.example.auth_service.mapper;

import com.example.auth_service.domain.User;
import com.example.auth_service.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Manual mapper for User to avoid Lombok issues with MapStruct.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    default UserDto toDto(User user) {
        if (user == null) return null;
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getName()
        );
    }

    default User toEntity(UserDto dto) {
        if (dto == null) return null;
        User user = new User();
        user.setId(dto.id());
        user.setEmail(dto.email());
        user.setName(dto.fullName());
        return user;
    }
}

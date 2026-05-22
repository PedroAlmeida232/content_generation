package com.example.auth_service.mapper;

import com.example.auth_service.domain.User;
import com.example.auth_service.domain.UserContext;
import com.example.auth_service.dto.UserContextDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * Manual mapper for UserContext to avoid Lombok/MapStruct nested property issues.
 */
@Mapper(componentModel = "spring")
public interface UserContextMapper {
    UserContextMapper INSTANCE = Mappers.getMapper(UserContextMapper.class);

    default UserContextDto toDto(UserContext entity) {
        if (entity == null) return null;
        return new UserContextDto(
                entity.getId(),
                entity.getUser() != null ? entity.getUser().getId() : null,
                entity.getContextKey(),
                entity.getContextValue(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    default UserContext toEntity(UserContextDto dto) {
        if (dto == null) return null;
        UserContext ctx = new UserContext();
        ctx.setContextKey(dto.contextKey());
        ctx.setContextValue(dto.contextValue());
        if (dto.userId() != null) {
            User user = new User();
            user.setId(dto.userId());
            ctx.setUser(user);
        }
        return ctx;
    }
}

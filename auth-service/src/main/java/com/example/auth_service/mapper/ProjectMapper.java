package com.example.auth_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.example.auth_service.domain.Project;
import com.example.auth_service.domain.ProjectSlide;
import com.example.auth_service.dto.ProjectSlideResponse;
import com.example.auth_service.dto.ProjectSummaryResponse;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

	@Mapping(source = "name", target = "title")
	@Mapping(target = "firstSlideImageUrl", ignore = true)
	ProjectSummaryResponse toSummaryResponse(Project project);

	@Mapping(source = "slideOrder", target = "slideOrder")
	@Mapping(source = "imageUrl", target = "imageUrl")
	@Mapping(source = "promptUsed", target = "promptUsed")
	@Mapping(source = "generatedAt", target = "generatedAt")
	ProjectSlideResponse toSlideResponse(ProjectSlide slide);

}

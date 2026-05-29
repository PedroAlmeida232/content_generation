package com.example.auth_service.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public record SaveProjectSlidesRequest(
	@NotEmpty(message = "Slides are required")
	@Valid
	List<SaveProjectSlideItemRequest> slides
) {
	public record SaveProjectSlideItemRequest(
		@JsonProperty("slide_order")
		@NotNull(message = "Slide order is required")
		@Min(value = 1, message = "Slide order must be at least 1")
		Integer slideOrder,

		@JsonProperty("image_url")
		@NotBlank(message = "Image URL is required")
		String imageUrl,

		@NotBlank(message = "Caption is required")
		String caption,

		@JsonProperty("prompt_used")
		@NotBlank(message = "Prompt used is required")
		String promptUsed
	) {
	}
}

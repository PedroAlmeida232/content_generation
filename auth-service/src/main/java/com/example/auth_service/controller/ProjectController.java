package com.example.auth_service.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.auth_service.dto.CreateProjectRequest;
import com.example.auth_service.dto.ProjectDetailResponse;
import com.example.auth_service.dto.ProjectSummaryResponse;
import com.example.auth_service.dto.SaveProjectSlidesRequest;
import com.example.auth_service.security.JwtAuthenticationFilter.JwtPrincipal;
import com.example.auth_service.service.ProjectService;

@RestController
@RequestMapping("/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public List<ProjectSummaryResponse> getProjects(Authentication authentication) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return projectService.getProjects(principal.userId());
	}

	@PostMapping
	public ResponseEntity<ProjectDetailResponse> createProject(
		Authentication authentication,
		@Valid @RequestBody CreateProjectRequest request
	) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		ProjectDetailResponse response = projectService.createProject(principal.userId(), request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/{id}")
	public ProjectDetailResponse getProject(Authentication authentication, @PathVariable UUID id) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return projectService.getProject(principal.userId(), id);
	}

	@PostMapping("/{id}/slides")
	public ProjectDetailResponse saveProjectSlides(
		Authentication authentication,
		@PathVariable UUID id,
		@Valid @RequestBody SaveProjectSlidesRequest request
	) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		return projectService.saveProjectSlides(principal.userId(), id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteProject(Authentication authentication, @PathVariable UUID id) {
		JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
		projectService.deleteProject(principal.userId(), id);
		return ResponseEntity.noContent().build();
	}

}

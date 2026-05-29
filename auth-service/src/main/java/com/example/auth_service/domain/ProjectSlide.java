package com.example.auth_service.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "project_slides")
@Getter
@Setter
public class ProjectSlide {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(name = "slide_order", nullable = false)
	private int slideOrder;

	@Column(name = "image_url")
	private String imageUrl;

	@Column
	private String caption;

	@Column(name = "prompt_used")
	private String promptUsed;

	@Column(name = "generated_at", nullable = false)
	@CreationTimestamp
	private LocalDateTime generatedAt;

}

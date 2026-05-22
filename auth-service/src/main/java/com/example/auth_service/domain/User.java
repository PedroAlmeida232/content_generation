package com.example.auth_service.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column
	private String name;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = Boolean.TRUE;

	@OneToMany(mappedBy = "user")
	private List<UserContext> contexts = new ArrayList<>();

	@OneToMany(mappedBy = "user")
	private List<Project> projects = new ArrayList<>();

}

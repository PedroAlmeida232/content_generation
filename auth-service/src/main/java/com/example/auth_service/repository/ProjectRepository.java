package com.example.auth_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.auth_service.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	Page<Project> findByUserId(UUID userId, Pageable pageable);

	Page<Project> findByUserIdAndStatusIgnoreCase(UUID userId, String status, Pageable pageable);

	@Query("""
		select p
		from Project p
		where p.user.id = :userId
		  and (:status is null or lower(p.status) = lower(:status))
		  and (
		    :search is null
		    or lower(p.name) like lower(concat('%', :search, '%'))
		    or lower(coalesce(p.description, '')) like lower(concat('%', :search, '%'))
		  )
		""")
	Page<Project> findByUserIdAndStatusAndSearch(
		@Param("userId") UUID userId,
		@Param("status") String status,
		@Param("search") String search,
		Pageable pageable
	);

	java.util.Optional<Project> findByIdAndUserId(UUID id, UUID userId);

}

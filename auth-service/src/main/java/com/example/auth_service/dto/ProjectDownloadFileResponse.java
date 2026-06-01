package com.example.auth_service.dto;

public record ProjectDownloadFileResponse(
	byte[] content,
	String contentType,
	String filename
) {
}

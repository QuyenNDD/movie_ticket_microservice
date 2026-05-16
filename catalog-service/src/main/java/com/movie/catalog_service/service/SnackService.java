package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.SnackRequestDTO;
import com.movie.catalog_service.dto.response.SnackResponse;
import com.movie.catalog_service.dto.response.SnackResponseDTO;
import jakarta.validation.Valid;

public interface SnackService {
    SnackResponse getAllSnack(boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    SnackResponseDTO createSnack(@Valid SnackRequestDTO request);

    SnackResponseDTO updateSnack(String snackId, @Valid SnackRequestDTO request);

    SnackResponseDTO deleteSnack(String snackId);

    SnackResponseDTO getSnackById(String snackId);

    SnackResponseDTO updateSnackImage(String snackId, String newImageUrl);
}

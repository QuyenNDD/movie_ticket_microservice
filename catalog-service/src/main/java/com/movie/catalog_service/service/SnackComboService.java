package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.SnackComboRequestDTO;
import com.movie.catalog_service.dto.response.SnackComboResponse;
import com.movie.catalog_service.dto.response.SnackComboResponseDTO;

public interface SnackComboService {
    SnackComboResponse getAllCombos(Boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    SnackComboResponseDTO getComboById(String comboId);
    SnackComboResponseDTO createCombo(SnackComboRequestDTO request);
    SnackComboResponseDTO updateCombo(String comboId, SnackComboRequestDTO request);
    SnackComboResponseDTO deleteCombo(String comboId);
    SnackComboResponseDTO updateComboImage(String comboId, String newImageUrl);
    Double getComboPrice(String comboId);
}

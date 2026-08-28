package com.movie.catalog_service.controller;

import com.movie.catalog_service.config.AppConstants;
import com.movie.catalog_service.dto.request.SnackComboRequestDTO;
import com.movie.catalog_service.dto.response.SnackComboResponse;
import com.movie.catalog_service.dto.response.SnackComboResponseDTO;
import com.movie.catalog_service.service.SnackComboService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/snack-combos")
public class SnackComboController {
    @Autowired
    SnackComboService snackComboService;

    @GetMapping()
    public ResponseEntity<SnackComboResponse> getAllCombos(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_SNACK_COMBO_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {

        SnackComboResponse response = snackComboService.getAllCombos(isActive, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/{comboId}")
    public ResponseEntity<SnackComboResponseDTO> getComboById(@PathVariable String comboId) {
        SnackComboResponseDTO response = snackComboService.getComboById(comboId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping()
    public ResponseEntity<SnackComboResponseDTO> createCombo(@Valid @RequestBody SnackComboRequestDTO request) {
        SnackComboResponseDTO response = snackComboService.createCombo(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{comboId}")
    public ResponseEntity<SnackComboResponseDTO> updateCombo(
            @PathVariable String comboId,
            @Valid @RequestBody SnackComboRequestDTO request) {
        SnackComboResponseDTO response = snackComboService.updateCombo(comboId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @DeleteMapping("/{comboId}")
    public ResponseEntity<SnackComboResponseDTO> deleteCombo(@PathVariable String comboId) {
        SnackComboResponseDTO response = snackComboService.deleteCombo(comboId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PatchMapping("/{comboId}/image")
    public ResponseEntity<SnackComboResponseDTO> updateComboImage(
            @PathVariable String comboId,
            @RequestParam String imageUrl) {
        SnackComboResponseDTO response = snackComboService.updateComboImage(comboId, imageUrl);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

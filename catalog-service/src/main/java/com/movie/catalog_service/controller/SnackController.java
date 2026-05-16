package com.movie.catalog_service.controller;

import com.movie.catalog_service.config.AppConstants;
import com.movie.catalog_service.dto.request.SnackRequestDTO;
import com.movie.catalog_service.dto.response.SnackResponse;
import com.movie.catalog_service.dto.response.SnackResponseDTO;
import com.movie.catalog_service.entity.Snack;
import com.movie.catalog_service.service.SnackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/snacks")
public class SnackController {
    @Autowired
    SnackService snackService;

    //Lay tat ca
    @GetMapping()
    public ResponseEntity<SnackResponse> getAllSnack(
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(name = "pageNumber", defaultValue = AppConstants.PAGE_NUMBER, required = false) Integer pageNumber,
            @RequestParam(name = "pageSize", defaultValue = AppConstants.PAGE_SIZE, required = false) Integer pageSize,
            @RequestParam(name = "sortBy", defaultValue = AppConstants.SORT_SNACK_BY, required = false) String sortBy,
            @RequestParam(name = "sortOrder", defaultValue = AppConstants.SORT_DIR, required = false) String sortOrder) {

        SnackResponse response = snackService.getAllSnack(isActive, pageNumber, pageSize, sortBy, sortOrder);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    //Tao moi
    @PostMapping()
    public ResponseEntity<SnackResponseDTO> createSnack(
            @Valid @RequestBody SnackRequestDTO request
            ) {
        SnackResponseDTO response = snackService.createSnack(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    //Cap nhat
    @PutMapping("/{snackId}")
    public ResponseEntity<SnackResponseDTO> updateSnack(
            @PathVariable String snackId,
            @Valid @RequestBody SnackRequestDTO request) {
        SnackResponseDTO response = snackService.updateSnack(snackId, request);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    //Xoa mem
    @DeleteMapping("/{snackId}")
    public ResponseEntity<SnackResponseDTO> deleteSnack(@PathVariable String snackId) {
        SnackResponseDTO response = snackService.deleteSnack(snackId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    //Lay theo id
    @GetMapping("/{snackId}")
    public ResponseEntity<SnackResponseDTO> getSnackById(@PathVariable String snackId) {
        SnackResponseDTO response = snackService.getSnackById(snackId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    //Cap nhat anh
    @PatchMapping("/{snackId}/image")
    public ResponseEntity<SnackResponseDTO> updateSnackImage(@PathVariable String snackId,
                                                             @RequestParam("imageUrl") String imageUrl) {
        SnackResponseDTO response = snackService.updateSnackImage(snackId, imageUrl);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

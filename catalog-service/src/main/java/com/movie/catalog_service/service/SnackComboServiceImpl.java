package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.SnackComboRequestDTO;
import com.movie.catalog_service.dto.response.SnackComboResponse;
import com.movie.catalog_service.dto.response.SnackComboResponseDTO;
import com.movie.catalog_service.entity.Snack;
import com.movie.catalog_service.entity.SnackCombo;
import com.movie.catalog_service.entity.SnackComboItem;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.SnackComboRepository;
import com.movie.catalog_service.repository.SnackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SnackComboServiceImpl implements SnackComboService {
    @Autowired
    SnackComboRepository snackComboRepository;

    @Autowired
    SnackRepository snackRepository;

    @Override
    public SnackComboResponse getAllCombos(Boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<SnackCombo> comboPage = snackComboRepository.filterCombos(isActive, pageDetails);

        List<SnackComboResponseDTO> content = comboPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        SnackComboResponse response = new SnackComboResponse();
        response.setContent(content);
        response.setPageNumber(comboPage.getNumber());
        response.setPageSize(comboPage.getSize());
        response.setTotalPages(comboPage.getTotalPages());
        response.setTotalElements(comboPage.getTotalElements());
        response.setLastPage(comboPage.isLast());
        return response;
    }

    @Override
    public SnackComboResponseDTO getComboById(String comboId) {
        SnackCombo combo = snackComboRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("SnackCombo", "comboId", comboId));
        return mapToResponse(combo);
    }

    @Override
    @Transactional
    public SnackComboResponseDTO createCombo(SnackComboRequestDTO request) {
        if (snackComboRepository.findByName(request.getName()).isPresent()) {
            throw new APIException("Name of combo: " + request.getName() + " is available");
        }

        List<SnackComboItem> items = buildComboItems(request);
        validateComboIsCheaperThanItemsTotal(request.getPrice(), items);

        SnackCombo combo = SnackCombo.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .isActive(true)
                .build();

        for (SnackComboItem item : items) {
            item.setCombo(combo);
        }
        combo.setItems(items);

        SnackCombo savedCombo = snackComboRepository.save(combo);
        return mapToResponse(savedCombo);
    }

    @Override
    @Transactional
    public SnackComboResponseDTO updateCombo(String comboId, SnackComboRequestDTO request) {
        SnackCombo existingCombo = snackComboRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("SnackCombo", "comboId", comboId));

        if (!Objects.equals(existingCombo.getName(), request.getName())) {
            if (snackComboRepository.existsByName(request.getName())) {
                throw new APIException("Name of combo: " + request.getName() + " is available");
            }
            existingCombo.setName(request.getName());
        }

        List<SnackComboItem> items = buildComboItems(request);
        validateComboIsCheaperThanItemsTotal(request.getPrice(), items);

        existingCombo.setDescription(request.getDescription());
        existingCombo.setPrice(request.getPrice());
        existingCombo.setImageUrl(request.getImageUrl());
        existingCombo.setIsActive(request.getIsActive());

        existingCombo.getItems().clear();
        for (SnackComboItem item : items) {
            item.setCombo(existingCombo);
            existingCombo.getItems().add(item);
        }

        SnackCombo savedCombo = snackComboRepository.save(existingCombo);
        return mapToResponse(savedCombo);
    }

    @Override
    public SnackComboResponseDTO deleteCombo(String comboId) {
        SnackCombo existingCombo = snackComboRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("SnackCombo", "comboId", comboId));
        existingCombo.setIsActive(false);
        SnackCombo savedCombo = snackComboRepository.save(existingCombo);
        return mapToResponse(savedCombo);
    }

    @Override
    public SnackComboResponseDTO updateComboImage(String comboId, String newImageUrl) {
        SnackCombo existingCombo = snackComboRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("SnackCombo", "comboId", comboId));
        existingCombo.setImageUrl(newImageUrl);
        SnackCombo savedCombo = snackComboRepository.save(existingCombo);
        return mapToResponse(savedCombo);
    }

    @Override
    public Double getComboPrice(String comboId) {
        SnackCombo combo = snackComboRepository.findById(comboId)
                .orElseThrow(() -> new ResourceNotFoundException("SnackCombo", "comboId", comboId));
        return combo.getPrice();
    }

    private List<SnackComboItem> buildComboItems(SnackComboRequestDTO request) {
        List<String> snackIds = request.getItems().stream()
                .map(SnackComboRequestDTO.ComboItemRequest::getSnackId)
                .toList();

        if (snackIds.stream().distinct().count() != snackIds.size()) {
            throw new APIException("Combo có món bị trùng, mỗi món chỉ được khai báo 1 lần");
        }

        List<SnackComboItem> items = new ArrayList<>();
        for (SnackComboRequestDTO.ComboItemRequest itemReq : request.getItems()) {
            Snack snack = snackRepository.findById(itemReq.getSnackId())
                    .orElseThrow(() -> new ResourceNotFoundException("Snack", "snackId", itemReq.getSnackId()));

            SnackComboItem item = new SnackComboItem();
            item.setSnackId(snack.getId());
            item.setQuantity(itemReq.getQuantity());
            items.add(item);
        }
        return items;
    }

    private void validateComboIsCheaperThanItemsTotal(Double comboPrice, List<SnackComboItem> items) {
        Map<String, Snack> snackById = snackRepository.findAllById(
                items.stream().map(SnackComboItem::getSnackId).toList()
        ).stream().collect(Collectors.toMap(Snack::getId, snack -> snack));

        double itemsTotal = items.stream()
                .mapToDouble(item -> snackById.get(item.getSnackId()).getPrice() * item.getQuantity())
                .sum();

        if (comboPrice >= itemsTotal) {
            throw new APIException("Giá combo (" + comboPrice + ") phải thấp hơn tổng giá lẻ các món (" + itemsTotal + ") để đảm bảo là gói ưu đãi");
        }
    }

    private SnackComboResponseDTO mapToResponse(SnackCombo combo) {
        Map<String, Snack> snackById = snackRepository.findAllById(
                combo.getItems().stream().map(SnackComboItem::getSnackId).toList()
        ).stream().collect(Collectors.toMap(Snack::getId, snack -> snack));

        List<SnackComboResponseDTO.ComboItemResponse> itemResponses = combo.getItems().stream()
                .map(item -> {
                    Snack snack = snackById.get(item.getSnackId());
                    return SnackComboResponseDTO.ComboItemResponse.builder()
                            .snackId(item.getSnackId())
                            .snackName(snack != null ? snack.getName() : item.getSnackId())
                            .quantity(item.getQuantity())
                            .build();
                })
                .toList();

        return SnackComboResponseDTO.builder()
                .id(combo.getId())
                .name(combo.getName())
                .description(combo.getDescription())
                .price(combo.getPrice())
                .imageUrl(combo.getImageUrl())
                .isActive(combo.getIsActive())
                .items(itemResponses)
                .build();
    }
}

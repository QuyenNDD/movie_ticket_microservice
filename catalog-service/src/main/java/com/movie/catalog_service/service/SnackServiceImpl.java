package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.SnackRequestDTO;
import com.movie.catalog_service.dto.response.SnackResponse;
import com.movie.catalog_service.dto.response.SnackResponseDTO;
import com.movie.catalog_service.entity.Snack;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.SnackRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SnackServiceImpl implements SnackService{
    @Autowired
    SnackRepository snackRepository;

    @Autowired
    ModelMapper modelMapper;

    @Override
    public SnackResponse getAllSnack(boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sortByAndOrder = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageDetails = PageRequest.of(pageNumber, pageSize, sortByAndOrder);
        Page<Snack> snackPage = snackRepository.filterSnacks(isActive, pageDetails);
        List<Snack> snacks = snackPage.getContent();
        List<SnackResponseDTO> snackResponseDTOS = snacks.stream()
                .map(snack -> modelMapper.map(snack, SnackResponseDTO.class))
                .toList();

        SnackResponse snackResponse = new SnackResponse();
        snackResponse.setContent(snackResponseDTOS);
        snackResponse.setPageNumber(snackPage.getNumber());
        snackResponse.setPageSize(snackPage.getSize());
        snackResponse.setTotalPages(snackPage.getTotalPages());
        snackResponse.setTotalElements(snackPage.getTotalElements());
        snackResponse.setLastPage(snackPage.isLast());
        return snackResponse;
    }

    @Override
    public SnackResponseDTO createSnack(SnackRequestDTO request) {
        if (snackRepository.findByTitle(request.getName()).isPresent()) {
            throw new APIException("Title is available");
        }
        Snack snack = Snack.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .imageUrl(request.getImageUrl())
                .isActive(true)
                .build();
        Snack savedSnack = snackRepository.save(snack);
        return modelMapper.map(savedSnack, SnackResponseDTO.class);
    }

    @Override
    public SnackResponseDTO updateSnack(String snackId, SnackRequestDTO request) {
        Snack existingSnack = snackRepository.findById(snackId)
                .orElseThrow(() -> new ResourceNotFoundException("Snack", "snackId", snackId));

        if (!Objects.equals(existingSnack.getName(), request.getName())) {
            if (snackRepository.existsByName(request.getName())) {
                throw new APIException("Name of snack: " + request.getName() + " is available");
            }
            existingSnack.setName(request.getName());
        }
        existingSnack.setDescription(request.getDescription());
        existingSnack.setPrice(request.getPrice());
        existingSnack.setImageUrl(request.getImageUrl());
        existingSnack.setIsActive(request.getIsActive());

        Snack savedSnack = snackRepository.save(existingSnack);
        return modelMapper.map(savedSnack, SnackResponseDTO.class);
    }

    @Override
    public SnackResponseDTO deleteSnack(String snackId) {
        Snack existingSnack = snackRepository.findById(snackId)
                .orElseThrow(() -> new ResourceNotFoundException("Snack", "snackId", snackId));
        existingSnack.setIsActive(false);
        Snack savedSnack = snackRepository.save(existingSnack);
        return modelMapper.map(savedSnack, SnackResponseDTO.class);
    }

    @Override
    public SnackResponseDTO getSnackById(String snackId) {
        Snack snack = snackRepository.findById(snackId)
                .orElseThrow(() -> new ResourceNotFoundException("Snack", "snackId", snackId));
        return modelMapper.map(snack, SnackResponseDTO.class);
    }

    @Override
    public SnackResponseDTO updateSnackImage(String snackId, String newImageUrl) {
        Snack existingSnack = snackRepository.findById(snackId)
                .orElseThrow(() -> new ResourceNotFoundException("Snack", "snackId", snackId));
        existingSnack.setImageUrl(newImageUrl);
        Snack savedSnack = snackRepository.save(existingSnack);
        return modelMapper.map(savedSnack, SnackResponseDTO.class);
    }
}

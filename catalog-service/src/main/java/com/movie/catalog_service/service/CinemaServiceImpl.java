package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.CinemaRequestDTO;
import com.movie.catalog_service.dto.response.CinemaResponse;
import com.movie.catalog_service.dto.response.CinemaResponseDTO;
import com.movie.catalog_service.entity.Cinema;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.CinemaRepository;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CinemaServiceImpl implements CinemaService{
    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public CinemaResponseDTO createCinema(CinemaRequestDTO request) {
        if (cinemaRepository.existsByNameAndCity(request.getName(), request.getCity())) {
            throw new APIException("Cụm rạp " + request.getName() + " đã tồn tại ở thành phố " + request.getCity());
        }

        Cinema cinema = modelMapper.map(request, Cinema.class);
        Cinema savedCinema = cinemaRepository.save(cinema);
        return modelMapper.map(savedCinema, CinemaResponseDTO.class);
    }

    @Override
    public CinemaResponseDTO updateCinema(String cinemaId, CinemaRequestDTO request) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", cinemaId));

        // Kiểm tra xem tên đổi có bị trùng với rạp khác không
        if (!cinema.getName().equals(request.getName()) || !cinema.getCity().equals(request.getCity())) {
            if (cinemaRepository.existsByNameAndCity(request.getName(), request.getCity())) {
                throw new APIException("Tên rạp và thành phố này đã bị trùng với một cụm rạp khác!");
            }
        }

        cinema.setName(request.getName());
        cinema.setAddress(request.getAddress());
        cinema.setCity(request.getCity());

        Cinema updatedCinema = cinemaRepository.save(cinema);
        return modelMapper.map(updatedCinema, CinemaResponseDTO.class);
    }

    @Override
    public CinemaResponseDTO getCinemaById(String cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", cinemaId));
        return modelMapper.map(cinema, CinemaResponseDTO.class);
    }

    @Override
    public CinemaResponseDTO deleteCinema(String cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", cinemaId));
        cinema.setIsActive(false);

        Cinema savedCinema = cinemaRepository.save(cinema);
        return modelMapper.map(savedCinema, CinemaResponseDTO.class);
    }

    @Override
    public CinemaResponse getAllCinemas(Boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sort = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        Page<Cinema> pageCinema = cinemaRepository.searchCinemas(null, null, isActive, pageable);

        List<CinemaResponseDTO> cinemaDTOs = pageCinema.getContent().stream()
                .map(cinema -> modelMapper.map(cinema, CinemaResponseDTO.class))
                .collect(Collectors.toList());

        CinemaResponse response = new CinemaResponse();
        response.setContent(cinemaDTOs);
        response.setPageNumber(pageCinema.getNumber());
        response.setPageSize(pageCinema.getSize());
        response.setTotalElements(pageCinema.getTotalElements());
        response.setTotalPages(pageCinema.getTotalPages());
        response.setLastPage(pageCinema.isLast());

        return response;
    }

    @Override
    public CinemaResponse searchCinemas(String name, String city, Boolean isActive, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder) {
        Sort sort = sortOrder.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);
        Page<Cinema> pageCinema = cinemaRepository.searchCinemas(name, city, isActive, pageable);

        List<CinemaResponseDTO> cinemaDTOs = pageCinema.getContent().stream()
                .map(cinema -> modelMapper.map(cinema, CinemaResponseDTO.class))
                .collect(Collectors.toList());

        CinemaResponse response = new CinemaResponse();
        response.setContent(cinemaDTOs);
        response.setPageNumber(pageCinema.getNumber());
        response.setPageSize(pageCinema.getSize());
        response.setTotalElements(pageCinema.getTotalElements());
        response.setTotalPages(pageCinema.getTotalPages());
        response.setLastPage(pageCinema.isLast());

        return response;
    }
}

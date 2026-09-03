package com.movie.catalog_service.service;

import com.movie.catalog_service.dto.request.CinemaRequestDTO;
import com.movie.catalog_service.dto.response.CinemaResponse;
import com.movie.catalog_service.dto.response.CinemaResponseDTO;
import com.movie.catalog_service.dto.response.RoomResponseDTO;
import com.movie.catalog_service.entity.Cinema;
import com.movie.catalog_service.entity.Room;
import com.movie.catalog_service.entity.ShowtimeStatus;
import com.movie.catalog_service.exception.APIException;
import com.movie.catalog_service.exception.ResourceNotFoundException;
import com.movie.catalog_service.repository.CinemaRepository;
import com.movie.catalog_service.repository.RoomRepository;
import com.movie.catalog_service.repository.ShowtimeRepository;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CinemaServiceImpl implements CinemaService{
    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

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

        CinemaResponseDTO response = modelMapper.map(cinema, CinemaResponseDTO.class);

        List<Room> rooms = cinema.getRooms();
        List<RoomResponseDTO> roomResponse = rooms.stream().map(room -> {
            RoomResponseDTO responseDTO = new RoomResponseDTO();
            responseDTO.setId(room.getId());
            responseDTO.setCinemaId(cinemaId);
            responseDTO.setCinemaName(cinema.getName());
            responseDTO.setName(room.getName());
            responseDTO.setRowCount(room.getRowCount());
            responseDTO.setColumnCount(room.getColumnCount());
            responseDTO.setTotalSeats(room.getTotalSeats());
            responseDTO.setIsActive(room.getIsActive());
            return responseDTO;
        }).toList();
        response.setRooms(roomResponse);
        return response;
    }

    @Override
    @Transactional
    public CinemaResponseDTO deleteCinema(String cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", cinemaId));

        // Khi rạp dừng hoạt động:
        // 1. Hủy toàn bộ suất chiếu tương lai của rạp.
        // 2. Đánh dấu toàn bộ phòng trong rạp là inactive.
        // 3. Đánh dấu rạp inactive.
        cancelFutureShowtimesOfCinema(cinemaId);
        deactivateRoomsOfCinema(cinemaId);

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

        Page<Cinema> pageCinema = cinemaRepository.searchCinemas(null, null, null, pageable);

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

    @Override
    @Transactional
    public CinemaResponseDTO reopenCinema(String cinemaId) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cinema", "id", cinemaId));

        cinema.setIsActive(true);

        Cinema savedCinema = cinemaRepository.save(cinema);

        return modelMapper.map(savedCinema, CinemaResponseDTO.class);
    }

    private void cancelFutureShowtimesOfCinema(String cinemaId) {
        int cancelledCount = showtimeRepository.cancelFutureScheduledShowtimesByCinemaId(
                cinemaId,
                LocalDateTime.now(),
                ShowtimeStatus.SCHEDULED,
                ShowtimeStatus.CANCELLED
        );

        log.info("Đã hủy {} suất chiếu tương lai của rạp {}", cancelledCount, cinemaId);
    }

    private void deactivateRoomsOfCinema(String cinemaId) {
        List<Room> rooms = roomRepository.findByCinemaId(cinemaId);

        if (rooms == null || rooms.isEmpty()) {
            return;
        }

        for (Room room : rooms) {
            room.setIsActive(false);
        }

        roomRepository.saveAll(rooms);
    }
}

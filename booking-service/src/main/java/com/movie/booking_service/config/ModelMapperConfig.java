package com.movie.booking_service.config;

import com.movie.booking_service.dto.BookingRequestDTO;
import com.movie.booking_service.entity.BookingSeat;
import com.movie.booking_service.entity.BookingSnack;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ModelMapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        // Set STRICT để tránh map nhầm các field có tên na ná nhau
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        // Dạy ModelMapper cách map SeatRequest -> BookingSeat (Khác tên biến price -> priceAtPurchase)
        TypeMap<BookingRequestDTO.SeatRequest, BookingSeat> seatMap = mapper.createTypeMap(BookingRequestDTO.SeatRequest.class, BookingSeat.class);
        seatMap.addMapping(BookingRequestDTO.SeatRequest::getPrice, BookingSeat::setPriceAtPurchase);

        // Dạy ModelMapper cách map SnackRequest -> BookingSnack
        TypeMap<BookingRequestDTO.SnackRequest, BookingSnack> snackMap = mapper.createTypeMap(BookingRequestDTO.SnackRequest.class, BookingSnack.class);
        snackMap.addMapping(BookingRequestDTO.SnackRequest::getPrice, BookingSnack::setPriceAtPurchase);

        return mapper;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

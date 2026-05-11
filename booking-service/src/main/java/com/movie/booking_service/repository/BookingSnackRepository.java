package com.movie.booking_service.repository;

import com.movie.booking_service.entity.BookingSnack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSnackRepository extends JpaRepository<BookingSnack, String> {
}

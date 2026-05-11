package com.movie.booking_service.repository;

import com.movie.booking_service.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, String> {
}

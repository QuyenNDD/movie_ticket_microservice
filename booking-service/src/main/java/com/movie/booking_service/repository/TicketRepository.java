package com.movie.booking_service.repository;

import com.movie.booking_service.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, String> {
    List<Ticket> findByBookingSeatIdIn(List<String> bookingSeatIds);
    Optional<Ticket> findByQrCode(String qrCode);
}

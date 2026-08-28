package com.movie.booking_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tickets")
public class Ticket {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "booking_seat_id", nullable = false, unique = true, length = 36)
    private String bookingSeatId;

    @Column(name = "qr_code", nullable = false, unique = true, length = 64)
    private String qrCode;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_in_by", length = 36)
    private String checkedInBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.qrCode == null) {
            this.qrCode = UUID.randomUUID().toString().replace("-", "");
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

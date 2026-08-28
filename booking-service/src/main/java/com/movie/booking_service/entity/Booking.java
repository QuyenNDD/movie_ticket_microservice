package com.movie.booking_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bookings")
public class Booking {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "showtime_id", nullable = false, length = 36)
    private String showtimeId;

    @Column(name = "total_price", nullable = false)
    private Double totalPrice;

    @Column(nullable = false)
    private String status;

    @Column(name = "booking_time", nullable = false)
    private LocalDateTime bookingTime;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    // NOT_APPLICABLE (không cần hoàn tiền) | PENDING (đang chờ hoàn tiền thủ công/tự động)
    @Column(name = "refund_status", length = 20)
    private String refundStatus;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSnack> bookingSnacks = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingCombo> bookingCombos = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.id == null){
            this.id = UUID.randomUUID().toString();
        }
        if (this.bookingTime == null){
            this.bookingTime = LocalDateTime.now();
        }
        if (this.status == null){
            this.status = "PENDING";
        }
    }
}

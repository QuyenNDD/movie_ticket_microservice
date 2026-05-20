package com.movie.catalog_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seats")
public class Seat {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "row_name", nullable = false, length = 5)
    private String rowName;

    @Column(name = "seat_lable", nullable = false)
    private String seatLabel;

    @Column(name = "seat_type", nullable = false)
    private String seatType;

    @Column(name = "grid_row", nullable = false)
    private Integer gridRow;

    @Column(name = "grid_column", nullable = false)
    private Integer gridColumn;

    @PrePersist
    public void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}

package com.movie.catalog_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "movies")
@Builder
public class Movie {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String title;

    // --- CÁC TRƯỜNG MỚI BỔ SUNG TỪ GIAO DIỆN ---
    @Column(length = 100)
    private String genre; // Thể loại (VD: Hoạt hình, Phiêu lưu)

    @Column(length = 50)
    private String country; // Quốc gia (VD: Nhật Bản)

    @Column(length = 50)
    private String language; // Ngôn ngữ (VD: Lồng Tiếng)

    @Column(name = "age_restriction", length = 10)
    private String ageRestriction; // Phân loại tuổi (VD: P, C13, C18)

    @Column(length = 100)
    private String director; // Đạo diễn

    @Column(length = 255)
    private String actors; // Diễn viên (Chuỗi danh sách)
    // -------------------------------------------

    @Column(nullable = false)
    private Integer duration;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "pose_url", length = 500)
    private String poseUrl;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Column(length = 50)
    private String status; // Trạng thái: ACTIVE, INACTIVE, UPCOMING

    @OneToMany(mappedBy = "movie", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Showtime> showtimes = new ArrayList<>();

    @PrePersist
    public void ensureId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}

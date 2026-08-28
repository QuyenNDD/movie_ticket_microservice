package com.movie.catalog_service.service;

public interface CatalogService {
    Double getSeatPrice(String showtimeId, String seatId);

    Double getSnackPrice(String snackId);

    Double getComboPrice(String comboId);
}

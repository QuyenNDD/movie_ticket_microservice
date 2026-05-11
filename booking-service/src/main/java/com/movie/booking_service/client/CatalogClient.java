package com.movie.booking_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "catalog-service", url = "http://localhost:8081/api/v1/catalog")
public interface CatalogClient {
    @GetMapping("/seats/{id}/price")
    Double getSeatPrice(@PathVariable("id") String seatId);

    @GetMapping("/snacks/{id}/price")
    Double getSnackPrice(@PathVariable("id") String snackId);
}

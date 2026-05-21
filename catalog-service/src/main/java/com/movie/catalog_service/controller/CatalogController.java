package com.movie.catalog_service.controller;

import com.movie.catalog_service.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    @Autowired
    CatalogService catalogService;

    @GetMapping("showtimes/{showtimeId}/seats/{seatId}/price")
    public Double getSeatPrice(
            @PathVariable("showtimeId") String showtimeId,
            @PathVariable("seatId") String seatId){
        return catalogService.getSeatPrice(showtimeId, seatId);
    }

    @GetMapping("/snacks/{snackId}/price")
    public Double getSnackPrice(@PathVariable("snackId") String snackId){
        return catalogService.getSnackPrice(snackId);
    }
}

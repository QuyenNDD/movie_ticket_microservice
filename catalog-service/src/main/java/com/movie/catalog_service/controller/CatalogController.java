package com.movie.catalog_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    @GetMapping("/seats/{id}/price")
    public Double getSeatPrice(@PathVariable("id") String seatId){
        if (seatId.contains("VIP")){
            return 90000.0;
        }
        return 75000.0;
    }

    @GetMapping("/snacks/{id}/price")
    public Double getSnackPrice(@PathVariable("id") String snackId){
        if (snackId.equals("COMBO-1")) return 85000.0;
        return 75000.0;
    }
}

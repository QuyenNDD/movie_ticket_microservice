package com.movie.catalog_service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalog/movies")
public class MovieController {
    @GetMapping()
    public String getAllMovies() {
        return "This is movies";
    }
}

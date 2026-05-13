package com.movie.auth_service.service;

import com.movie.auth_service.dto.AuthRequestDTO;
import com.movie.auth_service.dto.RegisterRequestDTO;
import org.springframework.stereotype.Service;

public interface UserService {
    String register(RegisterRequestDTO requestDTO);

    String login(AuthRequestDTO requestDTO);
}

package com.movie.auth_service.controller;

import com.movie.auth_service.dto.request.LoginRequestDTO;
import com.movie.auth_service.dto.request.RegisterRequestDTO;
import com.movie.auth_service.dto.request.TokenRefreshRequestDTO;
import com.movie.auth_service.dto.response.JwtResponseDTO;
import com.movie.auth_service.dto.response.UserResponseDTO;
import com.movie.auth_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @Autowired
    UserService userService;

    // 1. ĐĂNG KÝ (Giữ nguyên của bạn, thêm @Valid)
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequestDTO requestDTO){
        String message = userService.registerUser(requestDTO);
        return new ResponseEntity<>(message, HttpStatus.CREATED);
    }

    // 2. ĐĂNG NHẬP (Nâng cấp trả về JwtResponseDTO thay vì String)
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@Valid @RequestBody LoginRequestDTO requestDTO) {
        JwtResponseDTO response = userService.authenticateUser(requestDTO);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 3. LÀM MỚI TOKEN (Khi Access Token hết hạn)
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponseDTO> refresh(@Valid @RequestBody TokenRefreshRequestDTO requestDTO) {
        JwtResponseDTO response = userService.refreshAccessToken(requestDTO);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    // 4. LẤY THÔNG TIN CÁ NHÂN PROFILE
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMyProfile() {
        UserResponseDTO response = userService.getMyProfile();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}

package com.movie.auth_service.service;

import com.movie.auth_service.dto.request.ForgotPasswordRequestDTO;
import com.movie.auth_service.dto.request.LoginRequestDTO;
import com.movie.auth_service.dto.request.RegisterRequestDTO;
import com.movie.auth_service.dto.request.ResetPasswordRequestDTO;
import com.movie.auth_service.dto.request.TokenRefreshRequestDTO;
import com.movie.auth_service.dto.response.JwtResponseDTO;
import com.movie.auth_service.dto.response.UserInternalResponseDTO;
import com.movie.auth_service.dto.response.UserResponseDTO;

public interface UserService {
    String registerUser(RegisterRequestDTO registerRequest);
    JwtResponseDTO authenticateUser(LoginRequestDTO loginRequest);
    JwtResponseDTO refreshAccessToken(TokenRefreshRequestDTO refreshRequest);
    void logout(TokenRefreshRequestDTO logoutRequest);
    void forgotPassword(ForgotPasswordRequestDTO forgotPasswordRequest);
    void resetPassword(ResetPasswordRequestDTO resetPasswordRequest);
    UserResponseDTO getMyProfile();
    UserInternalResponseDTO getInternalUserById(String userId);
}

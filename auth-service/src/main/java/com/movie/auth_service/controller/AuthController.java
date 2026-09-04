package com.movie.auth_service.controller;

import com.movie.auth_service.dto.request.ChangePasswordRequestDTO;
import com.movie.auth_service.dto.request.ForgotPasswordRequestDTO;
import com.movie.auth_service.dto.request.LoginRequestDTO;
import com.movie.auth_service.dto.request.RegisterRequestDTO;
import com.movie.auth_service.dto.request.ResetPasswordRequestDTO;
import com.movie.auth_service.dto.request.TokenRefreshRequestDTO;
import com.movie.auth_service.dto.response.JwtResponseDTO;
import com.movie.auth_service.dto.response.UserInternalResponseDTO;
import com.movie.auth_service.dto.response.UserResponseDTO;
import com.movie.auth_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @Autowired
    UserService userService;

    @Value("${app.internal-secret}")
    private String internalSecret;

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

    // 5. ĐĂNG XUẤT (thu hồi refresh token)
    @PostMapping("/logout")
    public ResponseEntity<String> logout(@Valid @RequestBody TokenRefreshRequestDTO requestDTO) {
        userService.logout(requestDTO);
        return new ResponseEntity<>("Đăng xuất thành công!", HttpStatus.OK);
    }

    // 6. QUÊN MẬT KHẨU (gửi email chứa link đặt lại mật khẩu nếu email tồn tại)
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO requestDTO) {
        userService.forgotPassword(requestDTO);
        return new ResponseEntity<>(
                "Nếu email tồn tại trong hệ thống, link đặt lại mật khẩu đã được gửi!",
                HttpStatus.OK
        );
    }

    // 7. ĐẶT LẠI MẬT KHẨU (dùng token nhận được qua email)
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO requestDTO) {
        userService.resetPassword(requestDTO);
        return new ResponseEntity<>("Đặt lại mật khẩu thành công!", HttpStatus.OK);
    }

    // 8. ĐỔI MẬT KHẨU (khi đã đăng nhập, cần mật khẩu hiện tại)
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody ChangePasswordRequestDTO requestDTO) {
        userService.changePassword(requestDTO);
        return new ResponseEntity<>("Đổi mật khẩu thành công!", HttpStatus.OK);
    }

    // 9. XÁC MINH EMAIL (dùng token nhận được qua email khi đăng ký)
    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        userService.verifyEmail(token);
        return new ResponseEntity<>("Xác minh email thành công!", HttpStatus.OK);
    }

    @GetMapping("/internal/users/{userId}")
    public ResponseEntity<UserInternalResponseDTO> getInternalUserById(
            @RequestHeader("X-Internal-Secret") String internalSecretHeader,
            @PathVariable String userId
    ) {
        if (!internalSecret.equals(internalSecretHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UserInternalResponseDTO response = userService.getInternalUserById(userId);
        return ResponseEntity.ok(response);
    }
}

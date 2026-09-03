package com.movie.auth_service.service;

import com.movie.auth_service.dto.request.ChangePasswordRequestDTO;
import com.movie.auth_service.dto.request.ForgotPasswordRequestDTO;
import com.movie.auth_service.dto.request.LoginRequestDTO;
import com.movie.auth_service.dto.request.RegisterRequestDTO;
import com.movie.auth_service.dto.request.ResetPasswordRequestDTO;
import com.movie.auth_service.dto.request.TokenRefreshRequestDTO;
import com.movie.auth_service.dto.response.JwtResponseDTO;
import com.movie.auth_service.dto.response.UserInternalResponseDTO;
import com.movie.auth_service.dto.response.UserResponseDTO;
import com.movie.auth_service.entity.EmailVerificationToken;
import com.movie.auth_service.entity.PasswordResetToken;
import com.movie.auth_service.entity.RefreshToken;
import com.movie.auth_service.entity.User;
import com.movie.auth_service.jwt.JwtUtils;
import com.movie.auth_service.repository.EmailVerificationTokenRepository;
import com.movie.auth_service.repository.PasswordResetTokenRepository;
import com.movie.auth_service.repository.RefreshTokenRepository;
import com.movie.auth_service.repository.UserRepository;
import com.movie.auth_service.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService{
    @Autowired
    UserRepository userRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    RestTemplate restTemplate;

    @Value("${app.internal-secret:dev-internal-secret}")
    private String internalSecret;

    @Value("${app.notification-service-url:http://localhost:8085}")
    private String notificationServiceUrl;

    @Value("${app.password-reset-expiration-ms:900000}")
    private long passwordResetExpirationMs;

    @Value("${app.password-reset-url:http://localhost:5173/reset-password}")
    private String passwordResetUrl;

    @Value("${app.email-verification-expiration-ms:86400000}")
    private long emailVerificationExpirationMs;

    @Value("${app.email-verification-url:http://localhost:5173/verify-email}")
    private String emailVerificationUrl;

    @Override
    public String registerUser(RegisterRequestDTO registerRequest) {
        // 1. Kiểm tra trùng lặp trùng Username hoặc Email
        if (userRepository.existsByUserName(registerRequest.getUserName())) {
            throw new RuntimeException("Lỗi: Tên đăng nhập đã tồn tại!");
        }
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("Lỗi: Email đã được sử dụng!");
        }

        // 2. Tạo đối tượng User mới và mã hóa mật khẩu bằng BCrypt
        User user = new User();
        user.setUserName(registerRequest.getUserName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword())); // BĂM MẬT KHẨU
        user.setRole("USER");
        user.setEmailVerified(false);

        userRepository.save(user);

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setUserId(user.getId());
        verificationToken.setToken(UUID.randomUUID().toString());
        verificationToken.setExpiresAt(LocalDateTime.now().plus(java.time.Duration.ofMillis(emailVerificationExpirationMs)));
        emailVerificationTokenRepository.save(verificationToken);

        String verifyLink = emailVerificationUrl + "?token=" + verificationToken.getToken();
        try {
            sendEmailVerification(user.getEmail(), verifyLink);
        } catch (Exception ex) {
            // Không để lỗi gửi mail chặn đăng ký; user vẫn tạo được, có thể xác minh sau.
            log.error("Gửi email xác minh thất bại: {}", ex.getMessage());
        }

        return "Đăng ký người dùng thành công!";
    }

    @Override
    public JwtResponseDTO authenticateUser(LoginRequestDTO loginRequest) {
        // 1. Gọi Spring Security thực hiện xác thực (gọi ngầm đến CustomUserDetailsService)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUserName(), loginRequest.getPassword())
        );

        // 2. Lưu trạng thái xác thực vào hệ thống
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 3. Lấy thông tin user vừa đăng nhập thành công
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        // 4. Đúc bộ đôi Access Token và Refresh Token
        String roleClean = userDetails.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        String accessToken = jwtUtils.generateJwt(userDetails.getUsername(), userDetails.getId(), roleClean);
        String refreshToken = jwtUtils.generateRefreshJwt(userDetails.getUsername(), userDetails.getId(), roleClean);

        // 5. Lưu Refresh Token vào DB để có thể thu hồi khi đăng xuất
        RefreshToken refreshTokenEntity = new RefreshToken();
        refreshTokenEntity.setUserId(userDetails.getId());
        refreshTokenEntity.setToken(refreshToken);
        refreshTokenEntity.setRevoked(false);
        refreshTokenEntity.setExpiresAt(toLocalDateTime(jwtUtils.getExpirationDateFromJwt(refreshToken)));
        refreshTokenRepository.save(refreshTokenEntity);

        return new JwtResponseDTO(accessToken, refreshToken, userDetails.getId(), userDetails.getUsername(), roleClean);
    }

    @Override
    public JwtResponseDTO refreshAccessToken(TokenRefreshRequestDTO refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();

        // Kiểm tra xem Refresh Token gửi lên có hợp lệ và còn hạn không
        if (refreshToken != null && jwtUtils.validateJwt(refreshToken)) {
            RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                    .orElseThrow(() -> new RuntimeException("Refresh token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!"));

            if (storedToken.isRevoked()) {
                throw new RuntimeException("Refresh token đã bị thu hồi. Vui lòng đăng nhập lại!");
            }

            String username = jwtUtils.getUserNameFromJwt(refreshToken);
            String userId = jwtUtils.getUserIdFromJwt(refreshToken);
            String role = jwtUtils.getRoleFromJwt(refreshToken);

            // Sinh ra một Access Token mới cứng dựa trên thông tin cũ
            String newAccessToken = jwtUtils.generateJwt(username, userId, role);

            return new JwtResponseDTO(newAccessToken, refreshToken, userId, username, role);
        }

        throw new RuntimeException("Refresh token không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại!");
    }

    @Override
    public void logout(TokenRefreshRequestDTO logoutRequest) {
        refreshTokenRepository.findByToken(logoutRequest.getRefreshToken())
                .ifPresent(storedToken -> {
                    storedToken.setRevoked(true);
                    refreshTokenRepository.save(storedToken);
                });
    }

    private LocalDateTime toLocalDateTime(java.util.Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDTO forgotPasswordRequest) {
        // Không tiết lộ email có tồn tại hay không: luôn trả về "thành công" ở Controller.
        userRepository.findByEmail(forgotPasswordRequest.getEmail()).ifPresent(user -> {
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUserId(user.getId());
            resetToken.setToken(UUID.randomUUID().toString());
            resetToken.setUsed(false);
            resetToken.setExpiresAt(LocalDateTime.now().plus(java.time.Duration.ofMillis(passwordResetExpirationMs)));
            passwordResetTokenRepository.save(resetToken);

            String resetLink = passwordResetUrl + "?token=" + resetToken.getToken();
            try {
                sendPasswordResetEmail(user.getEmail(), resetLink);
            } catch (Exception ex) {
                // Không để lỗi gửi mail rò rỉ ra ngoài (tránh lộ email có tồn tại hay không);
                // token vẫn được lưu, người dùng có thể yêu cầu gửi lại nếu cần.
                log.error("Gửi email đặt lại mật khẩu thất bại: {}", ex.getMessage());
            }
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequestDTO resetPasswordRequest) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(resetPasswordRequest.getToken())
                .orElseThrow(() -> new RuntimeException("Token đặt lại mật khẩu không hợp lệ!"));

        if (resetToken.isUsed()) {
            throw new RuntimeException("Token đặt lại mật khẩu đã được sử dụng!");
        }

        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu lại!");
        }

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy thông tin người dùng"));

        user.setPassword(passwordEncoder.encode(resetPasswordRequest.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Thu hồi mọi refresh token đang hoạt động để buộc đăng nhập lại trên mọi thiết bị
        var activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());
        activeTokens.forEach(rt -> rt.setRevoked(true));
        refreshTokenRepository.saveAll(activeTokens);
    }

    private void sendPasswordResetEmail(String toEmail, String resetLink) {
        Map<String, String> body = new HashMap<>();
        body.put("toEmail", toEmail);
        body.put("resetLink", resetLink);

        postInternalNotification("/api/v1/notifications/internal/password-reset", body);
    }

    @Override
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Token xác minh email không hợp lệ!"));

        if (verificationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            emailVerificationTokenRepository.delete(verificationToken);
            throw new RuntimeException("Token xác minh email đã hết hạn. Vui lòng yêu cầu gửi lại!");
        }

        User user = userRepository.findById(verificationToken.getUserId())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy thông tin người dùng"));

        user.setEmailVerified(true);
        userRepository.save(user);

        // Xóa token sau khi dùng để chống dùng lại (thay vì thêm field "used" như PasswordResetToken)
        emailVerificationTokenRepository.delete(verificationToken);
    }

    private void sendEmailVerification(String toEmail, String verifyLink) {
        Map<String, String> body = new HashMap<>();
        body.put("toEmail", toEmail);
        body.put("verifyLink", verifyLink);

        postInternalNotification("/api/v1/notifications/internal/email-verification", body);
    }

    private void postInternalNotification(String path, Map<String, String> body) {
        String url = notificationServiceUrl + path;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, String.class);
    }

    @Override
    public UserResponseDTO getMyProfile() {
        User user = getCurrentAuthenticatedUser();
        return modelMapper.map(user, UserResponseDTO.class);
    }

    @Override
    public void changePassword(ChangePasswordRequestDTO changePasswordRequest) {
        User user = getCurrentAuthenticatedUser();

        if (!passwordEncoder.matches(changePasswordRequest.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không chính xác!");
        }

        user.setPassword(passwordEncoder.encode(changePasswordRequest.getNewPassword()));
        userRepository.save(user);

        // Thu hồi mọi refresh token đang hoạt động để buộc đăng nhập lại trên mọi thiết bị
        var activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId());
        activeTokens.forEach(rt -> rt.setRevoked(true));
        refreshTokenRepository.saveAll(activeTokens);
    }

    // Lấy thông tin user hiện tại từ bộ nhớ SecurityContextHolder (do AuthTokenFilter gán vào trước đó)
    private User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy thông tin người dùng"));
    }

    @Override
    public UserInternalResponseDTO getInternalUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user với id: " + userId));

        return UserInternalResponseDTO.builder()
                .id(user.getId())
                .userName(user.getUserName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}

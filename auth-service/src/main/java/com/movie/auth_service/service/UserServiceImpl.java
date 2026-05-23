package com.movie.auth_service.service;

import com.movie.auth_service.dto.request.LoginRequestDTO;
import com.movie.auth_service.dto.request.RegisterRequestDTO;
import com.movie.auth_service.dto.request.TokenRefreshRequestDTO;
import com.movie.auth_service.dto.response.JwtResponseDTO;
import com.movie.auth_service.dto.response.UserResponseDTO;
import com.movie.auth_service.entity.User;
import com.movie.auth_service.jwt.JwtUtils;
import com.movie.auth_service.repository.UserRepository;
import com.movie.auth_service.security.CustomUserDetails;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService{
    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtUtils jwtUtils;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    ModelMapper modelMapper;

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

        userRepository.save(user);
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
        String refreshToken = jwtUtils.generateRefreshJwt(userDetails.getUsername(), userDetails.getId());

        return new JwtResponseDTO(accessToken, refreshToken, userDetails.getId(), userDetails.getUsername(), roleClean);
    }

    @Override
    public JwtResponseDTO refreshAccessToken(TokenRefreshRequestDTO refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();

        // Kiểm tra xem Refresh Token gửi lên có hợp lệ và còn hạn không
        if (refreshToken != null && jwtUtils.validateJwt(refreshToken)) {
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
    public UserResponseDTO getMyProfile() {
        // Lấy thông tin user hiện tại từ bộ nhớ SecurityContextHolder (do AuthTokenFilter gán vào trước đó)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy thông tin người dùng"));

        return modelMapper.map(user, UserResponseDTO.class);
    }
}

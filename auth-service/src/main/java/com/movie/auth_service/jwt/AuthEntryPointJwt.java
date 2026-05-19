package com.movie.auth_service.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {

        // Thiết lập kiểu trả về là JSON
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Trả về mã lỗi 401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // Gói thông báo lỗi vào 1 cái Map
        final Map<String, Object> body = new HashMap<>();
        body.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        body.put("error", "Unauthorized");
        body.put("message", "Bạn chưa đăng nhập hoặc Token bảo mật không hợp lệ/đã hết hạn!");
        body.put("path", request.getServletPath());

        // Dùng ObjectMapper của thư viện Jackson để biến Map thành chuỗi JSON và bắn về Postman
        final ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), body);
    }
}

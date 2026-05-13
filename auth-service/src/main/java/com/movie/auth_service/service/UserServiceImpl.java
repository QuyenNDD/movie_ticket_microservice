package com.movie.auth_service.service;

import com.movie.auth_service.dto.AuthRequestDTO;
import com.movie.auth_service.dto.RegisterRequestDTO;
import com.movie.auth_service.entity.User;
import com.movie.auth_service.jwt.JwtUtils;
import com.movie.auth_service.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    public String register(RegisterRequestDTO requestDTO) {
        if (userRepository.findByUserName(requestDTO.getUsername()).isPresent()){
            return "Username is existing";
        }
        User newUser = new User();
        newUser.setUserName(requestDTO.getUsername());
        newUser.setEmail(requestDTO.getEmail());

        String hashPassword = passwordEncoder.encode(requestDTO.getPassword());
        newUser.setPassword(hashPassword);

        userRepository.save(newUser);
        return "Register successfully";
    }

    @Override
    public String login(AuthRequestDTO requestDTO) {
        User user = userRepository.findByUserName(requestDTO.getUsername())
                .orElseThrow(() -> new RuntimeException("User not available"));

        if (!passwordEncoder.matches(requestDTO.getPassword(), user.getPassword())) {
            return "Username or password was incorrect";
        }

        String token = jwtUtils.generateJwt(requestDTO.getUsername(), user.getId(), user.getRole());
        return token;
    }
}

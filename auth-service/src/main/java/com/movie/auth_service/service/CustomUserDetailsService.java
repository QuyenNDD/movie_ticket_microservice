package com.movie.auth_service.service;

import com.movie.auth_service.entity.User;
import com.movie.auth_service.repository.UserRepository;
import com.movie.auth_service.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User Not Found with username: " + username));

        // Nếu tìm thấy, bọc nó vào CustomUserDetails để Spring xử lý tiếp
        return CustomUserDetails.build(user);
    }
}

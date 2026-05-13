package com.movie.auth_service.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {
    @Value("${spring.movie.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.movie.jwt.expirationMs}")
    private int jwtExpiration;

    public String generateJwt(String userName, String userId, String role) {
        return Jwts.builder()
                .setSubject(userName)
                .claim("userId", userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime() + jwtExpiration))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUserNameFromJwt(String token){
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJwt(token).getBody().getSubject();
    }

    public boolean validateJwt(String authToken){
        try {
            Jwts.parserBuilder().setSigningKey(key()).build()
                    .parseClaimsJwt(authToken);
            return true;
        }catch (MalformedJwtException e){
            System.out.println("Invalid JWT Token: " + e.getMessage());
        }catch (ExpiredJwtException e) {
            System.out.println("Jwt token is expiration: " + e.getMessage());
        }catch (UnsupportedJwtException e) {
            System.out.println("Jwt token is unsupported: " + e.getMessage());
        }catch (IllegalArgumentException e) {
            System.out.println("Jwt claims string is empty: " + e.getMessage());
        }
        return false;
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}

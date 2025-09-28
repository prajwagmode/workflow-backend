package com.flow.workflow.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // Must be at least 32 chars for HS256
    private final String SECRET = "mysecretkey12345mysecretkey12345";
    // 24 hours validity
    private final long EXPIRATION = 1000 * 60 * 60 * 24;

    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // token with role claim
    public String generateToken(String username, String role) {
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException e) {
            // invalid token
            return null;
        }
    }

    // <--- new helper: safely read the "role" claim (may be null)
    public String extractRole(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setAllowedClockSkewSeconds(60)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            Object roleObj = claims.get("role");
            if (roleObj == null) return null;
            // claim might be stored as String
            return roleObj.toString();
        } catch (JwtException e) {
            return null;
        }
    }

    public boolean validateToken(String token, String username) {
        try {
            String extracted = extractUsername(token);
            return (username != null && username.equals(extracted) && !isTokenExpired(token));
        } catch (ExpiredJwtException e) {
            System.err.println("JWT expired: " + e.getMessage());
        } catch (JwtException e) {
            System.err.println("JWT invalid: " + e.getMessage());
        }
        return false;
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .setAllowedClockSkewSeconds(60)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return expiration.before(new Date());
        } catch (JwtException e) {
            return true; // treat parsing issues as expired/invalid
        }
    }
}

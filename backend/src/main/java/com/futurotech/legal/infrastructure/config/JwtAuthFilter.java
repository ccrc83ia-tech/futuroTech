package com.futurotech.legal.infrastructure.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT del header Authorization y arma el Authentication con las
 * authorities derivadas de los permisos del usuario. Stateless: sin sesión.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                // Los tokens de refresh no autorizan acceso a recursos.
                if (!jwtService.isRefreshToken(claims)) {
                    List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(
                            jwtService.permissions(claims).stream()
                                    .map(p -> new SimpleGrantedAuthority("PERM_" + p))
                                    .toList());
                    String role = jwtService.role(claims);
                    if (role != null) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
                    }
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // Token inválido o expirado: se deja sin autenticar; Spring responde 401/403.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

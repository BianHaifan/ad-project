package com.adproject.common.security;

import com.adproject.auth.application.JwtService;
import com.adproject.user.domain.UserStatus;
import com.adproject.user.infrastructure.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SecurityErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, SecurityErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }
        try {
            AuthenticatedUser parsed = jwtService.parse(authorization.substring(7));
            var user = userRepository.findById(parsed.userId()).orElseThrow();
            if (user.getStatus() != UserStatus.ACTIVE || user.getRole() != parsed.role()) {
                throw new IllegalArgumentException("Inactive or mismatched account");
            }
            var authentication = new UsernamePasswordAuthenticationToken(parsed, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED", "Missing, invalid, or expired credentials");
        }
    }
}

package com.ebook.bookforest.global.security;

import com.ebook.bookforest.global.security.jwt.JwtAccessDeniedHandler;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationEntryPoint;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import com.ebook.bookforest.global.security.oauth.CustomOAuth2UserService;
import com.ebook.bookforest.global.security.oauth.OAuth2AuthenticationFailureHandler;
import com.ebook.bookforest.global.security.oauth.OAuth2AuthenticationSuccessHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler successHandler;
    private final OAuth2AuthenticationFailureHandler failureHandler;

    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final JwtExceptionHandlerFilter jwtExceptionHandlerFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable) // CSRF 비활성화
            .sessionManagement(session -> session.sessionCreationPolicy(
                SessionCreationPolicy.STATELESS)) // 세션 사용 x
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // health check, error
                .requestMatchers(
                    "/actuator/health",
                    "/error"
                ).permitAll()
                // 소셜 로그인
                .requestMatchers(
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/api/reissue",
                    "/api/logout"
                ).permitAll()

                .requestMatchers(
                    HttpMethod.OPTIONS,
                    "/**"
                ).permitAll()

                .requestMatchers(HttpMethod.GET, "/api/books/popular", "/api/books/trending").permitAll()

                .requestMatchers(
                    "/api/platform",
                    "/api/platform/**",
                    "/api/library",
                    "/api/library/**",
                    "/api/book",
                    "/api/book/**",
                    "/api/categories",
                    "/api/categories/**"
                ).permitAll()
                .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                )
                .successHandler(successHandler)
                .failureHandler(failureHandler)
            )

            // 인증 실패 시 로그인 페이지 redirect 대신 401 반환
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(
                    jwtAuthenticationEntryPoint
                )
                .accessDeniedHandler(
                    jwtAccessDeniedHandler
                )
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            )

            .addFilterBefore(
                jwtExceptionHandlerFilter,
                JwtAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
            "http://bookforest.cc",
            "https://bookforest.cc",
            "http://localhost:*",
            "http://127.0.0.1:*",
            "http://192.168.1.19:*"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.*;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private PlayerDetailsService playerDetailsService;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(playerDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        List<String> exact = new ArrayList<>();
        List<String> patterns = new ArrayList<>();
        for (String o : origins) {
            String t = o.trim();
            if (t.contains("*")) patterns.add(t); else exact.add(t);
        }
        patterns.add("https://alex-dyakin.com");
        patterns.add("https://*.alex-dyakin.com");

        if (!exact.isEmpty()) config.setAllowedOrigins(exact);
        if (!patterns.isEmpty()) config.setAllowedOriginPatterns(patterns);
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("X-Auth-Token"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

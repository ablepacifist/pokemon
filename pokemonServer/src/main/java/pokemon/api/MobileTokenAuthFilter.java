package pokemon.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pokemon.data.PokemonDatabase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates the Android app, which presents
 * {@code Authorization: Bearer <token>} because it cannot carry this server's
 * session cookie from the WebView's local origin.
 *
 * The token is the one LexiconServer issued at login. Pokemon shares the same
 * database (it already reads the common PLAYERS table), so validation is a
 * read-only lookup against MOBILE_TOKENS — LexiconServer remains the sole owner
 * of issuing and rotating those tokens.
 *
 * On success this populates the Spring Security context exactly the way
 * {@code AuthController.createSession} does, so every existing controller keeps
 * working unchanged.
 */
@Component
public class MobileTokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired private PokemonDatabase db;
    @Autowired private PlayerDetailsService playerDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (!alreadyAuthenticated()) {
            String rawToken = extractBearerToken(request);
            if (rawToken != null) {
                try {
                    String username = db.findUsernameByMobileToken(sha256(rawToken));
                    if (username != null) {
                        UserDetails ud = playerDetailsService.loadUserByUsername(username);
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                ud, null, ud.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);

                        // Mirror the session the SSO/manual login paths create, so a
                        // client that can hold a cookie keeps working without the header
                        HttpSession session = request.getSession(true);
                        session.setAttribute(
                                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                                SecurityContextHolder.getContext());
                    }
                } catch (Exception e) {
                    // Never fail the request on token trouble — fall through unauthenticated
                    System.err.println("[MobileTokenAuthFilter] " + e.getMessage());
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean alreadyAuthenticated() {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        return existing != null
                && existing.isAuthenticated()
                && !"anonymousUser".equals(existing.getPrincipal());
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null
                || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** Must match LexiconServer's MobileTokenManager hashing. */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

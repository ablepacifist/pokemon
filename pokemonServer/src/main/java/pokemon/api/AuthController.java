package pokemon.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/pokemon/auth")
public class AuthController {

    private static final String LEXICON_INTERNAL = "http://127.0.0.1:36568";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired private AuthenticationManager authManager;
    @Autowired private PlayerDetailsService playerDetailsService;
    @Autowired private PokemonDatabase db;

    // ── Manual login (fallback) ───────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body,
                                   HttpServletRequest req, HttpServletResponse res) {
        try {
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(body.get("username"), body.get("password")));
            return createSession(auth, req);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Invalid credentials");
        }
    }

    // ── SSO: swap a Lexicon SSO token for a Pokemon session ──────────────────

    @PostMapping("/sso")
    public ResponseEntity<?> ssoLogin(@RequestBody Map<String, String> body,
                                      HttpServletRequest req) {
        String token = body.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body("Missing SSO token");
        }
        try {
            // Validate token against LexiconServer (internal server-to-server call)
            String jsonBody = MAPPER.writeValueAsString(Map.of("token", token));
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(LEXICON_INTERNAL + "/api/auth/sso/validate-token"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> httpRes = HTTP.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (httpRes.statusCode() != 200) {
                return ResponseEntity.status(401).body("SSO token invalid or expired");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> ssoResult = MAPPER.readValue(httpRes.body(), Map.class);
            if (!Boolean.TRUE.equals(ssoResult.get("valid"))) {
                return ResponseEntity.status(401).body("SSO token rejected");
            }

            String username = (String) ssoResult.get("username");
            if (username == null) return ResponseEntity.status(401).body("No username in SSO response");

            // Load player from shared PLAYERS table and create a Pokemon session
            org.springframework.security.core.userdetails.UserDetails ud =
                    playerDetailsService.loadUserByUsername(username);
            Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
            return createSession(auth, req);

        } catch (Exception e) {
            System.err.println("[AuthController] SSO error: " + e.getMessage());
            return ResponseEntity.status(500).body("SSO failed: " + e.getMessage());
        }
    }

    // ── Who am I ─────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Not authenticated");
        }
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername()));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok("Logged out");
    }

    // ── Shared helper ─────────────────────────────────────────────────────────

    private ResponseEntity<?> createSession(Authentication auth, HttpServletRequest req) {
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = req.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername()));
    }
}

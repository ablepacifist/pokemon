package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;

import java.util.Map;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class CollectionController {

    @Autowired
    private PokemonDatabase db;

    @GetMapping("/collection")
    public ResponseEntity<?> collection() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            return ResponseEntity.ok(db.getCaughtByPlayer(playerId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/nickname")
    public ResponseEntity<?> nickname(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            long caughtId = ((Number) body.get("caughtId")).longValue();
            String nickname = body.getOrDefault("nickname", "").toString().trim();
            if (nickname.length() > 30) return ResponseEntity.badRequest().body("Nickname too long");
            db.nicknamePokemon(caughtId, playerId, nickname.isEmpty() ? null : nickname);
            return ResponseEntity.ok("Nickname updated");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Returns the list of species IDs the authenticated player has caught at least once. */
    @GetMapping("/caught-species")
    public ResponseEntity<?> caughtSpecies() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            return ResponseEntity.ok(db.getCaughtSpeciesIds(playerId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/items")
    public ResponseEntity<?> items() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            return ResponseEntity.ok(db.getPlayerItems(playerId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

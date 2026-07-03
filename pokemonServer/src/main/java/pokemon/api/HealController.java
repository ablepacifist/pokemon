package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.HealService;

import java.util.Map;

/** Thin pass-through to HealService for using potions / revives. */
@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class HealController {

    @Autowired
    private HealService healService;

    /** Use a healing item on a caught Pokemon. Body: {caughtId, item} */
    @PostMapping("/heal")
    public ResponseEntity<?> heal(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser"))
            return ResponseEntity.status(401).body("Login required");
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            long caughtId = ((Number) body.get("caughtId")).longValue();
            String item = body.getOrDefault("item", "").toString().toUpperCase();
            return ResponseEntity.ok(healService.useItem(playerId, caughtId, item));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

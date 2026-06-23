package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;
import pokemon.logic.LevelingService;

import java.util.Map;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class GrindController {

    @Autowired private PokemonDatabase db;
    @Autowired private LevelingService levelingService;

    /** Grind (sacrifice) a Pokemon → receive EXP Candy based on its level. */
    @PostMapping("/grind")
    public ResponseEntity<?> grind(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long caughtId = ((Number) body.get("caughtId")).longValue();
            Map<String, Object> result = db.grindPokemon(caughtId, playerId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Use EXP candy on a caught Pokemon. May trigger level-up and move learning. */
    @PostMapping("/use-candy")
    public ResponseEntity<?> useCandy(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long   targetId  = ((Number) body.get("targetId")).longValue();
            String candyType = body.getOrDefault("candyType", "").toString().toUpperCase();
            int    amount    = ((Number) body.getOrDefault("amount", 1)).intValue();
            Map<String, Object> result = levelingService.useCandy(playerId, targetId, candyType, amount);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private int getPlayerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser"))
            return -1;
        return ((CustomUserDetails) auth.getPrincipal()).getId();
    }
}

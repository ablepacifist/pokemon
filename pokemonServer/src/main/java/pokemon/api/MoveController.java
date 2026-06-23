package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.LevelingService;
import pokemon.logic.MoveService;

import java.util.Map;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class MoveController {

    @Autowired private MoveService moveService;
    @Autowired private LevelingService levelingService;

    /** Get all moves (up to 4) for a specific caught Pokemon owned by the logged-in player. */
    @GetMapping("/moves/{caughtId}")
    public ResponseEntity<?> getMoves(@PathVariable long caughtId) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            return ResponseEntity.ok(moveService.getMovesForCaughtPokemon(caughtId, playerId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /** Get the full level-up learnset for a species. */
    @GetMapping("/species/{speciesId}/learnset")
    public ResponseEntity<?> getLearnset(@PathVariable int speciesId) {
        try {
            return ResponseEntity.ok(moveService.getLearnsetForSpecies(speciesId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    /** Replace a move in slot 1-4 for a caught Pokemon. Body: {caughtId, moveId, slot} */
    @PostMapping("/moves/replace")
    public ResponseEntity<?> replaceMove(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long caughtId = ((Number) body.get("caughtId")).longValue();
            int moveId    = ((Number) body.get("moveId")).intValue();
            int slot      = ((Number) body.get("slot")).intValue();
            moveService.replaceMove(caughtId, playerId, moveId, slot);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    private int getPlayerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser"))
            return -1;
        return ((CustomUserDetails) auth.getPrincipal()).getId();
    }
}

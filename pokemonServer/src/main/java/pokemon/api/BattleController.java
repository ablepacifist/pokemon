package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.BattleService;

import java.util.Map;

/**
 * Thin pass-through to BattleService — no battle logic lives here.
 */
@RestController
@RequestMapping("/api/pokemon/battle")
@CrossOrigin(origins = "*")
public class BattleController {

    @Autowired
    private BattleService battleService;

    /** Start a wild battle. Body: {spawnId, caughtId, lat, lng} */
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long spawnId  = ((Number) body.get("spawnId")).longValue();
            long caughtId = ((Number) body.get("caughtId")).longValue();
            double lat = body.get("lat") != null ? ((Number) body.get("lat")).doubleValue() : 0;
            double lng = body.get("lng") != null ? ((Number) body.get("lng")).doubleValue() : 0;
            return ResponseEntity.ok(battleService.startBattle(playerId, spawnId, caughtId, lat, lng));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Choose a move. Body: {battleId, moveId} */
    @PostMapping("/turn")
    public ResponseEntity<?> turn(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            int  moveId   = ((Number) body.get("moveId")).intValue();
            return ResponseEntity.ok(battleService.takeTurn(playerId, battleId, moveId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Switch active Pokemon (costs a turn). Body: {battleId, caughtId} */
    @PostMapping("/switch")
    public ResponseEntity<?> switchActive(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            long caughtId = ((Number) body.get("caughtId")).longValue();
            return ResponseEntity.ok(battleService.switchActive(playerId, battleId, caughtId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Attempt to flee. Body: {battleId} */
    @PostMapping("/flee")
    public ResponseEntity<?> flee(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            return ResponseEntity.ok(battleService.flee(playerId, battleId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Current HP-based catch multiplier (read-only). Query: ?battleId= */
    @GetMapping("/catch-bonus")
    public ResponseEntity<?> catchBonus(@RequestParam long battleId) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            return ResponseEntity.ok(Map.of("catchBonus", battleService.getCatchBonus(playerId, battleId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** End/abandon a battle session. Body: {battleId} */
    @PostMapping("/end")
    public ResponseEntity<?> end(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            battleService.endBattle(playerId, battleId);
            return ResponseEntity.ok(Map.of("ended", true));
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

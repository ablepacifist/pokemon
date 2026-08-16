package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.GymBattleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Thin pass-through for gym battles (Milestone 4). */
@RestController
@RequestMapping("/api/pokemon/gym/battle")
@CrossOrigin(origins = "*")
public class GymBattleController {

    @Autowired private GymBattleService gymBattle;

    /** Start a gym battle. Body: {gymId, team: [caughtId, ...]} */
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long gymId = ((Number) body.get("gymId")).longValue();
            List<Long> team = new ArrayList<>();
            Object t = body.get("team");
            if (t instanceof List<?> list) for (Object o : list) team.add(((Number) o).longValue());
            return ResponseEntity.ok(gymBattle.start(playerId, gymId, team));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/turn")
    public ResponseEntity<?> turn(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            int moveId = ((Number) body.get("moveId")).intValue();
            return ResponseEntity.ok(gymBattle.takeTurn(playerId, battleId, moveId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/switch")
    public ResponseEntity<?> switchActive(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            long caughtId = ((Number) body.get("caughtId")).longValue();
            return ResponseEntity.ok(gymBattle.switchActive(playerId, battleId, caughtId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/end")
    public ResponseEntity<?> end(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long battleId = ((Number) body.get("battleId")).longValue();
            gymBattle.end(playerId, battleId);
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

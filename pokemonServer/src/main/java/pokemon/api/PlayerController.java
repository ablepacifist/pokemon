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
public class PlayerController {

    @Autowired
    private PokemonDatabase db;

    @GetMapping("/player/stats")
    public ResponseEntity<?> stats() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser"))
            return ResponseEntity.status(401).body("Login required");
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            String username = auth.getName();
            int[] s = db.getPlayerStats(playerId);
            int xp = s[0], coins = s[1];
            int totalCaught = db.countCaughtByPlayer(playerId);
            int level = computeLevel(xp);
            int prevXp = xpForLevel(level);
            int nextXp = xpForLevel(level + 1);
            return ResponseEntity.ok(Map.of(
                "username", username,
                "level", level,
                "xp", xp,
                "xpProgress", xp - prevXp,
                "xpRequired", nextXp - prevXp,
                "coins", coins,
                "totalCaught", totalCaught
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    public static int computeLevel(int xp) {
        return Math.max(1, (int)(1 + Math.floor(Math.sqrt(xp / 500.0))));
    }

    public static int xpForLevel(int level) {
        int l = level - 1;
        return l * l * 500;
    }
}

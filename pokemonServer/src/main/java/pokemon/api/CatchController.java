package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.CatchService;
import pokemon.object.CaughtPokemon;

import java.util.Map;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class CatchController {

    @Autowired
    private CatchService catchService;

    @PostMapping("/catch")
    public ResponseEntity<?> catchPokemon(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            long spawnId = ((Number) body.get("spawnId")).longValue();
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            String ballType = body.getOrDefault("ballType", "POKEBALL").toString().toUpperCase();

            CaughtPokemon result = catchService.attemptCatch(playerId, spawnId, lat, lng, ballType);
            if (result == null) {
                return ResponseEntity.ok(Map.of("success", false, "message", "It broke free!"));
            }
            return ResponseEntity.ok(Map.of("success", true, "pokemon", result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

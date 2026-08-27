package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.BuddyService;
import pokemon.logic.EggService;
import pokemon.logic.WalkService;

import java.util.Map;

/**
 * Thin pass-through for the walk / eggs / buddy system (M7). No logic here.
 */
@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class WalkController {

    @Autowired private WalkService walkService;
    @Autowired private EggService eggService;
    @Autowired private BuddyService buddyService;

    /** Report a GPS ping; advances distance, egg incubation, and buddy candy. Body: {lat,lng} */
    @PostMapping("/walk")
    public ResponseEntity<?> walk(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            return ResponseEntity.ok(walkService.recordWalk(playerId, lat, lng));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/eggs")
    public ResponseEntity<?> eggs() {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try { return ResponseEntity.ok(eggService.getEggs(playerId)); }
        catch (Exception e) { return ResponseEntity.status(500).body("Error: " + e.getMessage()); }
    }

    /** Start incubating an egg. Body: {eggId} */
    @PostMapping("/eggs/incubate")
    public ResponseEntity<?> incubate(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long eggId = ((Number) body.get("eggId")).longValue();
            eggService.incubate(playerId, eggId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/buddy")
    public ResponseEntity<?> getBuddy() {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            Map<String, Object> b = buddyService.getBuddy(playerId);
            return ResponseEntity.ok(b == null ? Map.of() : b);
        } catch (Exception e) { return ResponseEntity.status(500).body("Error: " + e.getMessage()); }
    }

    /** Set the player's buddy. Body: {caughtId} */
    @PostMapping("/buddy")
    public ResponseEntity<?> setBuddy(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long caughtId = ((Number) body.get("caughtId")).longValue();
            buddyService.setBuddy(playerId, caughtId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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

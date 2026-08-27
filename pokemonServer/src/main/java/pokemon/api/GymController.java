package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.GymService;

import java.util.Map;

/** Thin pass-through for teams + gyms (Milestone 4). */
@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class GymController {

    @Autowired private GymService gymService;

    // ── Teams ───────────────────────────────────────────────────────────────────

    @GetMapping("/team")
    public ResponseEntity<?> getTeam() {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try { return ResponseEntity.ok(Map.of("team", gymService.getTeam(playerId) == null ? "" : gymService.getTeam(playerId))); }
        catch (Exception e) { return ResponseEntity.status(500).body("Error: " + e.getMessage()); }
    }

    @PostMapping("/team")
    public ResponseEntity<?> setTeam(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            gymService.setTeam(playerId, String.valueOf(body.get("team")));
            return ResponseEntity.ok(Map.of("success", true, "team", gymService.getTeam(playerId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    // ── Gyms ────────────────────────────────────────────────────────────────────

    @GetMapping("/gyms/nearby")
    public ResponseEntity<?> nearby(@RequestParam double lat, @RequestParam double lng,
                                    @RequestParam(defaultValue = "500") double radius) {
        try { return ResponseEntity.ok(gymService.nearbyGyms(lat, lng, radius)); }
        catch (Exception e) { return ResponseEntity.status(500).body("Error: " + e.getMessage()); }
    }

    @GetMapping("/gym/{id}")
    public ResponseEntity<?> getGym(@PathVariable long id) {
        try {
            var g = gymService.getGym(id);
            return g != null ? ResponseEntity.ok(g) : ResponseEntity.notFound().build();
        } catch (Exception e) { return ResponseEntity.status(500).body("Error: " + e.getMessage()); }
    }

    @PostMapping("/gym/add")
    public ResponseEntity<?> addGym(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            String name = body.getOrDefault("name", "Gym").toString();
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            return ResponseEntity.ok(gymService.addGym(playerId, name, lat, lng));
        } catch (Exception e) { return ResponseEntity.status(500).body("Error: " + e.getMessage()); }
    }

    @PostMapping("/gym/spin")
    public ResponseEntity<?> spin(@RequestBody Map<String, Object> body) {
        int playerId = getPlayerId();
        if (playerId < 0) return ResponseEntity.status(401).body("Login required");
        try {
            long gymId = ((Number) body.get("gymId")).longValue();
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            return ResponseEntity.ok(gymService.spinGym(playerId, gymId, lat, lng));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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

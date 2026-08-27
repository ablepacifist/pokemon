package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.logic.EvolutionService;

import java.util.Map;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class EvolutionController {

    @Autowired
    private EvolutionService evolutionService;

    private int playerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((CustomUserDetails) auth.getPrincipal()).getId();
    }

    private boolean authed() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser");
    }

    @GetMapping("/{id}/evolution")
    public ResponseEntity<?> checkEvolution(@PathVariable long id) {
        if (!authed()) return ResponseEntity.status(401).body("Login required");
        try {
            return ResponseEntity.ok(evolutionService.checkEvolution(id, playerId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/evolve")
    public ResponseEntity<?> evolve(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (!authed()) return ResponseEntity.status(401).body("Login required");
        try {
            int targetSpeciesId = ((Number) body.get("targetSpeciesId")).intValue();
            return ResponseEntity.ok(evolutionService.evolve(id, playerId(), targetSpeciesId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(400).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

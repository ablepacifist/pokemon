package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;
import pokemon.logic.GeospatialUtils;
import pokemon.logic.SpawnScheduler;
import pokemon.object.PokemonSpawn;

import java.util.List;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class PokemonMapController {

    private static final int MIN_SPAWNS_NEAR_PLAYER = 2;
    private static final double PLAYER_SPAWN_RADIUS_M = 250.0;

    @Autowired private PokemonDatabase db;
    @Autowired private SpawnScheduler spawner;

    /**
     * Returns active spawns within radius of player position.
     * Also records the player's location and triggers on-demand spawns
     * if fewer than MIN_SPAWNS_NEAR_PLAYER are visible.
     */
    @GetMapping("/nearby")
    public ResponseEntity<?> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radius) {
        try {
            // Update player location if authenticated
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !auth.getPrincipal().equals("anonymousUser")) {
                int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
                db.updatePlayerLocation(playerId, lat, lng);
            }

            // Spawn on-demand near the player if the area is sparse
            int nearbyCount = db.countActiveSpawnsNear(lat, lng, PLAYER_SPAWN_RADIUS_M);
            if (nearbyCount < MIN_SPAWNS_NEAR_PLAYER) {
                spawner.spawnNearLocation(lat, lng, PLAYER_SPAWN_RADIUS_M);
            }

            List<PokemonSpawn> all = db.getActiveSpawns();
            List<PokemonSpawn> nearby = all.stream()
                .filter(s -> GeospatialUtils.distanceMeters(lat, lng, s.getLat(), s.getLng()) <= radius)
                .toList();
            return ResponseEntity.ok(nearby);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

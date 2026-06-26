package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;
import pokemon.logic.GeospatialUtils;
import pokemon.object.Pokestop;

import java.util.List;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class PokestopController {

    private static final double SPIN_RADIUS_M = 200.0;
    private static final Random RNG = new Random();

    @Autowired
    private PokemonDatabase db;

    @GetMapping("/pokestops/nearby")
    public ResponseEntity<?> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "500") double radius) {
        try {
            List<Pokestop> stops = db.getAllPokestops().stream()
                .filter(s -> GeospatialUtils.distanceMeters(lat, lng, s.getLat(), s.getLng()) <= radius)
                .toList();
            return ResponseEntity.ok(stops);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/pokestop/spin")
    public ResponseEntity<?> spin(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            long stopId = ((Number) body.get("stopId")).longValue();
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();

            Pokestop stop = db.getPokestopById(stopId);
            if (stop == null) return ResponseEntity.notFound().build();

            double dist = GeospatialUtils.distanceMeters(lat, lng, stop.getLat(), stop.getLng());
            if (dist > SPIN_RADIUS_M) {
                return ResponseEntity.status(409).body("Too far away (" + (int)dist + "m)");
            }
            if (!stop.isCanSpin()) {
                return ResponseEntity.status(409).body("Pokestop is on cooldown");
            }

            // Drop table: Lure Module (~3%), berry (~20%), stone (~5%), regular items otherwise
            // Quantities doubled across the board vs original
            String item; int qty;
            if (RNG.nextInt(30) == 0) {
                item = "LURE_MODULE"; qty = 1;
            } else if (RNG.nextInt(5) == 0) {
                String[] berries = {"RAZZ_BERRY","NANAB_BERRY","PINAP_BERRY"};
                item = berries[RNG.nextInt(berries.length)]; qty = 2 + RNG.nextInt(3);
            } else if (RNG.nextInt(20) == 0) {
                String[] stones = {"THUNDER_STONE","WATER_STONE","FIRE_STONE","LEAF_STONE","MOON_STONE","LINK_CABLE"};
                item = stones[RNG.nextInt(stones.length)]; qty = 2;
            } else {
                String[] pool = {"POKEBALL","POKEBALL","POKEBALL","GREAT_BALL","GREAT_BALL","ULTRA_BALL","POTION","REVIVE"};
                item = pool[RNG.nextInt(pool.length)]; qty = 2 + RNG.nextInt(5);
            }
            db.adjustItem(playerId, item, qty);
            db.spinPokestop(stopId, playerId);
            try { db.addXp(playerId, 50); } catch (Exception ignored) {}
            try { db.addStardust(playerId, 50 + RNG.nextInt(51)); } catch (Exception ignored) {} // 50-100 stardust

            return ResponseEntity.ok(Map.of(
                "item", item,
                "quantity", qty,
                "message", "Got " + qty + "× " + item.replace("_", " ") + "!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/pokestop/add")
    public ResponseEntity<?> addStop(@RequestBody Map<String, Object> body) {
        try {
            String name = body.getOrDefault("name", "Pokestop").toString();
            double lat = ((Number) body.get("lat")).doubleValue();
            double lng = ((Number) body.get("lng")).doubleValue();
            String biome = GeospatialUtils.detectBiome(lat, lng);
            db.addPokestop(name, lat, lng, biome);
            return ResponseEntity.ok("Pokestop added: " + name + " (biome: " + biome + ")");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/pokestop/lure")
    public ResponseEntity<?> lure(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser")) {
            return ResponseEntity.status(401).body("Login required");
        }
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            long stopId = ((Number) body.get("stopId")).longValue();
            int count = db.getItemCount(playerId, "LURE_MODULE");
            if (count <= 0) return ResponseEntity.status(409).body("No Lure Module in inventory");
            db.adjustItem(playerId, "LURE_MODULE", -1);
            db.lurePokestop(stopId);
            return ResponseEntity.ok(Map.of("message", "Lure Module activated! Pokémon will swarm for 30 minutes."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

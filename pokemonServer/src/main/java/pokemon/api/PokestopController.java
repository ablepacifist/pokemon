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

            // Give random items; coins come from battles/gyms only
            String[] pool = {"POKEBALL","POKEBALL","POKEBALL","GREAT_BALL","GREAT_BALL","ULTRA_BALL","POTION","REVIVE"};
            String item = pool[RNG.nextInt(pool.length)];
            int qty = 1 + RNG.nextInt(3);
            db.adjustItem(playerId, item, qty);
            db.spinPokestop(stopId, playerId);
            try { db.addXp(playerId, 50); } catch (Exception ignored) {}

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
            db.addPokestop(name, lat, lng);
            return ResponseEntity.ok("Pokestop added: " + name);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

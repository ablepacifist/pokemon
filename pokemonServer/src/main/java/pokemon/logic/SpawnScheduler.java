package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pokemon.data.PokemonDatabase;
import pokemon.object.PokemonSpecies;
import pokemon.object.Pokestop;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Component
public class SpawnScheduler {

    private static final int SPAWN_RADIUS_STOP_M = 300;
    private static final int SPAWN_RADIUS_PLAYER_M = 400;
    private static final int SPAWN_DURATION_MIN = 30;
    private static final double SPAWN_CHANCE_PER_STOP = 0.6;
    private static final Random RNG = new Random();

    // Rarity thresholds: roll 1-100 → rarity tier
    private static final int[] RARITY_THRESHOLDS = {0, 60, 85, 95, 99, 100};

    @Autowired
    private PokemonDatabase db;

    @Scheduled(fixedRate = 600_000) // every 10 minutes
    public void runSpawnCycle() {
        try {
            db.deleteExpiredSpawns();
            List<PokemonSpecies> species = db.getAllSpecies();
            if (species.isEmpty()) return;

            int spawned = 0;

            // Spawn near Pokestops
            List<Pokestop> stops = db.getAllPokestops();
            for (Pokestop stop : stops) {
                if (RNG.nextDouble() > SPAWN_CHANCE_PER_STOP) continue;
                int count = 1 + RNG.nextInt(2);
                for (int i = 0; i < count; i++) {
                    spawnAt(species, stop.getLat(), stop.getLng(), SPAWN_RADIUS_STOP_M);
                    spawned++;
                }
            }

            // Spawn near players who were active in the last 30 minutes
            List<double[]> playerLocs = db.getRecentPlayerLocations();
            for (double[] pos : playerLocs) {
                int count = 2 + RNG.nextInt(2); // 2-3 per player per cycle
                for (int i = 0; i < count; i++) {
                    spawnAt(species, pos[0], pos[1], SPAWN_RADIUS_PLAYER_M);
                    spawned++;
                }
            }

            if (spawned > 0) System.out.println("[SpawnScheduler] Spawned " + spawned + " Pokemon.");
            if (stops.isEmpty() && playerLocs.isEmpty()) {
                System.out.println("[SpawnScheduler] No stops or active players — nothing to spawn around.");
            }
        } catch (Exception e) {
            System.err.println("[SpawnScheduler] Error: " + e.getMessage());
        }
    }

    /**
     * Called on-demand from PokemonMapController when a player's area is sparse.
     * Spawns 1-2 Pokemon near the given position immediately.
     */
    public void spawnNearLocation(double lat, double lng, double radiusM) {
        try {
            List<PokemonSpecies> species = db.getAllSpecies();
            if (species.isEmpty()) return;
            int count = 1 + RNG.nextInt(2);
            for (int i = 0; i < count; i++) spawnAt(species, lat, lng, radiusM);
        } catch (Exception e) {
            System.err.println("[SpawnScheduler] On-demand spawn error: " + e.getMessage());
        }
    }

    private void spawnAt(List<PokemonSpecies> species, double lat, double lng, double radius) throws Exception {
        PokemonSpecies chosen = pickWeightedSpecies(species);
        double[] pos = GeospatialUtils.randomOffset(lat, lng, radius);
        Instant expires = Instant.now().plusSeconds(SPAWN_DURATION_MIN * 60L);
        db.insertSpawn(chosen.getId(), pos[0], pos[1], expires);
    }

    private PokemonSpecies pickWeightedSpecies(List<PokemonSpecies> all) {
        int roll = 1 + RNG.nextInt(100);
        int targetRarity = 1;
        for (int r = 5; r >= 1; r--) {
            if (roll > RARITY_THRESHOLDS[r - 1]) { targetRarity = r; break; }
        }
        final int finalRarity = targetRarity;
        List<PokemonSpecies> pool = all.stream()
            .filter(s -> s.getRarity() == finalRarity).toList();
        if (pool.isEmpty()) pool = all;
        return pool.get(RNG.nextInt(pool.size()));
    }
}

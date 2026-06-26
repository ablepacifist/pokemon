package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pokemon.data.PokemonDatabase;
import pokemon.object.PokemonSpecies;
import pokemon.object.Pokestop;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

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
                int base = 1 + RNG.nextInt(2);
                int count = stop.isLured() ? base + 2 : base; // lured stops get 2 extra spawns
                for (int i = 0; i < count; i++) {
                    spawnAt(species, stop.getLat(), stop.getLng(), SPAWN_RADIUS_STOP_M, stop.getBiome());
                    spawned++;
                }
            }

            // Spawn near players who were active in the last 30 minutes
            // Biome is detected from the player's actual location — independent of any pokestop
            List<double[]> playerLocs = db.getRecentPlayerLocations();
            for (double[] pos : playerLocs) {
                String playerBiome = GeospatialUtils.detectBiome(pos[0], pos[1]);
                int count = 2 + RNG.nextInt(2); // 2-3 per player per cycle
                for (int i = 0; i < count; i++) {
                    spawnAt(species, pos[0], pos[1], SPAWN_RADIUS_PLAYER_M, playerBiome);
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
            String biome = GeospatialUtils.detectBiome(lat, lng);
            int count = 1 + RNG.nextInt(2);
            for (int i = 0; i < count; i++) spawnAt(species, lat, lng, radiusM, biome);
        } catch (Exception e) {
            System.err.println("[SpawnScheduler] On-demand spawn error: " + e.getMessage());
        }
    }

    private void spawnAt(List<PokemonSpecies> species, double lat, double lng, double radius, String biome) throws Exception {
        PokemonSpecies chosen = pickBiomeWeightedSpecies(species, biome);
        double[] pos = GeospatialUtils.randomOffset(lat, lng, radius);
        Instant expires = Instant.now().plusSeconds(SPAWN_DURATION_MIN * 60L);
        db.insertSpawn(chosen.getId(), pos[0], pos[1], expires);
    }

    private PokemonSpecies pickBiomeWeightedSpecies(List<PokemonSpecies> all, String biome) {
        int roll = 1 + RNG.nextInt(100);
        int targetRarity = 1;
        for (int r = 5; r >= 1; r--) {
            if (roll > RARITY_THRESHOLDS[r - 1]) { targetRarity = r; break; }
        }
        final int finalRarity = targetRarity;
        List<PokemonSpecies> pool = all.stream()
            .filter(s -> s.getRarity() == finalRarity).toList();
        if (pool.isEmpty()) pool = all;

        Set<String> favored = switch (biome == null ? "NORMAL" : biome) {
            case "WATER" -> Set.of("Water", "Ice", "Electric");
            case "GRASS" -> Set.of("Grass", "Bug", "Normal");
            default      -> Set.of();
        };
        if (favored.isEmpty()) return pool.get(RNG.nextInt(pool.size()));

        List<PokemonSpecies> weighted = new ArrayList<>();
        for (PokemonSpecies sp : pool) {
            int w = (favored.contains(sp.getType1()) || favored.contains(sp.getType2())) ? 3 : 1;
            for (int i = 0; i < w; i++) weighted.add(sp);
        }
        return weighted.isEmpty() ? pool.get(RNG.nextInt(pool.size())) : weighted.get(RNG.nextInt(weighted.size()));
    }
}

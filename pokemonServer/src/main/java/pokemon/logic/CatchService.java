package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.CaughtPokemon;
import pokemon.object.PokemonSpecies;
import pokemon.object.PokemonSpawn;

import java.util.Map;
import java.util.Random;

@Service
public class CatchService {

    private static final double[] BASE_CATCH_RATE = {0, 0.80, 0.60, 0.35, 0.12, 0.04};
    private static final Map<String, Double> BALL_MULTIPLIER = Map.of(
        "POKEBALL", 1.0,
        "GREAT_BALL", 1.5,
        "ULTRA_BALL", 2.0
    );
    private static final double MAX_CATCH_DISTANCE_M = 200.0;
    private static final Random RNG = new Random();

    // Wild Pokemon level ranges per rarity: {minLevel, randomRange}
    // e.g. rarity 1 → level = 1 + RNG(0..11) → Lv 1–12
    private static final int[][] LEVEL_RANGE = {
        {0,  0},  // unused
        {1, 11},  // rarity 1: Lv 1–12
        {3, 13},  // rarity 2: Lv 3–16
        {8, 13},  // rarity 3: Lv 8–21
        {15,19},  // rarity 4: Lv 15–34
        {30,20},  // rarity 5: Lv 30–50
    };

    @Autowired
    private PokemonDatabase db;

    @Autowired
    private MoveService moveService;

    @Autowired
    private BattleService battleService;

    /**
     * Returns the caught Pokemon if successful, null if failed, throws if invalid.
     */
    public CaughtPokemon attemptCatch(int playerId, long spawnId, double playerLat,
                                      double playerLng, String ballType, String berry) throws Exception {
        return attemptCatch(playerId, spawnId, playerLat, playerLng, ballType, berry, null);
    }

    /**
     * Catch attempt, optionally coming from a battle (battleId). A battle weakens
     * the wild Pokemon, so its remaining HP grants a catch-rate bonus.
     */
    public CaughtPokemon attemptCatch(int playerId, long spawnId, double playerLat,
                                      double playerLng, String ballType, String berry,
                                      Long battleId) throws Exception {
        PokemonSpawn spawn = db.getSpawnById(spawnId);
        if (spawn == null) throw new IllegalArgumentException("Spawn not found");
        if (spawn.getCaughtByPlayer() != null) throw new IllegalStateException("Already caught");

        double dist = GeospatialUtils.distanceMeters(playerLat, playerLng, spawn.getLat(), spawn.getLng());
        if (dist > MAX_CATCH_DISTANCE_M) throw new IllegalStateException("Too far away (" + (int)dist + "m)");

        int boxCount = db.countCaughtByPlayer(playerId);
        if (boxCount >= 300) throw new IllegalStateException("Pokemon Box is full (300/300). Transfer some Pokemon first.");

        int ballCount = db.getItemCount(playerId, ballType);
        if (ballCount <= 0) throw new IllegalStateException("No " + ballType + " remaining");
        db.adjustItem(playerId, ballType, -1);

        PokemonSpecies species = db.getSpeciesById(spawn.getSpeciesId());
        double rate = BASE_CATCH_RATE[Math.min(species.getRarity(), 5)]
                    * BALL_MULTIPLIER.getOrDefault(ballType, 1.0);

        // Battle-weakened Pokemon are easier to catch (HP-based multiplier from the session).
        if (battleId != null) {
            double battleBonus = battleService.getCatchBonus(playerId, battleId);
            rate = Math.min(rate * battleBonus, 1.0);
        }

        boolean doubleCandy = false;
        if (berry != null && !berry.isBlank()) {
            int berryCount = db.getItemCount(playerId, berry);
            if (berryCount > 0) {
                db.adjustItem(playerId, berry, -1);
                if ("RAZZ_BERRY".equals(berry))       rate = Math.min(rate * 1.5, 1.0);
                else if ("PINAP_BERRY".equals(berry)) doubleCandy = true;
                // NANAB_BERRY: consumed, no catch-rate bonus (would prevent dodge in full implementation)
            }
        }

        if (RNG.nextDouble() > rate) {
            try { db.addXp(playerId, 10); } catch (Exception ignored) {}
            return null; // missed
        }

        db.markSpawnCaught(spawnId, playerId);

        // Catch came from a battle → close out that battle session.
        if (battleId != null) {
            try { battleService.endBattle(playerId, battleId); } catch (Exception ignored) {}
        }

        // Level comes from the spawn (set at spawn time) so it matches what the
        // map showed and any battle used. Fall back to a rarity roll for old spawns.
        int level = spawn.getLevel();
        if (level <= 0) {
            int rarity = Math.min(species.getRarity(), 5);
            int[] range = LEVEL_RANGE[rarity];
            level = Math.max(1, Math.min(100, range[0] + RNG.nextInt(range[1] + 1)));
        }

        // Compute stats: baseStat * (level + 50) / 100 with ±15% IV variance
        double iv = 0.85 + RNG.nextDouble() * 0.30;
        int hp    = statAtLevel(species.getBaseHp(),      level, iv) + level;
        int atk   = statAtLevel(species.getBaseAttack(),  level, iv);
        int def   = statAtLevel(species.getBaseDefense(), level, iv);
        int spAtk = statAtLevel(species.getBaseSpAtk(),   level, iv);
        int spDef = statAtLevel(species.getBaseSpDef(),   level, iv);
        int speed = statAtLevel(species.getBaseSpeed(),   level, iv);

        CaughtPokemon caught = new CaughtPokemon();
        caught.setPlayerId(playerId);
        caught.setSpeciesId(species.getId());
        caught.setSpeciesName(species.getName());
        caught.setSpriteKey(species.getSpriteKey());
        caught.setType1(species.getType1());
        caught.setType2(species.getType2());
        caught.setPokemonLevel(level);
        caught.setHp(hp);
        caught.setAttack(atk);
        caught.setDefense(def);
        caught.setSpAtk(spAtk);
        caught.setSpDef(spDef);
        caught.setSpeed(speed);
        caught.setCaughtLat(playerLat);
        caught.setCaughtLng(playerLng);
        // Store IV and initial EXP (pokemon starts exactly at its level with 0 progress toward next)
        caught.setIv(iv);
        caught.setExp(pokemon.data.PokemonDatabase.expForLevel(level));

        long id = db.insertCaughtPokemon(caught);
        caught.setId(id);

        // Assign the moves this Pokemon knows at its catch level
        try { moveService.assignInitialMoves(id, species.getId(), level); } catch (Exception ignored) {}

        // XP + generic candy for catching
        try { db.addXp(playerId, species.getRarity() * 100); } catch (Exception ignored) {}
        try { db.adjustItem(playerId, "CANDY_XS", doubleCandy ? 6 : 3); } catch (Exception ignored) {}

        return caught;
    }

    /** baseStat * (level + 50) / 100 * iv, minimum 1 */
    private static int statAtLevel(int base, int level, double iv) {
        return Math.max(1, (int)(base * (level + 50) / 100.0 * iv));
    }
}

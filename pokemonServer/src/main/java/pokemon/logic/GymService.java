package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Gyms (Milestone 4). Single-player adaptation: a new gym is controlled by a
 * RIVAL team and defended by generated NPC Pokemon, so there is always a gym to
 * battle. Beating all defenders flips the gym to the player's team (handled by
 * GymBattleService); the player can then post their own defenders.
 */
@Service
public class GymService {

    public static final String[] TEAMS = { "VALOR", "MYSTIC", "INSTINCT" };
    private static final double SPIN_RADIUS_M = 200.0;
    private static final int MAX_DEFENDERS = 6;
    private static final Random RNG = new Random();

    @Autowired private PokemonDatabase db;

    // ── Teams ───────────────────────────────────────────────────────────────────

    public String getTeam(int playerId) throws Exception { return db.getTeam(playerId); }

    /** Set the player's team (once chosen it can be changed, but costs nothing here). */
    public void setTeam(int playerId, String team) throws Exception {
        String t = team == null ? "" : team.trim().toUpperCase();
        boolean valid = false;
        for (String x : TEAMS) if (x.equals(t)) valid = true;
        if (!valid) throw new IllegalArgumentException("Team must be VALOR, MYSTIC, or INSTINCT");
        db.setTeam(playerId, t);
    }

    // ── Gyms on the map ─────────────────────────────────────────────────────────

    public List<Gym> nearbyGyms(double lat, double lng, double radius) throws Exception {
        List<Gym> out = new ArrayList<>();
        for (Gym g : db.getAllGyms())
            if (GeospatialUtils.distanceMeters(lat, lng, g.getLat(), g.getLng()) <= radius) out.add(g);
        return out;
    }

    public Gym getGym(long gymId) throws Exception { return db.getGymById(gymId); }

    /** Create a gym controlled by a rival NPC team with a few generated defenders. */
    public Gym addGym(int playerId, String name, double lat, double lng) throws Exception {
        String playerTeam = db.getTeam(playerId);
        String rival = rivalTeam(playerTeam);
        long gymId = db.addGym(name == null || name.isBlank() ? "Gym" : name, lat, lng, rival);
        int count = 2 + RNG.nextInt(2); // 2-3 NPC defenders
        generateNpcDefenders(gymId, rival, count);
        return db.getGymById(gymId);
    }

    private String rivalTeam(String playerTeam) {
        List<String> pool = new ArrayList<>();
        for (String t : TEAMS) if (!t.equals(playerTeam)) pool.add(t);
        return pool.get(RNG.nextInt(pool.size()));
    }

    /** Populate a gym with NPC defenders (used on creation and when a gym reverts to a rival). */
    public void generateNpcDefenders(long gymId, String team, int count) throws Exception {
        List<PokemonSpecies> species = db.getAllSpecies();
        if (species.isEmpty()) return;
        for (int slot = 1; slot <= Math.min(count, MAX_DEFENDERS); slot++) {
            db.insertDefender(buildNpcDefender(gymId, slot, species));
        }
    }

    private GymDefender buildNpcDefender(long gymId, int slot, List<PokemonSpecies> species) throws Exception {
        PokemonSpecies sp = species.get(RNG.nextInt(species.size()));
        int level = 12 + RNG.nextInt(19);         // Lv 12-30
        double iv = 0.90 + RNG.nextDouble() * 0.20;

        GymDefender d = new GymDefender();
        d.setGymId(gymId);
        d.setSlot(slot);
        d.setMotivation(100);
        d.setSpeciesId(sp.getId());
        d.setName(sp.getName());
        d.setSpriteKey(sp.getSpriteKey());
        d.setType1(sp.getType1());
        d.setType2(sp.getType2());
        d.setLevel(level);
        d.setHp(statAtLevel(sp.getBaseHp(), level, iv) + level + 10);
        d.setAttack(statAtLevel(sp.getBaseAttack(), level, iv));
        d.setDefense(statAtLevel(sp.getBaseDefense(), level, iv));
        d.setSpAtk(statAtLevel(sp.getBaseSpAtk(), level, iv));
        d.setSpDef(statAtLevel(sp.getBaseSpDef(), level, iv));
        d.setSpeed(statAtLevel(sp.getBaseSpeed(), level, iv));
        d.setMoveIds(pickMoves(sp.getId(), level));
        return d;
    }

    /** Last 4 level-up moves the species knows by this level; falls back to Tackle/Growl. */
    public int[] pickMoves(int speciesId, int level) throws Exception {
        List<PokemonMove> learn = db.getLearnsetUpToLevel(speciesId, level);
        int[] moves = { 0, 0, 0, 0 };
        int n = Math.min(4, learn.size());
        for (int i = 0; i < n; i++) moves[i] = learn.get(learn.size() - n + i).getId();
        if (n == 0) { moves[0] = 33; moves[1] = 45; } // Tackle, Growl
        return moves;
    }

    // ── Spinning a gym disc (like a PokeStop) ───────────────────────────────────

    public Map<String, Object> spinGym(int playerId, long gymId, double lat, double lng) throws Exception {
        Gym gym = db.getGymById(gymId);
        if (gym == null) throw new IllegalArgumentException("Gym not found");
        double dist = GeospatialUtils.distanceMeters(lat, lng, gym.getLat(), gym.getLng());
        if (dist > SPIN_RADIUS_M) throw new IllegalStateException("Too far away (" + (int) dist + "m)");
        if (!gym.isCanSpin()) throw new IllegalStateException("Gym disc is on cooldown");

        String[] pool = { "POKEBALL", "POKEBALL", "GREAT_BALL", "POTION", "RAZZ_BERRY", "REVIVE" };
        String item = pool[RNG.nextInt(pool.length)];
        int qty = 1 + RNG.nextInt(3);
        db.adjustItem(playerId, item, qty);
        db.spinGym(gymId, playerId);
        try { db.addXp(playerId, 60); } catch (Exception ignored) {}
        return Map.of("item", item, "quantity", qty,
            "message", "Got " + qty + "× " + item.replace("_", " ") + " from the gym!");
    }

    private static int statAtLevel(int base, int level, double iv) {
        return Math.max(1, (int) (base * (level + 50) / 100.0 * iv));
    }
}

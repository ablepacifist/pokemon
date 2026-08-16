package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Turn-based wild battle engine (Milestone 3). Battles are server-authoritative
 * and held in memory — short-lived, no DB persistence. ALL battle logic lives
 * here; the controller is a thin pass-through.
 *
 * Damage uses the standard Pokemon formula with a physical/special split, STAB,
 * the 18-type chart, and a 0.85–1.0 random factor. Status conditions (sleep,
 * paralysis, poison, burn, freeze, confusion) and stat-stage moves are applied
 * via a curated effect registry keyed by Gen 1 move id.
 */
@Service
public class BattleService {

    private static final double MAX_BATTLE_DISTANCE_M = 200.0;
    private static final long   SESSION_TTL_MS = 15 * 60 * 1000L;
    private static final int    MAX_TURNS_BEFORE_FLEE_RISES = 1;
    private static final Random RNG = new Random();

    // Wild level ranges per rarity (mirrors CatchService): {minLevel, randomRange}
    private static final int[][] LEVEL_RANGE = {
        {0, 0}, {1, 11}, {3, 13}, {8, 13}, {15, 19}, {30, 20}
    };

    private final ConcurrentHashMap<Long, BattleState> sessions = new ConcurrentHashMap<>();
    private final AtomicLong battleIdSeq = new AtomicLong(1);

    @Autowired
    private PokemonDatabase db;

    @Autowired
    private CombatEngine combat;

    // ── Session lifecycle ──────────────────────────────────────────────────────

    public BattleState startBattle(int playerId, long spawnId, long caughtId,
                                    double lat, double lng) throws Exception {
        purgeStale();

        PokemonSpawn spawn = db.getSpawnById(spawnId);
        if (spawn == null) throw new IllegalArgumentException("Spawn not found");
        if (spawn.getCaughtByPlayer() != null) throw new IllegalStateException("Already caught");

        double dist = GeospatialUtils.distanceMeters(lat, lng, spawn.getLat(), spawn.getLng());
        if (dist > MAX_BATTLE_DISTANCE_M) throw new IllegalStateException("Too far away (" + (int) dist + "m)");

        BattleCombatant wild   = buildWild(spawn);
        BattleCombatant player = buildPlayer(caughtId, playerId);
        if (player == null) throw new IllegalArgumentException("Your Pokemon was not found");
        if (player.isFainted()) throw new IllegalStateException(player.getName() + " has fainted — revive it first");

        BattleState state = new BattleState();
        state.setBattleId(battleIdSeq.getAndIncrement());
        state.setPlayerId(playerId);
        state.setSpawnId(spawnId);
        state.setPlayerLat(lat);
        state.setPlayerLng(lng);
        state.setWild(wild);
        state.setPlayer(player);
        state.addLog("A wild " + wild.getName() + " (Lv." + wild.getLevel() + ") appeared!");
        state.addLog("Go, " + player.getName() + "!");
        sessions.put(state.getBattleId(), state);
        return state;
    }

    public BattleState getBattle(int playerId, long battleId) {
        BattleState s = sessions.get(battleId);
        if (s == null) throw new IllegalArgumentException("Battle not found or already ended");
        if (s.getPlayerId() != playerId) throw new IllegalStateException("Not your battle");
        return s;
    }

    public void endBattle(int playerId, long battleId) {
        BattleState s = sessions.get(battleId);
        if (s != null && s.getPlayerId() == playerId) {
            persistPlayerHp(s);
            sessions.remove(battleId);
        }
    }

    /** Save the active player Pokemon's current battle HP back to the database. */
    private void persistPlayerHp(BattleState s) {
        try {
            if (s.getPlayer() != null)
                db.updateCurrentHp(s.getPlayer().getRefId(), s.getPlayerId(), s.getPlayer().getCurHp());
        } catch (Exception e) {
            System.err.println("[BattleService] HP persist error: " + e.getMessage());
        }
    }

    /** Read the current HP-based catch multiplier without ending the session. */
    public double getCatchBonus(int playerId, long battleId) {
        BattleState s = sessions.get(battleId);
        if (s == null || s.getPlayerId() != playerId) return 1.0;
        if (s.getWild() != null && s.getWild().isFainted())
            throw new IllegalStateException("That Pokemon fainted — it can't be caught");
        return s.getCatchBonus();
    }

    // ── Combatant construction ─────────────────────────────────────────────────

    private BattleCombatant buildWild(PokemonSpawn spawn) throws Exception {
        PokemonSpecies sp = db.getSpeciesById(spawn.getSpeciesId());
        if (sp == null) throw new IllegalStateException("Species data missing");

        // Level comes from the spawn (shown on the map). IV is deterministic from the
        // spawn id so re-engaging the same spawn is consistent.
        Random seeded = new Random(spawn.getId() * 2654435761L);
        int rarity = Math.min(Math.max(sp.getRarity(), 1), 5);
        int level = spawn.getLevel();
        if (level <= 0) {
            int[] range = LEVEL_RANGE[rarity];
            level = Math.max(1, Math.min(100, range[0] + seeded.nextInt(range[1] + 1)));
        }
        double iv = 0.90 + seeded.nextDouble() * 0.20;

        BattleCombatant c = new BattleCombatant();
        c.setRefId(sp.getId());
        c.setSpeciesId(sp.getId());
        c.setName(sp.getName());
        c.setSpriteKey(sp.getSpriteKey());
        c.setType1(sp.getType1());
        c.setType2(sp.getType2());
        c.setLevel(level);
        int maxHp = statAtLevel(sp.getBaseHp(), level, iv) + level + 10;
        c.setMaxHp(maxHp);
        c.setCurHp(maxHp);
        c.setAttack(statAtLevel(sp.getBaseAttack(), level, iv));
        c.setDefense(statAtLevel(sp.getBaseDefense(), level, iv));
        c.setSpAtk(statAtLevel(sp.getBaseSpAtk(), level, iv));
        c.setSpDef(statAtLevel(sp.getBaseSpDef(), level, iv));
        c.setSpeed(statAtLevel(sp.getBaseSpeed(), level, iv));
        c.setMoves(wildMoveset(sp.getId(), level));
        return c;
    }

    private List<PokemonMove> wildMoveset(int speciesId, int level) throws Exception {
        List<PokemonMove> learn = db.getLearnsetUpToLevel(speciesId, level);
        List<PokemonMove> moves = new ArrayList<>();
        int start = Math.max(0, learn.size() - 4);
        for (int i = start; i < learn.size(); i++) moves.add(learn.get(i));
        if (moves.isEmpty()) {
            moves.add(new PokemonMove(33, "Tackle", "Normal", "Physical", 40, 100, 35));
            moves.add(new PokemonMove(45, "Growl", "Normal", "Status", 0, 100, 40));
        }
        return moves;
    }

    private BattleCombatant buildPlayer(long caughtId, int playerId) throws Exception {
        CaughtPokemon p = db.getCaughtById(caughtId, playerId);
        if (p == null) return null;

        BattleCombatant c = new BattleCombatant();
        c.setRefId(p.getId());
        c.setSpeciesId(p.getSpeciesId());
        c.setName(p.getNickname() != null && !p.getNickname().isBlank() ? p.getNickname() : p.getSpeciesName());
        c.setSpriteKey(p.getSpriteKey());
        c.setType1(p.getType1());
        c.setType2(p.getType2());
        c.setLevel(p.getPokemonLevel());
        c.setMaxHp(p.getHp());
        // Battle HP persists between battles — start from stored current HP.
        c.setCurHp(p.getCurrentHp() > 0 ? Math.min(p.getCurrentHp(), p.getHp()) : 0);
        c.setAttack(p.getAttack());
        c.setDefense(p.getDefense());
        c.setSpAtk(p.getSpAtk());
        c.setSpDef(p.getSpDef());
        c.setSpeed(p.getSpeed());
        List<PokemonMove> moves = db.getCaughtPokemonMoves(caughtId, playerId);
        if (moves.isEmpty()) moves.add(new PokemonMove(33, "Tackle", "Normal", "Physical", 40, 100, 35));
        c.setMoves(moves);
        return c;
    }

    // ── Player actions ─────────────────────────────────────────────────────────

    /** Player selects a move; resolves a full turn (both sides act, ordered by speed). */
    public BattleState takeTurn(int playerId, long battleId, int moveId) throws Exception {
        BattleState s = getBattle(playerId, battleId);
        if (s.isOver()) throw new IllegalStateException("Battle is already over");
        s.resetLog();
        s.touch();

        PokemonMove playerMove = combat.findMove(s.getPlayer(), moveId);
        if (playerMove == null) throw new IllegalArgumentException("That Pokemon doesn't know that move");
        PokemonMove wildMove = combat.chooseAiMove(s.getWild(), s.getPlayer());

        combat.resolveTurn(s.getPlayer(), playerMove, s.getWild(), wildMove, s.getLog());
        combat.endOfTurnTicks(s.getPlayer(), s.getWild(), s.getLog());
        resolveOutcome(s);
        s.incrementTurn();
        persistPlayerHp(s);
        return s;
    }

    /** Switch the active player Pokemon (costs a turn — the wild gets a free hit). */
    public BattleState switchActive(int playerId, long battleId, long newCaughtId) throws Exception {
        BattleState s = getBattle(playerId, battleId);
        if (s.isOver()) throw new IllegalStateException("Battle is already over");
        s.resetLog();
        s.touch();

        BattleCombatant next = buildPlayer(newCaughtId, playerId);
        if (next == null) throw new IllegalArgumentException("That Pokemon was not found");
        if (next.isFainted()) throw new IllegalStateException(next.getName() + " has fainted — revive it first");
        // Persist the outgoing Pokemon's HP before swapping.
        persistPlayerHp(s);
        s.addLog(s.getPlayer().getName() + ", come back!");
        s.setPlayer(next);
        s.addLog("Go, " + next.getName() + "!");

        // Wild attacks the newly sent-out Pokemon.
        PokemonMove wildMove = combat.chooseAiMove(s.getWild(), s.getPlayer());
        if (combat.canAct(s.getWild(), s.getLog())) combat.executeMove(s.getWild(), s.getPlayer(), wildMove, s.getLog());
        combat.endOfTurnTicks(s.getPlayer(), s.getWild(), s.getLog());
        resolveOutcome(s);
        s.incrementTurn();
        persistPlayerHp(s);
        return s;
    }

    /** Attempt to flee. Success chance rises with each turn. */
    public BattleState flee(int playerId, long battleId) throws Exception {
        BattleState s = getBattle(playerId, battleId);
        if (s.isOver()) throw new IllegalStateException("Battle is already over");
        s.resetLog();
        s.touch();

        double chance = 0.5 + 0.12 * Math.max(0, s.getTurnCount() - MAX_TURNS_BEFORE_FLEE_RISES);
        if (s.getPlayer().getSpeed() >= s.getWild().getSpeed()) chance += 0.2;
        if (RNG.nextDouble() < Math.min(0.95, chance)) {
            s.addLog("Got away safely!");
            s.setOver(true);
            s.setOutcome("FLED");
            sessions.remove(battleId);
        } else {
            s.addLog("Can't escape!");
            PokemonMove wildMove = combat.chooseAiMove(s.getWild(), s.getPlayer());
            if (combat.canAct(s.getWild(), s.getLog())) combat.executeMove(s.getWild(), s.getPlayer(), wildMove, s.getLog());
            combat.endOfTurnTicks(s.getPlayer(), s.getWild(), s.getLog());
            resolveOutcome(s);
            s.incrementTurn();
        }
        persistPlayerHp(s);
        return s;
    }

    private void resolveOutcome(BattleState s) {
        if (s.isOver()) return;
        BattleCombatant wild = s.getWild();
        BattleCombatant player = s.getPlayer();

        if (wild.isFainted()) {
            s.setOver(true);
            s.setOutcome("WON");
            awardWinXp(s);
            // Defeated wild Pokemon is consumed — remove it from the map so it can't
            // be battled or caught again.
            try { db.markSpawnCaught(s.getSpawnId(), s.getPlayerId()); } catch (Exception ignored) {}
            sessions.remove(s.getBattleId());
        } else if (player.isFainted()) {
            s.setOver(true);
            s.setOutcome("FAINTED");
            s.addLog(player.getName() + " has no energy left to battle!");
            sessions.remove(s.getBattleId());
        }
    }

    private void awardWinXp(BattleState s) {
        try {
            int rarity = 1;
            PokemonSpecies sp = db.getSpeciesById(s.getWild().getSpeciesId());
            if (sp != null) rarity = Math.max(1, sp.getRarity());
            long mfrom = s.getWild().getLevel() * (long) (5 + rarity * 3);
            Map<String, Object> r = db.addPokemonExp(s.getPlayer().getRefId(), s.getPlayerId(), mfrom);
            s.addLog(s.getPlayer().getName() + " gained " + mfrom + " EXP!");
            if (Boolean.TRUE.equals(r.get("leveledUp"))) {
                s.addLog(s.getPlayer().getName() + " grew to Lv." + r.get("newLevel") + "!");
            }
            db.addXp(s.getPlayerId(), (int) (s.getWild().getLevel() * 3L)); // trainer XP
        } catch (Exception e) {
            // XP is best-effort; never fail the battle result over it.
            System.err.println("[BattleService] XP award error: " + e.getMessage());
        }
    }

    private static int statAtLevel(int base, int level, double iv) {
        return Math.max(1, (int) (base * (level + 50) / 100.0 * iv));
    }

    private void purgeStale() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.getLastActivity() > SESSION_TTL_MS);
    }
}

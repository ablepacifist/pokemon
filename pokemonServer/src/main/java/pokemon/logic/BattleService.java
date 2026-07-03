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

        PokemonMove playerMove = findMove(s.getPlayer(), moveId);
        if (playerMove == null) throw new IllegalArgumentException("That Pokemon doesn't know that move");
        PokemonMove wildMove = chooseWildMove(s.getWild(), s.getPlayer());

        resolveOrderedTurn(s, playerMove, wildMove);
        endOfTurn(s);
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
        PokemonMove wildMove = chooseWildMove(s.getWild(), s.getPlayer());
        if (canAct(s.getWild(), s)) executeMove(s, s.getWild(), s.getPlayer(), wildMove, false);
        endOfTurn(s);
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
            PokemonMove wildMove = chooseWildMove(s.getWild(), s.getPlayer());
            if (canAct(s.getWild(), s)) executeMove(s, s.getWild(), s.getPlayer(), wildMove, false);
            endOfTurn(s);
            s.incrementTurn();
        }
        persistPlayerHp(s);
        return s;
    }

    // ── Turn resolution ────────────────────────────────────────────────────────

    private void resolveOrderedTurn(BattleState s, PokemonMove playerMove, PokemonMove wildMove) {
        BattleCombatant player = s.getPlayer();
        BattleCombatant wild   = s.getWild();

        player.setFlinched(false);
        wild.setFlinched(false);

        // Order: higher move priority first; tie broken by effective speed; then random.
        boolean playerFirst;
        if (playerMove.getPriority() != wildMove.getPriority()) {
            playerFirst = playerMove.getPriority() > wildMove.getPriority();
        } else {
            int pSpeed = effectiveSpeed(player);
            int wSpeed = effectiveSpeed(wild);
            if (pSpeed != wSpeed) playerFirst = pSpeed > wSpeed;
            else                  playerFirst = RNG.nextBoolean();
        }

        BattleCombatant firstC  = playerFirst ? player : wild;
        BattleCombatant secondC = playerFirst ? wild : player;
        PokemonMove firstM  = playerFirst ? playerMove : wildMove;
        PokemonMove secondM = playerFirst ? wildMove : playerMove;

        if (canAct(firstC, s)) executeMove(s, firstC, secondC, firstM, playerFirst);
        if (secondC.isFainted() || firstC.isFainted()) return;
        if (secondC.isFlinched()) { s.addLog(secondC.getName() + " flinched and couldn't move!"); return; }
        if (canAct(secondC, s)) executeMove(s, secondC, firstC, secondM, !playerFirst);
    }

    /** Executes one combatant's move against the other, applying damage + effects. */
    private void executeMove(BattleState s, BattleCombatant attacker, BattleCombatant defender,
                             PokemonMove move, boolean attackerIsPlayer) {
        if (defender.isFainted() || attacker.isFainted()) return;

        s.addLog(attacker.getName() + " used " + move.getName() + "!");

        // Accuracy check (0 accuracy in data = always hits, e.g. self-buffs).
        if (move.getAccuracy() > 0) {
            double acc = move.getAccuracy()
                       * accStageMult(attacker.getStage(5))
                       / accStageMult(defender.getStage(6));
            if (RNG.nextDouble() * 100 > acc) {
                s.addLog(attacker.getName() + "'s attack missed!");
                return;
            }
        }

        int totalDealt = 0;
        if (move.getPower() > 0) {
            double typeMult = TypeChart.multiplier(move.getType(), defender.getType1(), defender.getType2());
            if (typeMult == 0.0) {
                s.addLog("It had no effect on " + defender.getName() + "...");
                return;
            }
            int hits = rollHitCount(move);
            boolean crit = false;
            for (int h = 0; h < hits && !defender.isFainted(); h++) {
                int dmg = computeDamage(attacker, defender, move, typeMult);
                boolean thisCrit = RNG.nextDouble() < critChance(move);
                if (thisCrit) { dmg = (int) (dmg * 1.5); crit = true; }
                dmg = Math.max(1, dmg);
                defender.setCurHp(defender.getCurHp() - dmg);
                totalDealt += dmg;
            }
            String eff = TypeChart.label(typeMult);
            String hitNote = hits > 1 ? " Hit " + hits + " times!" : "";
            s.addLog(defender.getName() + " took " + totalDealt + " damage."
                + (crit ? " A critical hit!" : "") + (eff.isEmpty() ? "" : " " + eff) + hitNote);

            // Drain (heal a % of damage dealt) or recoil (negative drain).
            if (move.getDrain() > 0 && totalDealt > 0) {
                int heal = Math.max(1, totalDealt * move.getDrain() / 100);
                attacker.setCurHp(attacker.getCurHp() + heal);
                s.addLog(attacker.getName() + " drained " + heal + " HP!");
            } else if (move.getDrain() < 0 && totalDealt > 0) {
                int recoil = Math.max(1, totalDealt * (-move.getDrain()) / 100);
                attacker.setCurHp(attacker.getCurHp() - recoil);
                s.addLog(attacker.getName() + " was hit by recoil (" + recoil + ")!");
            }
        }

        // Self-healing moves (e.g. Recover, Roost). Negative healing = self-damage.
        if (move.getHealing() > 0) {
            int heal = Math.max(1, attacker.getMaxHp() * move.getHealing() / 100);
            int before = attacker.getCurHp();
            attacker.setCurHp(attacker.getCurHp() + heal);
            if (attacker.getCurHp() > before) s.addLog(attacker.getName() + " restored " + (attacker.getCurHp() - before) + " HP!");
        } else if (move.getHealing() < 0) {
            int dmg = Math.max(1, attacker.getMaxHp() * (-move.getHealing()) / 100);
            attacker.setCurHp(attacker.getCurHp() - dmg);
        }

        applyMoveEffects(s, attacker, defender, move, totalDealt);

        if (defender.isFainted()) s.addLog(defender.getName() + " fainted!");
        if (attacker.isFainted()) s.addLog(attacker.getName() + " fainted!");
    }

    /** Standard physical/special damage with STAB and type multiplier (no crit/random-free). */
    private int computeDamage(BattleCombatant attacker, BattleCombatant defender, PokemonMove move, double typeMult) {
        boolean physical = "Physical".equalsIgnoreCase(move.getCategory());
        int atkStat = physical ? effectiveAttack(attacker) : effectiveSpAtk(attacker);
        int defStat = physical
            ? Math.max(1, (int) (defender.getDefense() * stageMult(defender.getStage(1))))
            : Math.max(1, (int) (defender.getSpDef()  * stageMult(defender.getStage(3))));
        double stab = matchesType(move.getType(), attacker) ? 1.5 : 1.0;
        double base = ((2.0 * attacker.getLevel() / 5.0 + 2.0) * move.getPower() * atkStat / Math.max(1, defStat)) / 50.0 + 2.0;
        double rand = 0.85 + RNG.nextDouble() * 0.15;
        return Math.max(1, (int) (base * stab * typeMult * rand));
    }

    /** Multi-hit count using the standard 2–5 hit distribution; otherwise the move's fixed range. */
    private int rollHitCount(PokemonMove move) {
        int min = Math.max(1, move.getMinHits());
        int max = Math.max(min, move.getMaxHits());
        if (min == max) return min;
        if (min == 2 && max == 5) {
            double r = RNG.nextDouble();
            if (r < 0.375) return 2;
            if (r < 0.75)  return 3;
            if (r < 0.875) return 4;
            return 5;
        }
        return min + RNG.nextInt(max - min + 1);
    }

    private double critChance(PokemonMove move) {
        return move.getCritRate() > 0 ? 0.125 : 0.0625;
    }

    /**
     * Data-driven secondary/primary effects from move metadata: ailments, flinch,
     * confusion, and stat-stage changes. A move with power 0 applies its effects as
     * the primary action (guaranteed unless it has a percentage chance); a damaging
     * move applies them as secondary effects gated by their chance.
     */
    private void applyMoveEffects(BattleState s, BattleCombatant attacker, BattleCombatant defender,
                                  PokemonMove move, int dealtDamage) {
        boolean damaging = move.getPower() > 0;
        // If a damaging move missed/no-effect (dealt 0) on a target, skip secondaries.
        if (damaging && dealtDamage <= 0) return;

        // ── Ailment (status condition) ──
        if (move.getAilment() != null && !move.getAilment().isBlank()) {
            int chance = move.getAilmentChance();
            // PokeAPI reports 0 for guaranteed status moves.
            boolean apply = chance <= 0 ? !damaging : RNG.nextInt(100) < chance;
            if (apply) {
                String ail = move.getAilment();
                int turns = (ail.equals("SLEEP")) ? 1 + RNG.nextInt(3)
                          : (ail.equals("CONFUSE")) ? 2 + RNG.nextInt(3) : 0;
                inflict(s, defender, ail, turns);
            }
        }

        // ── Flinch ──
        if (move.getFlinchChance() > 0 && damaging && !defender.isFainted()) {
            if (RNG.nextInt(100) < move.getFlinchChance()) defender.setFlinched(true);
        }

        // ── Stat-stage changes ──
        if (move.getStatChanges() != null && !move.getStatChanges().isBlank()) {
            int chance = move.getStatChance();
            boolean apply = chance <= 0 ? true : RNG.nextInt(100) < chance;
            if (apply) {
                BattleCombatant tgt = "self".equalsIgnoreCase(move.getTarget()) ? attacker : defender;
                if (!tgt.isFainted()) applyStatChanges(s, tgt, move.getStatChanges());
            }
        }
    }

    private static final java.util.Map<String, Integer> STAT_IDX = java.util.Map.of(
        "atk", 0, "def", 1, "spa", 2, "spd", 3, "spe", 4, "acc", 5, "eva", 6);
    private static final String[] STAT_NAME = { "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed", "accuracy", "evasion" };

    private void applyStatChanges(BattleState s, BattleCombatant target, String encoded) {
        for (String part : encoded.split("\\|")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            Integer idx = STAT_IDX.get(kv[0].trim());
            int delta;
            try { delta = Integer.parseInt(kv[1].trim()); } catch (Exception e) { continue; }
            if (idx == null || delta == 0) continue;
            target.addStage(idx, delta);
            int mag = Math.abs(delta);
            String amt = mag >= 2 ? " sharply" : "";
            s.addLog(target.getName() + "'s " + STAT_NAME[idx] + (delta > 0 ? amt + " rose!" : amt + " fell!"));
        }
    }

    private void inflict(BattleState s, BattleCombatant target, String status, int turns) {
        if (target.getStatus() != null || target.isFainted()) return; // one major status at a time
        target.setStatus(status);
        target.setStatusTurns(turns);
        s.addLog(target.getName() + " " + statusVerb(status) + "!");
    }

    /** Pre-move check: can this combatant act this turn? Handles freeze/sleep/paralyze/confuse. */
    private boolean canAct(BattleCombatant c, BattleState s) {
        if (c.isFainted()) return false;
        String st = c.getStatus();
        if (st == null) return true;

        switch (st) {
            case "FREEZE" -> {
                if (RNG.nextDouble() < 0.20) { c.setStatus(null); s.addLog(c.getName() + " thawed out!"); return true; }
                s.addLog(c.getName() + " is frozen solid!");
                return false;
            }
            case "SLEEP" -> {
                c.setStatusTurns(c.getStatusTurns() - 1);
                if (c.getStatusTurns() <= 0) { c.setStatus(null); s.addLog(c.getName() + " woke up!"); return true; }
                s.addLog(c.getName() + " is fast asleep.");
                return false;
            }
            case "PARALYZE" -> {
                if (RNG.nextDouble() < 0.25) { s.addLog(c.getName() + " is paralyzed! It can't move!"); return false; }
                return true;
            }
            case "CONFUSE" -> {
                c.setStatusTurns(c.getStatusTurns() - 1);
                if (c.getStatusTurns() <= 0) { c.setStatus(null); s.addLog(c.getName() + " snapped out of confusion!"); return true; }
                if (RNG.nextDouble() < 0.33) {
                    int self = Math.max(1, c.getMaxHp() / 12);
                    c.setCurHp(c.getCurHp() - self);
                    s.addLog(c.getName() + " is confused! It hurt itself in its confusion (" + self + ").");
                    return false;
                }
                return true;
            }
            default -> { return true; }
        }
    }

    /** End-of-turn: poison/burn chip damage, then resolve outcome if someone fainted. */
    private void endOfTurn(BattleState s) {
        for (BattleCombatant c : new BattleCombatant[]{ s.getPlayer(), s.getWild() }) {
            if (c.isFainted()) continue;
            String st = c.getStatus();
            if ("POISON".equals(st) || "BURN".equals(st)) {
                int chip = Math.max(1, c.getMaxHp() / 8);
                c.setCurHp(c.getCurHp() - chip);
                s.addLog(c.getName() + " was hurt by " + (st.equals("BURN") ? "its burn" : "poison") + " (" + chip + ").");
            }
        }
        resolveOutcome(s);
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

    // ── Wild AI ────────────────────────────────────────────────────────────────

    private PokemonMove chooseWildMove(BattleCombatant wild, BattleCombatant target) {
        List<PokemonMove> moves = wild.getMoves();
        if (moves == null || moves.isEmpty())
            return new PokemonMove(33, "Tackle", "Normal", "Physical", 40, 100, 35);
        // Weighted pick: damaging moves weighted by power × type effectiveness; status flat.
        double[] weights = new double[moves.size()];
        double total = 0;
        for (int i = 0; i < moves.size(); i++) {
            PokemonMove m = moves.get(i);
            double w;
            if (m.getPower() > 0) {
                double tm = TypeChart.multiplier(m.getType(), target.getType1(), target.getType2());
                w = Math.max(5, m.getPower() * Math.max(0.25, tm));
            } else {
                w = 18; // status move base appeal
            }
            weights[i] = w;
            total += w;
        }
        double roll = RNG.nextDouble() * total;
        for (int i = 0; i < moves.size(); i++) {
            roll -= weights[i];
            if (roll <= 0) return moves.get(i);
        }
        return moves.get(moves.size() - 1);
    }

    // ── Stat helpers ───────────────────────────────────────────────────────────

    private PokemonMove findMove(BattleCombatant c, int moveId) {
        if (c.getMoves() == null) return null;
        return c.getMoves().stream().filter(m -> m.getId() == moveId).findFirst().orElse(null);
    }

    private boolean matchesType(String moveType, BattleCombatant c) {
        return moveType != null &&
            (moveType.equalsIgnoreCase(c.getType1()) || moveType.equalsIgnoreCase(c.getType2()));
    }

    private int effectiveAttack(BattleCombatant c) {
        double v = c.getAttack() * stageMult(c.getStage(0));
        if ("BURN".equals(c.getStatus())) v *= 0.5;
        return Math.max(1, (int) v);
    }

    private int effectiveSpAtk(BattleCombatant c) {
        return Math.max(1, (int) (c.getSpAtk() * stageMult(c.getStage(2))));
    }

    private int effectiveSpeed(BattleCombatant c) {
        double v = c.getSpeed() * stageMult(c.getStage(4));
        if ("PARALYZE".equals(c.getStatus())) v *= 0.5;
        return Math.max(1, (int) v);
    }

    private double stageMult(int stage) {
        return stage >= 0 ? (2.0 + stage) / 2.0 : 2.0 / (2.0 - stage);
    }

    private double accStageMult(int stage) {
        return stage >= 0 ? (3.0 + stage) / 3.0 : 3.0 / (3.0 - stage);
    }

    private static int statAtLevel(int base, int level, double iv) {
        return Math.max(1, (int) (base * (level + 50) / 100.0 * iv));
    }

    private String statusVerb(String status) {
        return switch (status) {
            case "SLEEP" -> "fell asleep";
            case "PARALYZE" -> "was paralyzed";
            case "POISON" -> "was poisoned";
            case "BURN" -> "was burned";
            case "FREEZE" -> "was frozen solid";
            case "CONFUSE" -> "became confused";
            default -> "was affected";
        };
    }

    private void purgeStale() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.getLastActivity() > SESSION_TTL_MS);
    }
}

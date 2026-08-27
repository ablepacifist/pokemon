package pokemon.logic;

import org.springframework.stereotype.Component;
import pokemon.object.BattleCombatant;
import pokemon.object.PokemonMove;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared turn-based combat core (extracted from the M3 wild-battle engine so both
 * wild battles and gym battles use identical rules). Operates purely on two
 * {@link BattleCombatant}s and a log sink — it knows nothing about sessions,
 * outcomes, players, or persistence. Callers resolve win/lose themselves.
 *
 * Rules: physical/special split, STAB, 18-type chart, crit, multi-hit, drain/
 * recoil, self-heal, priority ordering, flinch, and status conditions
 * (sleep/paralyze/poison/burn/freeze/confusion) driven by move metadata.
 */
@Component
public class CombatEngine {

    private static final Random RNG = new Random();

    private static final Map<String, Integer> STAT_IDX = Map.of(
        "atk", 0, "def", 1, "spa", 2, "spd", 3, "spe", 4, "acc", 5, "eva", 6);
    private static final String[] STAT_NAME = { "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed", "accuracy", "evasion" };

    /** Resolve one full turn: order by priority then speed, both sides act (respecting faint/flinch). */
    public void resolveTurn(BattleCombatant a, PokemonMove aMove,
                            BattleCombatant b, PokemonMove bMove, List<String> log) {
        a.setFlinched(false);
        b.setFlinched(false);

        boolean aFirst;
        if (aMove.getPriority() != bMove.getPriority()) {
            aFirst = aMove.getPriority() > bMove.getPriority();
        } else {
            int sa = effectiveSpeed(a), sb = effectiveSpeed(b);
            aFirst = sa != sb ? sa > sb : RNG.nextBoolean();
        }

        BattleCombatant first  = aFirst ? a : b;
        BattleCombatant second = aFirst ? b : a;
        PokemonMove firstM  = aFirst ? aMove : bMove;
        PokemonMove secondM = aFirst ? bMove : aMove;

        if (canAct(first, log)) executeMove(first, second, firstM, log);
        if (second.isFainted() || first.isFainted()) return;
        if (second.isFlinched()) { log.add(second.getName() + " flinched and couldn't move!"); return; }
        if (canAct(second, log)) executeMove(second, first, secondM, log);
    }

    /** Executes one combatant's move against the other, applying damage + effects. */
    public void executeMove(BattleCombatant attacker, BattleCombatant defender, PokemonMove move, List<String> log) {
        if (defender.isFainted() || attacker.isFainted()) return;

        log.add(attacker.getName() + " used " + move.getName() + "!");

        if (move.getAccuracy() > 0) {
            double acc = move.getAccuracy() * accStageMult(attacker.getStage(5)) / accStageMult(defender.getStage(6));
            if (RNG.nextDouble() * 100 > acc) { log.add(attacker.getName() + "'s attack missed!"); return; }
        }

        int totalDealt = 0;
        if (move.getPower() > 0) {
            double typeMult = TypeChart.multiplier(move.getType(), defender.getType1(), defender.getType2());
            if (typeMult == 0.0) { log.add("It had no effect on " + defender.getName() + "..."); return; }
            int hits = rollHitCount(move);
            boolean crit = false;
            for (int h = 0; h < hits && !defender.isFainted(); h++) {
                int dmg = computeDamage(attacker, defender, move, typeMult);
                if (RNG.nextDouble() < critChance(move)) { dmg = (int) (dmg * 1.5); crit = true; }
                dmg = Math.max(1, dmg);
                defender.setCurHp(defender.getCurHp() - dmg);
                totalDealt += dmg;
            }
            String eff = TypeChart.label(typeMult);
            String hitNote = hits > 1 ? " Hit " + hits + " times!" : "";
            log.add(defender.getName() + " took " + totalDealt + " damage."
                + (crit ? " A critical hit!" : "") + (eff.isEmpty() ? "" : " " + eff) + hitNote);

            if (move.getDrain() > 0 && totalDealt > 0) {
                int heal = Math.max(1, totalDealt * move.getDrain() / 100);
                attacker.setCurHp(attacker.getCurHp() + heal);
                log.add(attacker.getName() + " drained " + heal + " HP!");
            } else if (move.getDrain() < 0 && totalDealt > 0) {
                int recoil = Math.max(1, totalDealt * (-move.getDrain()) / 100);
                attacker.setCurHp(attacker.getCurHp() - recoil);
                log.add(attacker.getName() + " was hit by recoil (" + recoil + ")!");
            }
        }

        if (move.getHealing() > 0) {
            int heal = Math.max(1, attacker.getMaxHp() * move.getHealing() / 100);
            int before = attacker.getCurHp();
            attacker.setCurHp(attacker.getCurHp() + heal);
            if (attacker.getCurHp() > before) log.add(attacker.getName() + " restored " + (attacker.getCurHp() - before) + " HP!");
        } else if (move.getHealing() < 0) {
            int dmg = Math.max(1, attacker.getMaxHp() * (-move.getHealing()) / 100);
            attacker.setCurHp(attacker.getCurHp() - dmg);
        }

        applyMoveEffects(attacker, defender, move, totalDealt, log);

        if (defender.isFainted()) log.add(defender.getName() + " fainted!");
        if (attacker.isFainted()) log.add(attacker.getName() + " fainted!");
    }

    /** End-of-turn poison/burn chip damage (no outcome resolution). */
    public void endOfTurnTicks(BattleCombatant a, BattleCombatant b, List<String> log) {
        for (BattleCombatant c : new BattleCombatant[]{ a, b }) {
            if (c.isFainted()) continue;
            String st = c.getStatus();
            if ("POISON".equals(st) || "BURN".equals(st)) {
                int chip = Math.max(1, c.getMaxHp() / 8);
                c.setCurHp(c.getCurHp() - chip);
                log.add(c.getName() + " was hurt by " + (st.equals("BURN") ? "its burn" : "poison") + " (" + chip + ").");
            }
        }
    }

    /** Pre-move check: freeze/sleep/paralyze/confuse. */
    public boolean canAct(BattleCombatant c, List<String> log) {
        if (c.isFainted()) return false;
        String st = c.getStatus();
        if (st == null) return true;
        switch (st) {
            case "FREEZE" -> {
                if (RNG.nextDouble() < 0.20) { c.setStatus(null); log.add(c.getName() + " thawed out!"); return true; }
                log.add(c.getName() + " is frozen solid!");
                return false;
            }
            case "SLEEP" -> {
                c.setStatusTurns(c.getStatusTurns() - 1);
                if (c.getStatusTurns() <= 0) { c.setStatus(null); log.add(c.getName() + " woke up!"); return true; }
                log.add(c.getName() + " is fast asleep.");
                return false;
            }
            case "PARALYZE" -> {
                if (RNG.nextDouble() < 0.25) { log.add(c.getName() + " is paralyzed! It can't move!"); return false; }
                return true;
            }
            case "CONFUSE" -> {
                c.setStatusTurns(c.getStatusTurns() - 1);
                if (c.getStatusTurns() <= 0) { c.setStatus(null); log.add(c.getName() + " snapped out of confusion!"); return true; }
                if (RNG.nextDouble() < 0.33) {
                    int self = Math.max(1, c.getMaxHp() / 12);
                    c.setCurHp(c.getCurHp() - self);
                    log.add(c.getName() + " is confused! It hurt itself in its confusion (" + self + ").");
                    return false;
                }
                return true;
            }
            default -> { return true; }
        }
    }

    /** Weighted AI move pick — reused for wild Pokemon and NPC gym defenders. */
    public PokemonMove chooseAiMove(BattleCombatant attacker, BattleCombatant target) {
        List<PokemonMove> moves = attacker.getMoves();
        if (moves == null || moves.isEmpty()) return defaultMove();
        double[] weights = new double[moves.size()];
        double total = 0;
        for (int i = 0; i < moves.size(); i++) {
            PokemonMove m = moves.get(i);
            double w;
            if (m.getPower() > 0) {
                double tm = TypeChart.multiplier(m.getType(), target.getType1(), target.getType2());
                w = Math.max(5, m.getPower() * Math.max(0.25, tm));
            } else {
                w = 18;
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

    public PokemonMove findMove(BattleCombatant c, int moveId) {
        if (c.getMoves() == null) return null;
        return c.getMoves().stream().filter(m -> m.getId() == moveId).findFirst().orElse(null);
    }

    public PokemonMove defaultMove() {
        return new PokemonMove(33, "Tackle", "Normal", "Physical", 40, 100, 35);
    }

    // ── internals ───────────────────────────────────────────────────────────────

    private void applyMoveEffects(BattleCombatant attacker, BattleCombatant defender,
                                  PokemonMove move, int dealtDamage, List<String> log) {
        boolean damaging = move.getPower() > 0;
        if (damaging && dealtDamage <= 0) return;

        if (move.getAilment() != null && !move.getAilment().isBlank()) {
            int chance = move.getAilmentChance();
            boolean apply = chance <= 0 ? !damaging : RNG.nextInt(100) < chance;
            if (apply) {
                String ail = move.getAilment();
                int turns = ail.equals("SLEEP") ? 1 + RNG.nextInt(3)
                          : ail.equals("CONFUSE") ? 2 + RNG.nextInt(3) : 0;
                inflict(defender, ail, turns, log);
            }
        }
        if (move.getFlinchChance() > 0 && damaging && !defender.isFainted()) {
            if (RNG.nextInt(100) < move.getFlinchChance()) defender.setFlinched(true);
        }
        if (move.getStatChanges() != null && !move.getStatChanges().isBlank()) {
            int chance = move.getStatChance();
            boolean apply = chance <= 0 || RNG.nextInt(100) < chance;
            if (apply) {
                BattleCombatant tgt = "self".equalsIgnoreCase(move.getTarget()) ? attacker : defender;
                if (!tgt.isFainted()) applyStatChanges(tgt, move.getStatChanges(), log);
            }
        }
    }

    private void applyStatChanges(BattleCombatant target, String encoded, List<String> log) {
        for (String part : encoded.split("\\|")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            Integer idx = STAT_IDX.get(kv[0].trim());
            int delta;
            try { delta = Integer.parseInt(kv[1].trim()); } catch (Exception e) { continue; }
            if (idx == null || delta == 0) continue;
            target.addStage(idx, delta);
            String amt = Math.abs(delta) >= 2 ? " sharply" : "";
            log.add(target.getName() + "'s " + STAT_NAME[idx] + (delta > 0 ? amt + " rose!" : amt + " fell!"));
        }
    }

    private void inflict(BattleCombatant target, String status, int turns, List<String> log) {
        if (target.getStatus() != null || target.isFainted()) return;
        target.setStatus(status);
        target.setStatusTurns(turns);
        log.add(target.getName() + " " + statusVerb(status) + "!");
    }

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

    private double critChance(PokemonMove move) { return move.getCritRate() > 0 ? 0.125 : 0.0625; }

    private boolean matchesType(String moveType, BattleCombatant c) {
        return moveType != null && (moveType.equalsIgnoreCase(c.getType1()) || moveType.equalsIgnoreCase(c.getType2()));
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

    private double stageMult(int stage) { return stage >= 0 ? (2.0 + stage) / 2.0 : 2.0 / (2.0 - stage); }
    private double accStageMult(int stage) { return stage >= 0 ? (3.0 + stage) / 3.0 : 3.0 / (3.0 - stage); }

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
}

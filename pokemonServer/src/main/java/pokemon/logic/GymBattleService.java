package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gym battles (Milestone 4). The player's team fights the gym's defenders
 * sequentially, reusing the shared {@link CombatEngine}. Defeating every
 * defender flips the gym to the player's team and awards coins — the game's
 * primary coin source. Player HP persists (no auto-heal).
 */
@Service
public class GymBattleService {

    private static final long SESSION_TTL_MS = 15 * 60 * 1000L;
    private final ConcurrentHashMap<Long, GymBattleState> sessions = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);
    private final java.util.Random rng = new java.util.Random();

    @Autowired private PokemonDatabase db;
    @Autowired private CombatEngine combat;

    // ── Lifecycle ───────────────────────────────────────────────────────────────

    public GymBattleState start(int playerId, long gymId, List<Long> caughtIds) throws Exception {
        purgeStale();
        Gym gym = db.getGymById(gymId);
        if (gym == null) throw new IllegalArgumentException("Gym not found");
        String playerTeam = db.getTeam(playerId);
        if (playerTeam == null || playerTeam.isBlank())
            throw new IllegalStateException("Choose a team before battling gyms");
        if (playerTeam.equals(gym.getControllingTeam()))
            throw new IllegalStateException("This gym is already yours — you can add defenders instead");
        if (caughtIds == null || caughtIds.isEmpty())
            throw new IllegalArgumentException("Pick at least one Pokemon");
        if (caughtIds.size() > 6) caughtIds = caughtIds.subList(0, 6);

        List<BattleCombatant> party = new ArrayList<>();
        for (Long cid : caughtIds) {
            BattleCombatant c = buildPartyMember(cid, playerId);
            if (c != null) party.add(c);
        }
        if (party.isEmpty()) throw new IllegalArgumentException("None of those Pokemon were found");
        if (party.stream().allMatch(BattleCombatant::isFainted))
            throw new IllegalStateException("All chosen Pokemon have fainted — revive them first");

        List<GymDefender> rows = gym.getDefenders();
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("This gym has no defenders");
        List<BattleCombatant> defenders = new ArrayList<>();
        List<Long> defenderIds = new ArrayList<>();
        for (GymDefender d : rows) { defenders.add(buildDefender(d)); defenderIds.add(d.getId()); }

        GymBattleState st = new GymBattleState();
        st.setBattleId(seq.getAndIncrement());
        st.setPlayerId(playerId);
        st.setGymId(gymId);
        st.setGymName(gym.getName());
        st.setPlayerTeam(playerTeam);
        st.setDefenderTeam(gym.getControllingTeam());
        st.setParty(party);
        st.setDefenders(defenders);
        st.setDefenderDbIds(defenderIds);
        st.setActivePlayerIdx(firstAlive(party, 0));
        st.setActiveDefenderIdx(0);
        st.addLog("The gym is defended by Team " + gym.getControllingTeam() + "!");
        st.addLog("Go, " + st.getActivePlayer().getName() + "!");
        sessions.put(st.getBattleId(), st);
        return st;
    }

    public GymBattleState get(int playerId, long battleId) {
        GymBattleState s = sessions.get(battleId);
        if (s == null) throw new IllegalArgumentException("Battle not found or already ended");
        if (s.getPlayerId() != playerId) throw new IllegalStateException("Not your battle");
        return s;
    }

    public void end(int playerId, long battleId) {
        GymBattleState s = sessions.get(battleId);
        if (s != null && s.getPlayerId() == playerId) { persistParty(s); sessions.remove(battleId); }
    }

    // ── Turns ───────────────────────────────────────────────────────────────────

    public GymBattleState takeTurn(int playerId, long battleId, int moveId) throws Exception {
        GymBattleState s = get(playerId, battleId);
        if (s.isOver()) throw new IllegalStateException("Battle is already over");
        s.resetLog(); s.touch();

        BattleCombatant player = s.getActivePlayer();
        BattleCombatant defender = s.getActiveDefender();
        if (player == null || defender == null) throw new IllegalStateException("Battle state error");

        PokemonMove pMove = combat.findMove(player, moveId);
        if (pMove == null) throw new IllegalArgumentException("That Pokemon doesn't know that move");
        PokemonMove dMove = combat.chooseAiMove(defender, player);

        combat.resolveTurn(player, pMove, defender, dMove, s.getLog());
        combat.endOfTurnTicks(player, defender, s.getLog());
        advanceAfterTurn(s);
        persistActive(s);
        return s;
    }

    public GymBattleState switchActive(int playerId, long battleId, long caughtId) throws Exception {
        GymBattleState s = get(playerId, battleId);
        if (s.isOver()) throw new IllegalStateException("Battle is already over");
        s.resetLog(); s.touch();

        int idx = -1;
        for (int i = 0; i < s.getParty().size(); i++)
            if (s.getParty().get(i).getRefId() == caughtId) idx = i;
        if (idx < 0) throw new IllegalArgumentException("That Pokemon isn't in this battle");
        if (s.getParty().get(idx).isFainted()) throw new IllegalStateException("That Pokemon has fainted");
        if (idx == s.getActivePlayerIdx()) throw new IllegalStateException("That Pokemon is already out");

        s.addLog(s.getActivePlayer().getName() + ", come back!");
        s.setActivePlayerIdx(idx);
        s.addLog("Go, " + s.getActivePlayer().getName() + "!");

        // Defender gets a free hit on the switch.
        BattleCombatant defender = s.getActiveDefender();
        if (defender != null && combat.canAct(defender, s.getLog()))
            combat.executeMove(defender, s.getActivePlayer(), combat.chooseAiMove(defender, s.getActivePlayer()), s.getLog());
        combat.endOfTurnTicks(s.getActivePlayer(), defender, s.getLog());
        advanceAfterTurn(s);
        persistActive(s);
        return s;
    }

    /** After a resolved turn, advance defeated defenders / fainted party and settle the outcome. */
    private void advanceAfterTurn(GymBattleState s) {
        // Defender defeated → next defender.
        if (s.getActiveDefender() != null && s.getActiveDefender().isFainted()) {
            int next = firstAlive(s.getDefenders(), s.getActiveDefenderIdx() + 1);
            if (next >= 0) {
                s.setActiveDefenderIdx(next);
                s.addLog("Team " + s.getDefenderTeam() + " sent out " + s.getActiveDefender().getName() + "!");
            }
        }
        // Player active fainted → next party member.
        if (s.getActivePlayer() != null && s.getActivePlayer().isFainted()) {
            int next = firstAlive(s.getParty(), 0);
            if (next >= 0 && next != s.getActivePlayerIdx()) {
                persistActive(s);
                s.setActivePlayerIdx(next);
                s.addLog("Go, " + s.getActivePlayer().getName() + "!");
            }
        }
        resolveOutcome(s);
    }

    private void resolveOutcome(GymBattleState s) {
        if (s.isOver()) return;
        boolean defendersLeft = s.getDefenders().stream().anyMatch(d -> !d.isFainted());
        boolean partyLeft = s.getParty().stream().anyMatch(p -> !p.isFainted());

        if (!defendersLeft) {
            s.setOver(true);
            s.setOutcome("WON");
            claimGym(s);
        } else if (!partyLeft) {
            s.setOver(true);
            s.setOutcome("LOST");
            s.addLog("Your team was defeated. The gym holds.");
            persistParty(s);
            sessions.remove(s.getBattleId());
        }
    }

    /** Player defeated all defenders → flip the gym, award coins + XP. */
    private void claimGym(GymBattleState s) {
        try {
            db.clearGymDefenders(s.getGymId());
            db.setGymTeam(s.getGymId(), s.getPlayerTeam());
            int coins = 40 + rng.nextInt(21); // 40-60 coins
            db.addCoins(s.getPlayerId(), coins);
            s.setCoinsAwarded(coins);
            db.addXp(s.getPlayerId(), 200);
            // Battle EXP to every Pokemon that fought (non-fainted or not, they participated).
            for (BattleCombatant c : s.getParty()) {
                try { db.addPokemonExp(c.getRefId(), s.getPlayerId(), 80L); } catch (Exception ignored) {}
            }
            s.addLog("You defeated all defenders and claimed the gym for Team " + s.getPlayerTeam() + "!");
            s.addLog("Earned " + coins + " coins!");
        } catch (Exception e) {
            System.err.println("[GymBattleService] claim error: " + e.getMessage());
        }
        persistParty(s);
        sessions.remove(s.getBattleId());
    }

    // ── Building combatants ─────────────────────────────────────────────────────

    private BattleCombatant buildPartyMember(long caughtId, int playerId) throws Exception {
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
        c.setCurHp(p.getCurrentHp() > 0 ? Math.min(p.getCurrentHp(), p.getHp()) : 0);
        c.setAttack(p.getAttack());
        c.setDefense(p.getDefense());
        c.setSpAtk(p.getSpAtk());
        c.setSpDef(p.getSpDef());
        c.setSpeed(p.getSpeed());
        List<PokemonMove> moves = db.getCaughtPokemonMoves(caughtId, playerId);
        if (moves.isEmpty()) moves.add(combat.defaultMove());
        c.setMoves(moves);
        return c;
    }

    private BattleCombatant buildDefender(GymDefender d) throws Exception {
        BattleCombatant c = new BattleCombatant();
        c.setRefId(d.getId());
        c.setSpeciesId(d.getSpeciesId());
        c.setName(d.getName());
        c.setSpriteKey(d.getSpriteKey());
        c.setType1(d.getType1());
        c.setType2(d.getType2());
        c.setLevel(d.getLevel());
        c.setMaxHp(d.getHp());
        c.setCurHp(d.getHp());
        c.setAttack(d.getAttack());
        c.setDefense(d.getDefense());
        c.setSpAtk(d.getSpAtk());
        c.setSpDef(d.getSpDef());
        c.setSpeed(d.getSpeed());
        List<PokemonMove> moves = new ArrayList<>();
        for (int mid : d.getMoveIds()) {
            if (mid <= 0) continue;
            PokemonMove m = db.getMoveById(mid);
            if (m != null) moves.add(m);
        }
        if (moves.isEmpty()) moves.add(combat.defaultMove());
        c.setMoves(moves);
        return c;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private int firstAlive(List<BattleCombatant> list, int from) {
        for (int i = Math.max(0, from); i < list.size(); i++) if (!list.get(i).isFainted()) return i;
        for (int i = 0; i < list.size(); i++) if (!list.get(i).isFainted()) return i;
        return -1;
    }

    private void persistActive(GymBattleState s) {
        BattleCombatant c = s.getActivePlayer();
        if (c != null) { try { db.updateCurrentHp(c.getRefId(), s.getPlayerId(), c.getCurHp()); } catch (Exception ignored) {} }
    }

    private void persistParty(GymBattleState s) {
        for (BattleCombatant c : s.getParty()) {
            try { db.updateCurrentHp(c.getRefId(), s.getPlayerId(), c.getCurHp()); } catch (Exception ignored) {}
        }
    }

    private void purgeStale() {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> now - s.getLastActivity() > SESSION_TTL_MS);
    }
}

package pokemon.object;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-held state for a gym battle: the player's team fights the gym's
 * defenders sequentially. Held in memory by GymBattleService.
 */
public class GymBattleState {
    private long battleId;
    private int  playerId;
    private long gymId;
    private String gymName;
    private String playerTeam;      // the attacker's team
    private String defenderTeam;    // the gym's current team

    private List<BattleCombatant> party = new ArrayList<>();      // player's team (up to 6)
    private int activePlayerIdx;
    private List<BattleCombatant> defenders = new ArrayList<>();  // gym defenders in slot order
    private transient List<Long> defenderDbIds = new ArrayList<>();
    private int activeDefenderIdx;

    private transient List<String> log = new ArrayList<>();
    private boolean over;
    private String  outcome;        // null | WON | LOST | LEFT
    private int coinsAwarded;

    private long lastActivity = System.currentTimeMillis();

    public long getBattleId() { return battleId; }
    public void setBattleId(long battleId) { this.battleId = battleId; }
    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public long getGymId() { return gymId; }
    public void setGymId(long gymId) { this.gymId = gymId; }
    public String getGymName() { return gymName; }
    public void setGymName(String gymName) { this.gymName = gymName; }
    public String getPlayerTeam() { return playerTeam; }
    public void setPlayerTeam(String playerTeam) { this.playerTeam = playerTeam; }
    public String getDefenderTeam() { return defenderTeam; }
    public void setDefenderTeam(String defenderTeam) { this.defenderTeam = defenderTeam; }

    public List<BattleCombatant> getParty() { return party; }
    public void setParty(List<BattleCombatant> party) { this.party = party; }
    public int getActivePlayerIdx() { return activePlayerIdx; }
    public void setActivePlayerIdx(int i) { this.activePlayerIdx = i; }
    public List<BattleCombatant> getDefenders() { return defenders; }
    public void setDefenders(List<BattleCombatant> defenders) { this.defenders = defenders; }
    public List<Long> getDefenderDbIds() { return defenderDbIds; }
    public void setDefenderDbIds(List<Long> ids) { this.defenderDbIds = ids; }
    public int getActiveDefenderIdx() { return activeDefenderIdx; }
    public void setActiveDefenderIdx(int i) { this.activeDefenderIdx = i; }

    public boolean isOver() { return over; }
    public void setOver(boolean over) { this.over = over; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public int getCoinsAwarded() { return coinsAwarded; }
    public void setCoinsAwarded(int coinsAwarded) { this.coinsAwarded = coinsAwarded; }

    public List<String> getLog() { return log; }
    public void resetLog() { this.log = new ArrayList<>(); }
    public void addLog(String m) { this.log.add(m); }

    public long getLastActivity() { return lastActivity; }
    public void touch() { this.lastActivity = System.currentTimeMillis(); }

    // Convenience views for the frontend (serialized as activePlayer / activeDefender).
    public BattleCombatant getActivePlayer() {
        return (activePlayerIdx >= 0 && activePlayerIdx < party.size()) ? party.get(activePlayerIdx) : null;
    }
    public BattleCombatant getActiveDefender() {
        return (activeDefenderIdx >= 0 && activeDefenderIdx < defenders.size()) ? defenders.get(activeDefenderIdx) : null;
    }
    public int getDefendersTotal() { return defenders.size(); }
    public long getDefendersRemaining() { return defenders.stream().filter(d -> !d.isFainted()).count(); }
}

package pokemon.object;

import java.util.List;

/**
 * One side of a wild battle — either the player's active Pokemon or the wild Pokemon.
 * Holds live battle state (current HP, status, stat stages) separate from the
 * persistent CAUGHT_POKEMON record. Fainting in battle does NOT persist.
 */
public class BattleCombatant {
    // Identity
    private long   refId;      // caughtId for the player's mon, speciesId for the wild mon
    private int    speciesId;
    private String name;
    private String spriteKey;
    private String type1;
    private String type2;
    private int    level;

    // Live HP
    private int maxHp;
    private int curHp;

    // Base battle stats (stage modifiers applied at calc time, not stored here)
    private int attack;
    private int defense;
    private int spAtk;
    private int spDef;
    private int speed;

    private List<PokemonMove> moves;

    // Status condition: null | PARALYZE | SLEEP | POISON | BURN | FREEZE | CONFUSE
    private String status;
    private int    statusTurns;   // remaining turns for SLEEP/CONFUSE counters

    // Stat stages [-6..+6] for: attack, defense, spAtk, spDef, speed, accuracy, evasion
    private transient int[] stages = new int[7];

    // Set when the opponent's move makes this combatant flinch (skips its move this turn).
    private transient boolean flinched;

    public BattleCombatant() {}

    public long getRefId() { return refId; }
    public void setRefId(long refId) { this.refId = refId; }
    public int getSpeciesId() { return speciesId; }
    public void setSpeciesId(int speciesId) { this.speciesId = speciesId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpriteKey() { return spriteKey; }
    public void setSpriteKey(String spriteKey) { this.spriteKey = spriteKey; }
    public String getType1() { return type1; }
    public void setType1(String type1) { this.type1 = type1; }
    public String getType2() { return type2; }
    public void setType2(String type2) { this.type2 = type2; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public int getCurHp() { return curHp; }
    public void setCurHp(int curHp) { this.curHp = Math.max(0, Math.min(curHp, maxHp)); }
    public int getAttack() { return attack; }
    public void setAttack(int attack) { this.attack = attack; }
    public int getDefense() { return defense; }
    public void setDefense(int defense) { this.defense = defense; }
    public int getSpAtk() { return spAtk; }
    public void setSpAtk(int spAtk) { this.spAtk = spAtk; }
    public int getSpDef() { return spDef; }
    public void setSpDef(int spDef) { this.spDef = spDef; }
    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }
    public List<PokemonMove> getMoves() { return moves; }
    public void setMoves(List<PokemonMove> moves) { this.moves = moves; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getStatusTurns() { return statusTurns; }
    public void setStatusTurns(int statusTurns) { this.statusTurns = statusTurns; }

    public boolean isFainted() { return curHp <= 0; }
    public int hpPercent() { return maxHp <= 0 ? 0 : (int) Math.round(100.0 * curHp / maxHp); }

    /** Stage accessors — index: 0 atk, 1 def, 2 spAtk, 3 spDef, 4 speed, 5 accuracy, 6 evasion. */
    public int getStage(int idx) { return stages[idx]; }
    public void addStage(int idx, int delta) {
        stages[idx] = Math.max(-6, Math.min(6, stages[idx] + delta));
    }

    public boolean isFlinched() { return flinched; }
    public void setFlinched(boolean flinched) { this.flinched = flinched; }
}

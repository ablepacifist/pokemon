package pokemon.object;

import java.time.Instant;

/**
 * A Pokemon defending a gym. Stores a full stat snapshot + moveset so that both
 * player-owned and NPC defenders battle uniformly without touching CAUGHT_POKEMON.
 */
public class GymDefender {
    private long id;
    private long gymId;
    private Integer playerId;   // null for NPC defenders
    private Long caughtId;      // null for NPC defenders
    private int slot;           // 1-6
    private int motivation;     // 0-100 (0 = returns to owner)
    private int speciesId;
    private String name;
    private String spriteKey;
    private String type1;
    private String type2;
    private int level;
    private int hp;
    private int attack;
    private int defense;
    private int spAtk;
    private int spDef;
    private int speed;
    private int[] moveIds = new int[4];
    private Instant placedAt;

    public GymDefender() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getGymId() { return gymId; }
    public void setGymId(long gymId) { this.gymId = gymId; }
    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }
    public Long getCaughtId() { return caughtId; }
    public void setCaughtId(Long caughtId) { this.caughtId = caughtId; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public int getMotivation() { return motivation; }
    public void setMotivation(int motivation) { this.motivation = Math.max(0, Math.min(100, motivation)); }
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
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
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
    public int[] getMoveIds() { return moveIds; }
    public void setMoveIds(int[] moveIds) { this.moveIds = moveIds; }
    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }
}

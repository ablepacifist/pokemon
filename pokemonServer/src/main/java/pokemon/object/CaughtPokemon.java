package pokemon.object;

import java.time.Instant;

public class CaughtPokemon {
    private long id;
    private int playerId;
    private int speciesId;
    private String speciesName;
    private String spriteKey;
    private String type1;
    private String type2;
    private int pokemonLevel;
    private int hp;          // max HP (computed stat)
    private int currentHp;   // persistent battle HP; 0 = fainted
    private int attack;
    private int defense;
    private int spAtk;
    private int spDef;
    private int speed;
    private Instant caughtAt;
    private double caughtLat;
    private double caughtLng;
    private String nickname;
    private long   exp;
    private double iv;
    private boolean favourite;
    private java.util.List<PokemonMove> moves;
    // transient base stats — populated only by getCaughtById, not stored in CAUGHT_POKEMON
    private transient int baseHpForGrind, baseAtkForGrind, baseDefForGrind;
    private transient int baseSpAtkForGrind, baseSpDefForGrind, baseSpeedForGrind;

    public CaughtPokemon() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public int getSpeciesId() { return speciesId; }
    public void setSpeciesId(int speciesId) { this.speciesId = speciesId; }
    public String getSpeciesName() { return speciesName; }
    public void setSpeciesName(String speciesName) { this.speciesName = speciesName; }
    public String getSpriteKey() { return spriteKey; }
    public void setSpriteKey(String spriteKey) { this.spriteKey = spriteKey; }
    public String getType1() { return type1; }
    public void setType1(String type1) { this.type1 = type1; }
    public String getType2() { return type2; }
    public void setType2(String type2) { this.type2 = type2; }
    public int getPokemonLevel() { return pokemonLevel; }
    public void setPokemonLevel(int pokemonLevel) { this.pokemonLevel = pokemonLevel; }
    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }
    public int getCurrentHp() { return currentHp; }
    public void setCurrentHp(int currentHp) { this.currentHp = currentHp; }
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
    public Instant getCaughtAt() { return caughtAt; }
    public void setCaughtAt(Instant caughtAt) { this.caughtAt = caughtAt; }
    public double getCaughtLat() { return caughtLat; }
    public void setCaughtLat(double caughtLat) { this.caughtLat = caughtLat; }
    public double getCaughtLng() { return caughtLng; }
    public void setCaughtLng(double caughtLng) { this.caughtLng = caughtLng; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public long getExp() { return exp; }
    public void setExp(long exp) { this.exp = exp; }
    public double getIv() { return iv; }
    public void setIv(double iv) { this.iv = iv; }
    public boolean isFavourite() { return favourite; }
    public void setFavourite(boolean favourite) { this.favourite = favourite; }
    public java.util.List<PokemonMove> getMoves() { return moves; }
    public void setMoves(java.util.List<PokemonMove> moves) { this.moves = moves; }

    /** Nickname if set, otherwise the species name. */
    public String displayName() {
        return (nickname != null && !nickname.isBlank()) ? nickname : speciesName;
    }
    public int getBaseHpForGrind() { return baseHpForGrind; }
    public void setBaseHpForGrind(int v) { this.baseHpForGrind = v; }
    public int getBaseAtkForGrind() { return baseAtkForGrind; }
    public void setBaseAtkForGrind(int v) { this.baseAtkForGrind = v; }
    public int getBaseDefForGrind() { return baseDefForGrind; }
    public void setBaseDefForGrind(int v) { this.baseDefForGrind = v; }
    public int getBaseSpAtkForGrind() { return baseSpAtkForGrind; }
    public void setBaseSpAtkForGrind(int v) { this.baseSpAtkForGrind = v; }
    public int getBaseSpDefForGrind() { return baseSpDefForGrind; }
    public void setBaseSpDefForGrind(int v) { this.baseSpDefForGrind = v; }
    public int getBaseSpeedForGrind() { return baseSpeedForGrind; }
    public void setBaseSpeedForGrind(int v) { this.baseSpeedForGrind = v; }
}

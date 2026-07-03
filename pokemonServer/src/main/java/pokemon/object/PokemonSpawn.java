package pokemon.object;

import java.time.Instant;

public class PokemonSpawn {
    private long id;
    private int speciesId;
    private String speciesName;
    private String spriteKey;
    private double lat;
    private double lng;
    private Instant spawnedAt;
    private Instant expiresAt;
    private Integer caughtByPlayer;
    private int level;

    public PokemonSpawn() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getSpeciesId() { return speciesId; }
    public void setSpeciesId(int speciesId) { this.speciesId = speciesId; }
    public String getSpeciesName() { return speciesName; }
    public void setSpeciesName(String speciesName) { this.speciesName = speciesName; }
    public String getSpriteKey() { return spriteKey; }
    public void setSpriteKey(String spriteKey) { this.spriteKey = spriteKey; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public Instant getSpawnedAt() { return spawnedAt; }
    public void setSpawnedAt(Instant spawnedAt) { this.spawnedAt = spawnedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Integer getCaughtByPlayer() { return caughtByPlayer; }
    public void setCaughtByPlayer(Integer caughtByPlayer) { this.caughtByPlayer = caughtByPlayer; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
}

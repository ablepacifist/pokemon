package pokemon.object;

import java.time.Instant;

public class Pokestop {
    private long id;
    private String name;
    private double lat;
    private double lng;
    private Integer lastSpunBy;
    private Instant lastSpunAt;
    private boolean canSpin;

    public Pokestop() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public Integer getLastSpunBy() { return lastSpunBy; }
    public void setLastSpunBy(Integer lastSpunBy) { this.lastSpunBy = lastSpunBy; }
    public Instant getLastSpunAt() { return lastSpunAt; }
    public void setLastSpunAt(Instant lastSpunAt) { this.lastSpunAt = lastSpunAt; }
    public boolean isCanSpin() { return canSpin; }
    public void setCanSpin(boolean canSpin) { this.canSpin = canSpin; }
}

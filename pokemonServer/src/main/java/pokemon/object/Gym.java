package pokemon.object;

import java.time.Instant;
import java.util.List;

/** A gym (Milestone 4) — a claimable location controlled by a team. */
public class Gym {
    private long id;
    private String name;
    private double lat;
    private double lng;
    private String controllingTeam;   // VALOR / MYSTIC / INSTINCT / null (unclaimed)
    private Integer lastSpunBy;
    private Instant lastSpunAt;
    private boolean canSpin;
    private List<GymDefender> defenders;  // populated for the map popup / detail

    public Gym() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public String getControllingTeam() { return controllingTeam; }
    public void setControllingTeam(String controllingTeam) { this.controllingTeam = controllingTeam; }
    public Integer getLastSpunBy() { return lastSpunBy; }
    public void setLastSpunBy(Integer lastSpunBy) { this.lastSpunBy = lastSpunBy; }
    public Instant getLastSpunAt() { return lastSpunAt; }
    public void setLastSpunAt(Instant lastSpunAt) { this.lastSpunAt = lastSpunAt; }
    public boolean isCanSpin() { return canSpin; }
    public void setCanSpin(boolean canSpin) { this.canSpin = canSpin; }
    public List<GymDefender> getDefenders() { return defenders; }
    public void setDefenders(List<GymDefender> defenders) { this.defenders = defenders; }
}

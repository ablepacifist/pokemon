package pokemon.object;

import java.time.Instant;

/** An egg in a player's inventory. Hatches after PROGRESS_KM reaches DISTANCE_KM. */
public class PlayerEgg {
    private long id;
    private int playerId;
    private double distanceKm;    // 2, 5, or 10
    private double progressKm;
    private boolean incubating;
    private Instant obtainedAt;

    public PlayerEgg() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public int getPlayerId() { return playerId; }
    public void setPlayerId(int playerId) { this.playerId = playerId; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public double getProgressKm() { return progressKm; }
    public void setProgressKm(double progressKm) { this.progressKm = progressKm; }
    public boolean isIncubating() { return incubating; }
    public void setIncubating(boolean incubating) { this.incubating = incubating; }
    public Instant getObtainedAt() { return obtainedAt; }
    public void setObtainedAt(Instant obtainedAt) { this.obtainedAt = obtainedAt; }
}

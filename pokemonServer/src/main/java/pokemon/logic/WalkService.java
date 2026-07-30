package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walk-distance engine (Milestone 7 / 16.0 foundation). Turns successive GPS
 * pings into cumulative kilometres, then feeds that distance to egg incubation
 * and the buddy system. This is the single source of "how far the player walked".
 */
@Service
public class WalkService {

    // Per-ping accrual cap (km): drops GPS teleports / driving spikes. Pings arrive
    // every ~30s, so a real walking step is well under this.
    private static final double MAX_STEP_KM = 0.25;
    private static final double MIN_STEP_KM = 0.002;   // ignore < 2m jitter

    @Autowired private PokemonDatabase db;
    @Autowired private EggService eggService;
    @Autowired private BuddyService buddyService;

    /** Record a GPS ping. Returns { totalKm, deltaKm, events[] }. */
    public Map<String, Object> recordWalk(int playerId, double lat, double lng) throws Exception {
        double[] state = db.getWalkState(playerId);
        double totalKm = state[0];
        boolean hasLast = state[3] == 1;

        double accrued = 0;
        if (hasLast) {
            double meters = GeospatialUtils.distanceMeters(state[1], state[2], lat, lng);
            double km = meters / 1000.0;
            if (km >= MIN_STEP_KM && km <= MAX_STEP_KM) accrued = km;
            // else: noise (too small) or teleport (too big) — position updates, distance doesn't
        }

        totalKm += accrued;
        db.updateWalkState(playerId, totalKm, lat, lng);

        List<Map<String, Object>> events = new ArrayList<>();
        if (accrued > 0) {
            events.addAll(eggService.advance(playerId, accrued));
            events.addAll(buddyService.advance(playerId, accrued));
        }

        Map<String, Object> out = new java.util.HashMap<>();
        out.put("totalKm", Math.round(totalKm * 100.0) / 100.0);
        out.put("deltaKm", accrued);
        out.put("events", events);
        return out;
    }
}

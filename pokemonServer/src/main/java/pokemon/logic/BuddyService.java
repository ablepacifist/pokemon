package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.CaughtPokemon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buddy Pokemon: one caught Pokemon walks with the player and earns candy every
 * KM_PER_CANDY kilometres walked. Distance is fed in by WalkService.
 */
@Service
public class BuddyService {

    private static final double DEFAULT_KM_PER_CANDY = 3.0;

    @Autowired private PokemonDatabase db;

    /** Set (or replace) the player's buddy. */
    public void setBuddy(int playerId, long caughtId) throws Exception {
        CaughtPokemon p = db.getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");
        db.setBuddy(playerId, caughtId, DEFAULT_KM_PER_CANDY);
    }

    /** Current buddy info (with the Pokemon's details) or null. */
    public Map<String, Object> getBuddy(int playerId) throws Exception {
        double[] b = db.getBuddy(playerId);
        if (b == null) return null;
        long caughtId = (long) b[0];
        CaughtPokemon p = db.getCaughtById(caughtId, playerId);
        if (p == null) return null; // buddy was released/traded
        Map<String, Object> m = new HashMap<>();
        m.put("caughtId", caughtId);
        m.put("speciesName", p.getSpeciesName());
        m.put("nickname", p.getNickname());
        m.put("spriteKey", p.getSpriteKey());
        m.put("level", p.getPokemonLevel());
        m.put("kmSinceCandy", b[1]);
        m.put("kmPerCandy", b[2]);
        return m;
    }

    /** Advance buddy distance; award EXP candy for each full KM_PER_CANDY crossed. */
    public List<Map<String, Object>> advance(int playerId, double deltaKm) throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        if (deltaKm <= 0) return events;
        double[] b = db.getBuddy(playerId);
        if (b == null) return events;

        double km = b[1] + deltaKm;
        double per = b[2] > 0 ? b[2] : DEFAULT_KM_PER_CANDY;
        int earned = 0;
        while (km >= per) { km -= per; earned++; }

        db.updateBuddyProgress(playerId, km);
        if (earned > 0) {
            db.adjustItem(playerId, "CANDY_XS", earned);
            CaughtPokemon p = db.getCaughtById((long) b[0], playerId);
            String name = p != null ? p.displayName() : "Your buddy";
            Map<String, Object> ev = new HashMap<>();
            ev.put("type", "buddyCandy");
            ev.put("candy", earned);
            ev.put("buddyName", name);
            events.add(ev);
        }
        return events;
    }
}

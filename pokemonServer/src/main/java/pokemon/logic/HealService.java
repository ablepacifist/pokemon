package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.CaughtPokemon;

import java.util.Map;

/**
 * Healing items. Potions restore HP to a conscious Pokemon; Revives bring back a
 * fainted one. All HP changes persist to CAUGHT_POKEMON.CURRENT_HP — there is no
 * auto-heal, so items are the only way back to full after a battle.
 */
@Service
public class HealService {

    @Autowired
    private PokemonDatabase db;

    /** Flat HP restored by each potion; MAX_POTION = full (-1 sentinel). */
    private static final Map<String, Integer> POTION = Map.of(
        "POTION", 20, "SUPER_POTION", 50, "HYPER_POTION", 200, "MAX_POTION", -1);
    /** Fraction of max HP a revive restores. */
    private static final Map<String, Double> REVIVE = Map.of(
        "REVIVE", 0.5, "MAX_REVIVE", 1.0);

    /** Apply a healing item to a caught Pokemon. Returns a summary for the UI. */
    public Map<String, Object> useItem(int playerId, long caughtId, String item) throws Exception {
        CaughtPokemon p = db.getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");

        int max = p.getHp();
        int cur = p.getCurrentHp();
        boolean fainted = cur <= 0;

        if (db.getItemCount(playerId, item) <= 0)
            throw new IllegalStateException("You have no " + pretty(item));

        int newHp;
        if (POTION.containsKey(item)) {
            if (fainted) throw new IllegalStateException(p.displayName() + " has fainted — use a Revive first");
            if (cur >= max) throw new IllegalStateException(p.displayName() + " is already at full HP");
            int amt = POTION.get(item);
            newHp = amt < 0 ? max : Math.min(max, cur + amt);
        } else if (REVIVE.containsKey(item)) {
            if (!fainted) throw new IllegalStateException(p.displayName() + " hasn't fainted");
            newHp = Math.max(1, (int) Math.round(max * REVIVE.get(item)));
        } else {
            throw new IllegalArgumentException("Not a healing item: " + item);
        }

        db.adjustItem(playerId, item, -1);
        db.updateCurrentHp(caughtId, playerId, newHp);

        return Map.of(
            "success", true,
            "caughtId", caughtId,
            "currentHp", newHp,
            "maxHp", max,
            "restored", newHp - Math.max(0, cur),
            "message", pretty(item) + " used — " + p.displayName() + " now at " + newHp + "/" + max + " HP"
        );
    }

    private String pretty(String item) {
        String s = item.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

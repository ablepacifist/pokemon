package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.CaughtPokemon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvolutionService {

    @Autowired
    private PokemonDatabase db;

    @Autowired
    private MoveService moveService;

    /**
     * Returns all evolution options for a caught Pokemon, with eligibility info.
     * Each entry: { evolvesToId, evolvesToName, minLevel, itemRequired, eligible, reason }
     */
    public List<Map<String, Object>> checkEvolution(long caughtId, int playerId) throws Exception {
        CaughtPokemon p = db.getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");

        List<Map<String, Object>> options = db.getEvolutionsFor(p.getSpeciesId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> opt : options) {
            Map<String, Object> entry = new HashMap<>(opt);
            int minLevel     = (int) opt.get("minLevel");
            String itemReq   = (String) opt.get("itemRequired");
            boolean levelOk  = minLevel == 0 || p.getPokemonLevel() >= minLevel;
            boolean itemOk   = itemReq == null || db.getItemCount(playerId, itemReq) > 0;
            boolean eligible = levelOk && itemOk;

            entry.put("eligible", eligible);
            if (!levelOk) entry.put("reason", "Needs level " + minLevel + " (current: " + p.getPokemonLevel() + ")");
            else if (!itemOk) entry.put("reason", "Needs " + itemReq.replace('_', ' '));
            else entry.put("reason", "Ready to evolve!");
            result.add(entry);
        }
        return result;
    }

    /**
     * Performs the evolution. Consumes the required item if any, updates species,
     * recalculates stats, then assigns any new moves the evolved form learns.
     */
    public Map<String, Object> evolve(long caughtId, int playerId, int targetSpeciesId) throws Exception {
        CaughtPokemon p = db.getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");

        // Find the matching evolution option
        List<Map<String, Object>> opts = db.getEvolutionsFor(p.getSpeciesId());
        Map<String, Object> chosen = opts.stream()
            .filter(o -> (int) o.get("evolvesToId") == targetSpeciesId)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Invalid evolution target"));

        int minLevel   = (int) chosen.get("minLevel");
        String itemReq = (String) chosen.get("itemRequired");

        if (minLevel > 0 && p.getPokemonLevel() < minLevel)
            throw new IllegalStateException("Pokemon must be at least level " + minLevel);
        if (itemReq != null) {
            if (db.getItemCount(playerId, itemReq) < 1)
                throw new IllegalStateException("Missing item: " + itemReq.replace('_', ' '));
            db.adjustItem(playerId, itemReq, -1);
        }

        String oldName = p.getSpeciesName();
        db.evolvePokemon(caughtId, playerId, targetSpeciesId);

        // Assign any learnset moves the new species gets at or below current level
        // that the Pokemon doesn't already have
        try {
            moveService.assignInitialMoves(caughtId, targetSpeciesId, p.getPokemonLevel());
        } catch (Exception ignored) {}

        // Award trainer XP for evolving
        try { db.addXp(playerId, 500); } catch (Exception ignored) {}

        String newName = (String) chosen.get("evolvesToName");
        return Map.of(
            "success",       true,
            "oldName",       oldName,
            "newName",       newName,
            "newSpeciesId",  targetSpeciesId,
            "newSpriteKey",  String.format("pokemon_icon_%03d_00.png", targetSpeciesId)
        );
    }
}

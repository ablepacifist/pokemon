package pokemon.logic;

import java.util.HashMap;
import java.util.Map;

/**
 * Standard 18-type effectiveness chart. Species in our dataset span Gen 1-7,
 * so all 18 types (including Steel / Dark / Fairy) are covered.
 *
 * multiplier(moveType, defType1, defType2) returns the combined effectiveness
 * (0, 0.25, 0.5, 1, 2, or 4). Unknown/empty types default to neutral (1.0).
 */
public final class TypeChart {

    private TypeChart() {}

    // CHART[attackType] -> { defendType -> multiplier }, only non-1.0 entries stored.
    private static final Map<String, Map<String, Double>> CHART = new HashMap<>();

    private static void put(String atk, Object... pairs) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(norm((String) pairs[i]), (Double) pairs[i + 1]);
        }
        CHART.put(norm(atk), m);
    }

    private static String norm(String t) {
        if (t == null || t.isBlank()) return "";
        return t.trim().substring(0, 1).toUpperCase() + t.trim().substring(1).toLowerCase();
    }

    static {
        put("Normal",   "Rock", 0.5, "Ghost", 0.0, "Steel", 0.5);
        put("Fire",     "Grass", 2.0, "Ice", 2.0, "Bug", 2.0, "Steel", 2.0,
                        "Fire", 0.5, "Water", 0.5, "Rock", 0.5, "Dragon", 0.5);
        put("Water",    "Fire", 2.0, "Ground", 2.0, "Rock", 2.0,
                        "Water", 0.5, "Grass", 0.5, "Dragon", 0.5);
        put("Electric", "Water", 2.0, "Flying", 2.0,
                        "Electric", 0.5, "Grass", 0.5, "Dragon", 0.5, "Ground", 0.0);
        put("Grass",    "Water", 2.0, "Ground", 2.0, "Rock", 2.0,
                        "Fire", 0.5, "Grass", 0.5, "Poison", 0.5, "Flying", 0.5,
                        "Bug", 0.5, "Dragon", 0.5, "Steel", 0.5);
        put("Ice",      "Grass", 2.0, "Ground", 2.0, "Flying", 2.0, "Dragon", 2.0,
                        "Fire", 0.5, "Water", 0.5, "Ice", 0.5, "Steel", 0.5);
        put("Fighting", "Normal", 2.0, "Ice", 2.0, "Rock", 2.0, "Dark", 2.0, "Steel", 2.0,
                        "Poison", 0.5, "Flying", 0.5, "Psychic", 0.5, "Bug", 0.5,
                        "Fairy", 0.5, "Ghost", 0.0);
        put("Poison",   "Grass", 2.0, "Fairy", 2.0,
                        "Poison", 0.5, "Ground", 0.5, "Rock", 0.5, "Ghost", 0.5, "Steel", 0.0);
        put("Ground",   "Fire", 2.0, "Electric", 2.0, "Poison", 2.0, "Rock", 2.0, "Steel", 2.0,
                        "Grass", 0.5, "Bug", 0.5, "Flying", 0.0);
        put("Flying",   "Grass", 2.0, "Fighting", 2.0, "Bug", 2.0,
                        "Electric", 0.5, "Rock", 0.5, "Steel", 0.5);
        put("Psychic",  "Fighting", 2.0, "Poison", 2.0,
                        "Psychic", 0.5, "Steel", 0.5, "Dark", 0.0);
        put("Bug",      "Grass", 2.0, "Psychic", 2.0, "Dark", 2.0,
                        "Fire", 0.5, "Fighting", 0.5, "Poison", 0.5, "Flying", 0.5,
                        "Ghost", 0.5, "Steel", 0.5, "Fairy", 0.5);
        put("Rock",     "Fire", 2.0, "Ice", 2.0, "Flying", 2.0, "Bug", 2.0,
                        "Fighting", 0.5, "Ground", 0.5, "Steel", 0.5);
        put("Ghost",    "Psychic", 2.0, "Ghost", 2.0, "Dark", 0.5, "Normal", 0.0);
        put("Dragon",   "Dragon", 2.0, "Steel", 0.5, "Fairy", 0.0);
        put("Dark",     "Psychic", 2.0, "Ghost", 2.0,
                        "Fighting", 0.5, "Dark", 0.5, "Fairy", 0.5);
        put("Steel",    "Ice", 2.0, "Rock", 2.0, "Fairy", 2.0,
                        "Fire", 0.5, "Water", 0.5, "Electric", 0.5, "Steel", 0.5);
        put("Fairy",    "Fighting", 2.0, "Dragon", 2.0, "Dark", 2.0,
                        "Fire", 0.5, "Poison", 0.5, "Steel", 0.5);
    }

    private static double single(String moveType, String defType) {
        if (defType == null || defType.isBlank()) return 1.0;
        Map<String, Double> row = CHART.get(norm(moveType));
        if (row == null) return 1.0;
        return row.getOrDefault(norm(defType), 1.0);
    }

    /** Combined multiplier against a (possibly dual-type) defender. */
    public static double multiplier(String moveType, String defType1, String defType2) {
        return single(moveType, defType1) * single(moveType, defType2);
    }

    /** Human-readable effectiveness label for battle log. */
    public static String label(double mult) {
        if (mult == 0.0)      return "It had no effect...";
        if (mult >= 2.0)      return "It's super effective!";
        if (mult < 1.0)       return "It's not very effective...";
        return "";
    }
}

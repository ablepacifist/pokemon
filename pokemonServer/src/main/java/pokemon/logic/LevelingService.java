package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.CaughtPokemon;
import pokemon.object.PokemonMove;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LevelingService {

    private static final Map<String, Long> CANDY_EXP = Map.of(
        "EXP_CANDY_XS", 100L,
        "EXP_CANDY_S",  800L,
        "EXP_CANDY_M",  3_000L,
        "EXP_CANDY_L",  10_000L,
        "EXP_CANDY_XL", 30_000L
    );

    @Autowired private PokemonDatabase db;
    @Autowired private MoveService moveService;

    public boolean isValidCandyType(String candyType) {
        return CANDY_EXP.containsKey(candyType);
    }

    /**
     * Use EXP candies on a caught Pokemon. Deducts from inventory, adds EXP,
     * handles level-up stat recalc, and auto-assigns any new learnable moves.
     * Returns a result map including any pending moves that require player choice.
     */
    public Map<String, Object> useCandy(int playerId, long targetId, String candyType, int amount)
            throws SQLException {
        if (!CANDY_EXP.containsKey(candyType))
            throw new IllegalArgumentException("Unknown candy type: " + candyType);
        if (amount < 1 || amount > 100)
            throw new IllegalArgumentException("Amount must be 1-100");

        int available = db.getItemCount(playerId, candyType);
        if (available < amount)
            throw new IllegalStateException("Not enough " + candyType
                + " (have " + available + ", need " + amount + ")");

        // Fetch the Pokemon before applying EXP to know its old level and species
        CaughtPokemon pokemon = db.getCaughtById(targetId, playerId);
        if (pokemon == null) throw new IllegalArgumentException("Pokemon not found");
        if (pokemon.getPokemonLevel() >= 100)
            throw new IllegalStateException("Pokemon is already max level (100)");

        int oldLevel = pokemon.getPokemonLevel();

        db.adjustItem(playerId, candyType, -amount);
        long totalExp = CANDY_EXP.get(candyType) * amount;
        Map<String, Object> expResult = db.addPokemonExp(targetId, playerId, totalExp);

        int newLevel = (int) expResult.get("newLevel");
        boolean leveledUp = (boolean) expResult.get("leveledUp");

        List<PokemonMove> pendingMoves = List.of();
        if (leveledUp) {
            pendingMoves = moveService.handleLevelUpMoves(
                targetId, playerId, pokemon.getSpeciesId(), oldLevel, newLevel);
        }

        Map<String, Object> result = new HashMap<>(expResult);
        result.put("pendingMoves", pendingMoves);
        return result;
    }
}

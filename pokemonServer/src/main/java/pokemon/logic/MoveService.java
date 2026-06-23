package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.PokemonMove;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class MoveService {

    @Autowired
    private PokemonDatabase db;

    public List<PokemonMove> getMovesForCaughtPokemon(long caughtId, int playerId) throws SQLException {
        return db.getCaughtPokemonMoves(caughtId, playerId);
    }

    public List<PokemonMove> getLearnsetForSpecies(int speciesId) throws SQLException {
        return db.getLearnsetForSpecies(speciesId);
    }

    /** Called right after a Pokemon is caught. Assigns the last 4 moves it would know at that level. */
    public void assignInitialMoves(long caughtId, int speciesId, int level) throws SQLException {
        List<PokemonMove> learnset = db.getLearnsetUpToLevel(speciesId, level);
        if (learnset.isEmpty()) {
            // Default: Tackle + Growl
            db.upsertCaughtPokemonMove(caughtId, 33, 1);
            db.upsertCaughtPokemonMove(caughtId, 45, 2);
            return;
        }
        // Take up to 4 most recently learned moves (highest level first → last 4 entries)
        int start = Math.max(0, learnset.size() - 4);
        for (int i = start; i < learnset.size(); i++) {
            db.upsertCaughtPokemonMove(caughtId, learnset.get(i).getId(), i - start + 1);
        }
    }

    /**
     * Called after a level-up. Auto-assigns moves if slots are free.
     * Returns any moves that couldn't be auto-assigned (Pokemon already has 4 moves) — caller shows "Replace Move?" UI.
     */
    public List<PokemonMove> handleLevelUpMoves(long caughtId, int playerId,
                                                 int speciesId, int oldLevel, int newLevel) throws SQLException {
        List<PokemonMove> newMoves = db.getLearnsetBetweenLevels(speciesId, oldLevel + 1, newLevel);
        if (newMoves.isEmpty()) return newMoves;

        List<PokemonMove> currentMoves = db.getCaughtPokemonMoves(caughtId, playerId);
        List<PokemonMove> pending = new ArrayList<>();

        for (PokemonMove move : newMoves) {
            // Skip if Pokemon already knows this move
            boolean alreadyKnows = currentMoves.stream().anyMatch(m -> m.getId() == move.getId());
            if (alreadyKnows) continue;

            if (currentMoves.size() < 4) {
                int slot = currentMoves.size() + 1;
                db.upsertCaughtPokemonMove(caughtId, move.getId(), slot);
                currentMoves.add(move);
            } else {
                pending.add(move);
            }
        }
        return pending;
    }

    /** Replace the move in a given slot (1-4) with a new move. */
    public void replaceMove(long caughtId, int playerId, int newMoveId, int slot) throws SQLException {
        if (slot < 1 || slot > 4) throw new IllegalArgumentException("Slot must be 1-4");
        // Verify ownership via existing caught pokemon check
        List<PokemonMove> current = db.getCaughtPokemonMoves(caughtId, playerId);
        if (current.isEmpty()) throw new IllegalArgumentException("Pokemon not found or no moves");
        db.upsertCaughtPokemonMove(caughtId, newMoveId, slot);
    }
}

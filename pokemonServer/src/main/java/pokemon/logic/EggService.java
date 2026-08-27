package pokemon.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pokemon.data.PokemonDatabase;
import pokemon.object.CaughtPokemon;
import pokemon.object.PlayerEgg;
import pokemon.object.PokemonSpecies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Eggs: obtained from PokeStops, incubated, and hatched by walking. The distance
 * accrued by WalkService advances any incubating egg; when it reaches the egg's
 * required distance the egg hatches into a new Pokemon (rarity weighted by tier).
 */
@Service
public class EggService {

    private static final int MAX_EGGS = 9;
    private static final int MAX_INCUBATING = 1;           // one infinite incubator slot
    private static final double[] EGG_TIERS = {2, 5, 10};
    private static final Random RNG = new Random();

    @Autowired private PokemonDatabase db;
    @Autowired private MoveService moveService;

    // ── Acquisition ─────────────────────────────────────────────────────────────

    /** Give the player a random-tier egg if they have room. Returns the km tier, or 0 if full. */
    public double giveRandomEgg(int playerId) throws Exception {
        if (db.countEggs(playerId) >= MAX_EGGS) return 0;
        double roll = RNG.nextDouble();
        double km = roll < 0.60 ? 2 : roll < 0.90 ? 5 : 10;   // 2km common, 5km uncommon, 10km rare
        db.insertEgg(playerId, km);
        return km;
    }

    // ── Incubation ──────────────────────────────────────────────────────────────

    public List<PlayerEgg> getEggs(int playerId) throws Exception {
        return db.getEggs(playerId);
    }

    public void incubate(int playerId, long eggId) throws Exception {
        PlayerEgg egg = db.getEgg(eggId, playerId);
        if (egg == null) throw new IllegalArgumentException("Egg not found");
        if (egg.isIncubating()) throw new IllegalStateException("That egg is already incubating");
        if (db.getIncubatingEggs(playerId).size() >= MAX_INCUBATING)
            throw new IllegalStateException("Your incubator is full — an egg is already incubating");
        db.setEggIncubating(eggId, playerId, true);
    }

    public void stopIncubating(int playerId, long eggId) throws Exception {
        PlayerEgg egg = db.getEgg(eggId, playerId);
        if (egg == null) throw new IllegalArgumentException("Egg not found");
        db.setEggIncubating(eggId, playerId, false);
    }

    // ── Walk progress → hatching ────────────────────────────────────────────────

    /** Advance every incubating egg by deltaKm; hatch those that complete. Returns hatch events. */
    public List<Map<String, Object>> advance(int playerId, double deltaKm) throws Exception {
        List<Map<String, Object>> hatched = new ArrayList<>();
        if (deltaKm <= 0) return hatched;
        for (PlayerEgg egg : db.getIncubatingEggs(playerId)) {
            double progress = egg.getProgressKm() + deltaKm;
            if (progress >= egg.getDistanceKm()) {
                db.deleteEgg(egg.getId());
                hatched.add(hatch(playerId, egg.getDistanceKm()));
            } else {
                db.updateEggProgress(egg.getId(), progress);
            }
        }
        return hatched;
    }

    /** Build and store a hatched Pokemon; award candy + stardust + XP. */
    private Map<String, Object> hatch(int playerId, double tierKm) throws Exception {
        // Rarity band by tier.
        int minR, maxR, minLvl, maxLvl;
        if (tierKm <= 2)      { minR = 1; maxR = 2; minLvl = 1;  maxLvl = 5;  }
        else if (tierKm <= 5) { minR = 2; maxR = 3; minLvl = 5;  maxLvl = 12; }
        else                  { minR = 4; maxR = 5; minLvl = 10; maxLvl = 20; }

        List<PokemonSpecies> pool = new ArrayList<>();
        for (PokemonSpecies sp : db.getAllSpecies())
            if (sp.getRarity() >= minR && sp.getRarity() <= maxR) pool.add(sp);
        if (pool.isEmpty()) pool = db.getAllSpecies();
        PokemonSpecies species = pool.get(RNG.nextInt(pool.size()));

        int level = minLvl + RNG.nextInt(maxLvl - minLvl + 1);
        double iv = 0.90 + RNG.nextDouble() * 0.20;   // eggs hatch with good IVs
        int hp = statAtLevel(species.getBaseHp(), level, iv) + level;

        CaughtPokemon c = new CaughtPokemon();
        c.setPlayerId(playerId);
        c.setSpeciesId(species.getId());
        c.setSpeciesName(species.getName());
        c.setSpriteKey(species.getSpriteKey());
        c.setType1(species.getType1());
        c.setType2(species.getType2());
        c.setPokemonLevel(level);
        c.setHp(hp);
        c.setAttack(statAtLevel(species.getBaseAttack(), level, iv));
        c.setDefense(statAtLevel(species.getBaseDefense(), level, iv));
        c.setSpAtk(statAtLevel(species.getBaseSpAtk(), level, iv));
        c.setSpDef(statAtLevel(species.getBaseSpDef(), level, iv));
        c.setSpeed(statAtLevel(species.getBaseSpeed(), level, iv));
        c.setIv(iv);
        c.setExp(PokemonDatabase.expForLevel(level));

        long id = db.insertCaughtPokemon(c);
        try { moveService.assignInitialMoves(id, species.getId(), level); } catch (Exception ignored) {}

        int candy = (int) (tierKm);   // 2/5/10 candy
        try { db.adjustItem(playerId, "CANDY_XS", candy); } catch (Exception ignored) {}
        try { db.addXp(playerId, (int) (tierKm * 50)); } catch (Exception ignored) {}

        return Map.of(
            "type", "hatch",
            "tierKm", tierKm,
            "speciesName", species.getName(),
            "spriteKey", species.getSpriteKey(),
            "level", level,
            "candy", candy,
            "caughtId", id
        );
    }

    private static int statAtLevel(int base, int level, double iv) {
        return Math.max(1, (int) (base * (level + 50) / 100.0 * iv));
    }
}

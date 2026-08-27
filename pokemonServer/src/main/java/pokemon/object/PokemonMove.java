package pokemon.object;

public class PokemonMove {
    private int id;
    private String name;
    private String type;
    private String category; // Physical, Special, Status
    private int power;
    private int accuracy;
    private int pp;
    private int slot; // 1-4, position in caught Pokemon's moveset (0 if not in a moveset)
    private int levelLearned; // level at which this move is learned (for learnset queries)

    // Battle-effect metadata (from PokeAPI / pokemondb)
    private int priority;        // move priority for turn order
    private int minHits = 1;     // multi-hit lower bound
    private int maxHits = 1;     // multi-hit upper bound
    private String ailment;      // PARALYZE|SLEEP|POISON|BURN|FREEZE|CONFUSE|null
    private int ailmentChance;   // % chance to inflict ailment (0 on a status move = guaranteed)
    private int critRate;        // 0 normal, 1+ high crit
    private int drain;           // + = heal % of damage dealt, - = recoil % of damage dealt
    private int healing;         // % of max HP healed (e.g. Recover)
    private int flinchChance;    // % chance to flinch the target
    private int statChance;      // % chance for stat changes to apply (0 = guaranteed primary)
    private String target;       // "self" or "foe" — who stat changes apply to
    private String statChanges;  // encoded "atk:1|spe:-1" or null

    public PokemonMove() {}

    public PokemonMove(int id, String name, String type, String category, int power, int accuracy, int pp) {
        this.id = id; this.name = name; this.type = type;
        this.category = category; this.power = power;
        this.accuracy = accuracy; this.pp = pp;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getPower() { return power; }
    public void setPower(int power) { this.power = power; }
    public int getAccuracy() { return accuracy; }
    public void setAccuracy(int accuracy) { this.accuracy = accuracy; }
    public int getPp() { return pp; }
    public void setPp(int pp) { this.pp = pp; }
    public int getSlot() { return slot; }
    public void setSlot(int slot) { this.slot = slot; }
    public int getLevelLearned() { return levelLearned; }
    public void setLevelLearned(int levelLearned) { this.levelLearned = levelLearned; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public int getMinHits() { return minHits; }
    public void setMinHits(int minHits) { this.minHits = minHits; }
    public int getMaxHits() { return maxHits; }
    public void setMaxHits(int maxHits) { this.maxHits = maxHits; }
    public String getAilment() { return ailment; }
    public void setAilment(String ailment) { this.ailment = ailment; }
    public int getAilmentChance() { return ailmentChance; }
    public void setAilmentChance(int ailmentChance) { this.ailmentChance = ailmentChance; }
    public int getCritRate() { return critRate; }
    public void setCritRate(int critRate) { this.critRate = critRate; }
    public int getDrain() { return drain; }
    public void setDrain(int drain) { this.drain = drain; }
    public int getHealing() { return healing; }
    public void setHealing(int healing) { this.healing = healing; }
    public int getFlinchChance() { return flinchChance; }
    public void setFlinchChance(int flinchChance) { this.flinchChance = flinchChance; }
    public int getStatChance() { return statChance; }
    public void setStatChance(int statChance) { this.statChance = statChance; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getStatChanges() { return statChanges; }
    public void setStatChanges(String statChanges) { this.statChanges = statChanges; }
}

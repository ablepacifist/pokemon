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
}

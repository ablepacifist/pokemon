package pokemon.object;

public class PokemonSpecies {
    private int id;
    private String name;
    private String type1;
    private String type2;
    private int baseHp;
    private int baseAttack;
    private int baseDefense;
    private int baseSpAtk;
    private int baseSpDef;
    private int baseSpeed;
    private int rarity;
    private String spriteKey;

    public PokemonSpecies() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType1() { return type1; }
    public void setType1(String type1) { this.type1 = type1; }
    public String getType2() { return type2; }
    public void setType2(String type2) { this.type2 = type2; }
    public int getBaseHp() { return baseHp; }
    public void setBaseHp(int baseHp) { this.baseHp = baseHp; }
    public int getBaseAttack() { return baseAttack; }
    public void setBaseAttack(int baseAttack) { this.baseAttack = baseAttack; }
    public int getBaseDefense() { return baseDefense; }
    public void setBaseDefense(int baseDefense) { this.baseDefense = baseDefense; }
    public int getBaseSpAtk() { return baseSpAtk; }
    public void setBaseSpAtk(int baseSpAtk) { this.baseSpAtk = baseSpAtk; }
    public int getBaseSpDef() { return baseSpDef; }
    public void setBaseSpDef(int baseSpDef) { this.baseSpDef = baseSpDef; }
    public int getBaseSpeed() { return baseSpeed; }
    public void setBaseSpeed(int baseSpeed) { this.baseSpeed = baseSpeed; }
    public int getRarity() { return rarity; }
    public void setRarity(int rarity) { this.rarity = rarity; }
    public String getSpriteKey() { return spriteKey; }
    public void setSpriteKey(String spriteKey) { this.spriteKey = spriteKey; }
}

package pokemon.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import pokemon.object.*;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class PokemonDatabase {

    private static HikariDataSource dataSource;

    @Value("${database.url}")
    private String dbUrl;

    @PostConstruct
    public void init() throws Exception {
        Class.forName("org.hsqldb.jdbc.JDBCDriver");
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername("SA");
        config.setMaximumPoolSize(10);
        config.setInitializationFailTimeout(0);
        config.setConnectionTimeout(15000);
        dataSource = new HikariDataSource(config);
        createTables();
        migrateSchema();
        seedSpecies();
        seedMoves();
        seedLearnsets();
        seedEvolutions();
        seedStarterItems();
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // ── Schema ──────────────────────────────────────────────────────────────

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS POKEMON_SPECIES (
                    ID INT PRIMARY KEY,
                    NAME VARCHAR(60),
                    TYPE1 VARCHAR(20),
                    TYPE2 VARCHAR(20),
                    BASE_HP INT,
                    BASE_ATTACK INT,
                    BASE_DEFENSE INT,
                    BASE_SP_ATK INT DEFAULT 0,
                    BASE_SP_DEF INT DEFAULT 0,
                    BASE_SPEED INT DEFAULT 0,
                    RARITY INT,
                    SPRITE_KEY VARCHAR(60)
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS POKEMON_SPAWNS (
                    ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    SPECIES_ID INT,
                    LAT DOUBLE,
                    LNG DOUBLE,
                    SPAWNED_AT TIMESTAMP,
                    EXPIRES_AT TIMESTAMP,
                    CAUGHT_BY_PLAYER INT
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS CAUGHT_POKEMON (
                    ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    PLAYER_ID INT,
                    SPECIES_ID INT,
                    POKEMON_LEVEL INT DEFAULT 1,
                    HP INT,
                    ATTACK INT,
                    DEFENSE INT,
                    SP_ATK INT DEFAULT 0,
                    SP_DEF INT DEFAULT 0,
                    SPEED INT DEFAULT 0,
                    CAUGHT_AT TIMESTAMP,
                    CAUGHT_LAT DOUBLE,
                    CAUGHT_LNG DOUBLE,
                    NICKNAME VARCHAR(30),
                    POKEMON_EXP BIGINT DEFAULT 0,
                    POKEMON_IV DOUBLE DEFAULT 0.95,
                    FAVOURITE BOOLEAN DEFAULT FALSE
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS POKESTOPS (
                    ID BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    NAME VARCHAR(100),
                    LAT DOUBLE,
                    LNG DOUBLE,
                    LAST_SPUN_BY INT,
                    LAST_SPUN_AT TIMESTAMP,
                    BIOME VARCHAR(20) DEFAULT 'NORMAL',
                    LURE_EXPIRES_AT TIMESTAMP
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS PLAYER_POKEMON_ITEMS (
                    PLAYER_ID INT,
                    ITEM_TYPE VARCHAR(30),
                    QUANTITY INT,
                    PRIMARY KEY (PLAYER_ID, ITEM_TYPE)
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS PLAYER_LOCATIONS (
                    PLAYER_ID INT PRIMARY KEY,
                    LAT DOUBLE,
                    LNG DOUBLE,
                    UPDATED_AT TIMESTAMP
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS POKEMON_PLAYER_STATS (
                    PLAYER_ID INT PRIMARY KEY,
                    XP INT DEFAULT 0,
                    POKE_COINS INT DEFAULT 0,
                    STARDUST INT DEFAULT 0
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS POKEMON_EVOLUTIONS (
                    SPECIES_ID INT,
                    EVOLVES_TO_ID INT,
                    MIN_LEVEL INT DEFAULT 0,
                    ITEM_REQUIRED VARCHAR(30),
                    PRIMARY KEY (SPECIES_ID, EVOLVES_TO_ID)
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS POKEMON_MOVES (
                    ID INT PRIMARY KEY,
                    NAME VARCHAR(50),
                    TYPE VARCHAR(20),
                    CATEGORY VARCHAR(10),
                    POWER INT DEFAULT 0,
                    ACCURACY INT DEFAULT 0,
                    PP INT DEFAULT 35
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS POKEMON_LEARNSET (
                    SPECIES_ID INT,
                    MOVE_ID INT,
                    LEVEL_LEARNED INT,
                    PRIMARY KEY (SPECIES_ID, MOVE_ID, LEVEL_LEARNED)
                )""");

            s.execute("""
                CREATE TABLE IF NOT EXISTS CAUGHT_POKEMON_MOVES (
                    CAUGHT_ID BIGINT,
                    MOVE_ID INT,
                    SLOT INT,
                    PRIMARY KEY (CAUGHT_ID, SLOT)
                )""");

            conn.commit();
        }
    }

    /**
     * Adds columns that may not exist on databases created before this version.
     * Each ALTER is wrapped individually so one failure doesn't block the rest.
     */
    private void migrateSchema() {
        try (Connection conn = getConnection(); Statement s = conn.createStatement()) {
            // POKEMON_SPECIES: real base stats
            runSilent(s, "ALTER TABLE POKEMON_SPECIES ADD COLUMN BASE_SP_ATK INT DEFAULT 0");
            runSilent(s, "ALTER TABLE POKEMON_SPECIES ADD COLUMN BASE_SP_DEF INT DEFAULT 0");
            runSilent(s, "ALTER TABLE POKEMON_SPECIES ADD COLUMN BASE_SPEED INT DEFAULT 0");
            // CAUGHT_POKEMON: level replaces CP; add full stat set; add EXP/IV for leveling
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN POKEMON_LEVEL INT DEFAULT 1");
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN SP_ATK INT DEFAULT 0");
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN SP_DEF INT DEFAULT 0");
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN SPEED INT DEFAULT 0");
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN POKEMON_EXP BIGINT DEFAULT 0");
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN POKEMON_IV DOUBLE DEFAULT 0.95");
            runSilent(s, "ALTER TABLE CAUGHT_POKEMON ADD COLUMN FAVOURITE BOOLEAN DEFAULT FALSE");
            runSilent(s, "ALTER TABLE POKEMON_PLAYER_STATS ADD COLUMN STARDUST INT DEFAULT 0");
            runSilent(s, "ALTER TABLE POKESTOPS ADD COLUMN BIOME VARCHAR(20) DEFAULT 'NORMAL'");
            runSilent(s, "ALTER TABLE POKESTOPS ADD COLUMN LURE_EXPIRES_AT TIMESTAMP");
            conn.commit();
        } catch (Exception e) {
            System.err.println("Schema migration warning: " + e.getMessage());
        }
    }

    private void runSilent(Statement s, String sql) {
        try { s.execute(sql); } catch (SQLException ignored) {}
    }

    // ── Seeding ──────────────────────────────────────────────────────────────

    private void seedSpecies() {
        // Re-seed if BASE_SP_ATK is still all zeros (old data) or table is empty
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM POKEMON_SPECIES WHERE BASE_SP_ATK > 0");
            rs.next();
            if (rs.getInt(1) > 0) return; // already seeded with real stats
            st.execute("DELETE FROM POKEMON_SPECIES");
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Species pre-seed check failed: " + e.getMessage());
            return;
        }

        try (var in = getClass().getClassLoader().getResourceAsStream("species.csv");
             var reader = new BufferedReader(new InputStreamReader(in));
             Connection conn = getConnection()) {

            PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO POKEMON_SPECIES
                (ID,NAME,TYPE1,TYPE2,RARITY,BASE_HP,BASE_ATTACK,BASE_DEFENSE,BASE_SP_ATK,BASE_SP_DEF,BASE_SPEED,SPRITE_KEY)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""");

            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",", -1);
                if (p.length < 11) continue;

                int id      = Integer.parseInt(p[0].trim());
                String name = p[1].trim();
                String t1   = p[2].trim();
                String t2   = p[3].trim().isEmpty() ? null : p[3].trim();
                int rarity  = Integer.parseInt(p[4].trim());
                int hp      = Integer.parseInt(p[5].trim());
                int atk     = Integer.parseInt(p[6].trim());
                int def     = Integer.parseInt(p[7].trim());
                int spAtk   = Integer.parseInt(p[8].trim());
                int spDef   = Integer.parseInt(p[9].trim());
                int speed   = Integer.parseInt(p[10].trim());
                String sprite = String.format("pokemon_icon_%03d_00.png", id);

                ps.setInt(1, id);
                ps.setString(2, name);
                ps.setString(3, t1);
                ps.setString(4, t2);
                ps.setInt(5, rarity);
                ps.setInt(6, hp);
                ps.setInt(7, atk);
                ps.setInt(8, def);
                ps.setInt(9, spAtk);
                ps.setInt(10, spDef);
                ps.setInt(11, speed);
                ps.setString(12, sprite);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Pokemon species seeded with real base stats.");
        } catch (Exception e) {
            System.err.println("Species seeding failed: " + e.getMessage());
        }
    }

    private void seedStarterItems() {
        try (Connection conn = getConnection()) {
            ResultSet players = conn.createStatement().executeQuery("SELECT ID FROM PLAYERS");
            PreparedStatement check = conn.prepareStatement(
                "SELECT COUNT(*) FROM PLAYER_POKEMON_ITEMS WHERE PLAYER_ID=? AND ITEM_TYPE=?");
            PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO PLAYER_POKEMON_ITEMS (PLAYER_ID,ITEM_TYPE,QUANTITY) VALUES (?,?,?)");

            while (players.next()) {
                int pid = players.getInt("ID");
                for (String[] item : new String[][]{
                        {"POKEBALL","20"}, {"GREAT_BALL","5"}, {"ULTRA_BALL","3"}, {"POTION","10"}, {"REVIVE","3"}}) {
                    check.setInt(1, pid);
                    check.setString(2, item[0]);
                    ResultSet cr = check.executeQuery();
                    cr.next();
                    if (cr.getInt(1) == 0) {
                        insert.setInt(1, pid);
                        insert.setString(2, item[0]);
                        insert.setInt(3, Integer.parseInt(item[1]));
                        insert.addBatch();
                    }
                }
            }
            insert.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            System.err.println("Starter item seeding failed: " + e.getMessage());
        }
    }

    // ── Spawn queries ─────────────────────────────────────────────────────────

    public List<PokemonSpawn> getActiveSpawns() throws SQLException {
        List<PokemonSpawn> list = new ArrayList<>();
        String sql = """
            SELECT s.*, sp.NAME, sp.SPRITE_KEY FROM POKEMON_SPAWNS s
            JOIN POKEMON_SPECIES sp ON s.SPECIES_ID = sp.ID
            WHERE s.EXPIRES_AT > CURRENT_TIMESTAMP AND s.CAUGHT_BY_PLAYER IS NULL""";
        try (Connection conn = getConnection(); ResultSet rs = conn.createStatement().executeQuery(sql)) {
            while (rs.next()) list.add(mapSpawn(rs));
        }
        return list;
    }

    public PokemonSpawn getSpawnById(long id) throws SQLException {
        String sql = """
            SELECT s.*, sp.NAME, sp.SPRITE_KEY FROM POKEMON_SPAWNS s
            JOIN POKEMON_SPECIES sp ON s.SPECIES_ID = sp.ID
            WHERE s.ID = ?""";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapSpawn(rs) : null;
        }
    }

    public void markSpawnCaught(long spawnId, int playerId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE POKEMON_SPAWNS SET CAUGHT_BY_PLAYER=? WHERE ID=?")) {
            ps.setInt(1, playerId);
            ps.setLong(2, spawnId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    public void insertSpawn(int speciesId, double lat, double lng, Instant expiresAt) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO POKEMON_SPAWNS (SPECIES_ID,LAT,LNG,SPAWNED_AT,EXPIRES_AT) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, speciesId);
            ps.setDouble(2, lat);
            ps.setDouble(3, lng);
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.setTimestamp(5, Timestamp.from(expiresAt));
            ps.executeUpdate();
            conn.commit();
        }
    }

    public void deleteExpiredSpawns() throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM POKEMON_SPAWNS WHERE EXPIRES_AT < CURRENT_TIMESTAMP")) {
            ps.executeUpdate();
            conn.commit();
        }
    }

    // ── Caught Pokemon ────────────────────────────────────────────────────────

    public long insertCaughtPokemon(CaughtPokemon p) throws SQLException {
        String sql = """
            INSERT INTO CAUGHT_POKEMON
            (PLAYER_ID,SPECIES_ID,POKEMON_LEVEL,HP,ATTACK,DEFENSE,SP_ATK,SP_DEF,SPEED,
             CAUGHT_AT,CAUGHT_LAT,CAUGHT_LNG,POKEMON_EXP,POKEMON_IV)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getPlayerId());
            ps.setInt(2, p.getSpeciesId());
            ps.setInt(3, p.getPokemonLevel());
            ps.setInt(4, p.getHp());
            ps.setInt(5, p.getAttack());
            ps.setInt(6, p.getDefense());
            ps.setInt(7, p.getSpAtk());
            ps.setInt(8, p.getSpDef());
            ps.setInt(9, p.getSpeed());
            ps.setTimestamp(10, Timestamp.from(Instant.now()));
            ps.setDouble(11, p.getCaughtLat());
            ps.setDouble(12, p.getCaughtLng());
            ps.setLong(13, p.getExp());
            ps.setDouble(14, p.getIv() > 0 ? p.getIv() : 0.95);
            ps.executeUpdate();
            conn.commit();
            ResultSet keys = ps.getGeneratedKeys();
            return keys.next() ? keys.getLong(1) : -1;
        }
    }

    public CaughtPokemon getCaughtById(long id, int playerId) throws SQLException {
        String sql = """
            SELECT c.*, sp.NAME, sp.SPRITE_KEY, sp.TYPE1, sp.TYPE2,
                   sp.BASE_HP, sp.BASE_ATTACK, sp.BASE_DEFENSE, sp.BASE_SP_ATK, sp.BASE_SP_DEF, sp.BASE_SPEED
            FROM CAUGHT_POKEMON c JOIN POKEMON_SPECIES sp ON c.SPECIES_ID=sp.ID
            WHERE c.ID=? AND c.PLAYER_ID=?""";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setInt(2, playerId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            CaughtPokemon p = mapCaught(rs);
            p.setBaseHpForGrind(rs.getInt("BASE_HP"));
            p.setBaseAtkForGrind(rs.getInt("BASE_ATTACK"));
            p.setBaseDefForGrind(rs.getInt("BASE_DEFENSE"));
            p.setBaseSpAtkForGrind(rs.getInt("BASE_SP_ATK"));
            p.setBaseSpDefForGrind(rs.getInt("BASE_SP_DEF"));
            p.setBaseSpeedForGrind(rs.getInt("BASE_SPEED"));
            return p;
        }
    }

    /** Grind (sacrifice) a caught Pokemon → delete it, award EXP candy to the player. */
    public Map<String, Object> grindPokemon(long caughtId, int playerId) throws SQLException {
        CaughtPokemon p = getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");

        int level = p.getPokemonLevel();
        String candyType;
        int amount;
        if      (level <= 15)  { candyType = "EXP_CANDY_XS"; amount = 3; }
        else if (level <= 30)  { candyType = "EXP_CANDY_S";  amount = 2; }
        else if (level <= 50)  { candyType = "EXP_CANDY_M";  amount = 1; }
        else if (level <= 75)  { candyType = "EXP_CANDY_L";  amount = 1; }
        else                   { candyType = "EXP_CANDY_XL"; amount = 1; }

        try (Connection conn = getConnection()) {
            PreparedStatement del = conn.prepareStatement(
                "DELETE FROM CAUGHT_POKEMON WHERE ID=? AND PLAYER_ID=?");
            del.setLong(1, caughtId); del.setInt(2, playerId);
            int rows = del.executeUpdate();
            if (rows == 0) throw new IllegalArgumentException("Pokemon not found");
            PreparedStatement delMoves = conn.prepareStatement(
                "DELETE FROM CAUGHT_POKEMON_MOVES WHERE CAUGHT_ID=?");
            delMoves.setLong(1, caughtId);
            delMoves.executeUpdate();
            conn.commit();
        }
        adjustItem(playerId, candyType, amount);
        return Map.of("candyType", candyType, "amount", amount,
                      "pokemonName", p.getSpeciesName() != null ? p.getSpeciesName() : "Pokemon");
    }

    /**
     * Add EXP to a caught Pokemon. Handles leveling up (including stat recalculation).
     * Returns a summary map describing the result.
     */
    public Map<String, Object> addPokemonExp(long caughtId, int playerId, long expAmount) throws SQLException {
        CaughtPokemon p = getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");
        if (p.getPokemonLevel() >= 100) throw new IllegalStateException("Pokemon is already max level (100)");

        long newExp   = p.getExp() + expAmount;
        int  oldLevel = p.getPokemonLevel();
        int  newLevel = Math.min(100, levelForExp(newExp));

        if (newLevel > oldLevel) {
            // Recalculate all stats at new level using stored IV
            double iv = p.getIv() > 0 ? p.getIv() : 0.95;
            // Base stats were loaded via getCaughtById; fall back to species lookup if 0
            int bHp = p.getBaseHpForGrind(), bAtk = p.getBaseAtkForGrind();
            int bDef = p.getBaseDefForGrind(), bSpA = p.getBaseSpAtkForGrind();
            int bSpD = p.getBaseSpDefForGrind(), bSpd = p.getBaseSpeedForGrind();
            if (bHp == 0) {
                PokemonSpecies sp = getSpeciesById(p.getSpeciesId());
                if (sp != null) {
                    bHp = sp.getBaseHp(); bAtk = sp.getBaseAttack(); bDef = sp.getBaseDefense();
                    bSpA = sp.getBaseSpAtk(); bSpD = sp.getBaseSpDef(); bSpd = sp.getBaseSpeed();
                }
            }
            int hp    = statAtLvl(bHp,  newLevel, iv) + newLevel;
            int atk   = statAtLvl(bAtk, newLevel, iv);
            int def   = statAtLvl(bDef, newLevel, iv);
            int spAtk = statAtLvl(bSpA, newLevel, iv);
            int spDef = statAtLvl(bSpD, newLevel, iv);
            int speed = statAtLvl(bSpd, newLevel, iv);

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                    UPDATE CAUGHT_POKEMON
                    SET POKEMON_LEVEL=?,HP=?,ATTACK=?,DEFENSE=?,SP_ATK=?,SP_DEF=?,SPEED=?,POKEMON_EXP=?
                    WHERE ID=? AND PLAYER_ID=?""")) {
                ps.setInt(1, newLevel); ps.setInt(2, hp);  ps.setInt(3, atk);  ps.setInt(4, def);
                ps.setInt(5, spAtk);   ps.setInt(6, spDef); ps.setInt(7, speed); ps.setLong(8, newExp);
                ps.setLong(9, caughtId); ps.setInt(10, playerId);
                ps.executeUpdate();
                conn.commit();
            }
        } else {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                    "UPDATE CAUGHT_POKEMON SET POKEMON_EXP=? WHERE ID=? AND PLAYER_ID=?")) {
                ps.setLong(1, newExp); ps.setLong(2, caughtId); ps.setInt(3, playerId);
                ps.executeUpdate();
                conn.commit();
            }
        }

        long expForCurrent = expForLevel(newLevel);
        long expForNext    = newLevel < 100 ? expForLevel(newLevel + 1) : expForCurrent;
        return Map.of(
            "leveledUp",          newLevel > oldLevel,
            "oldLevel",           oldLevel,
            "newLevel",           newLevel,
            "exp",                newExp,
            "expForCurrentLevel", expForCurrent,
            "expToNextLevel",     Math.max(0, expForNext - newExp),
            "totalExpThisLevel",  expForNext - expForCurrent
        );
    }

    // ── EXP / level helpers ────────────────────────────────────────────────────

    /** Medium-Fast group: total EXP needed to reach level n = n³ */
    public static long expForLevel(int level) {
        return (long) level * level * level;
    }

    /** Highest level whose total EXP threshold ≤ the given exp. */
    public static int levelForExp(long exp) {
        for (int lv = 100; lv >= 1; lv--) {
            if (exp >= (long) lv * lv * lv) return lv;
        }
        return 1;
    }

    private static int statAtLvl(int base, int level, double iv) {
        return Math.max(1, (int) (base * (level + 50) / 100.0 * iv));
    }

    public List<CaughtPokemon> getCaughtByPlayer(int playerId) throws SQLException {
        List<CaughtPokemon> list = new ArrayList<>();
        String sql = """
            SELECT c.*, sp.NAME, sp.SPRITE_KEY, sp.TYPE1, sp.TYPE2
            FROM CAUGHT_POKEMON c JOIN POKEMON_SPECIES sp ON c.SPECIES_ID=sp.ID
            WHERE c.PLAYER_ID=? ORDER BY c.CAUGHT_AT DESC""";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapCaught(rs));
        }
        return list;
    }

    /** Returns distinct species IDs that this player has caught at least once. */
    public Set<Integer> getCaughtSpeciesIds(int playerId) throws SQLException {
        Set<Integer> ids = new HashSet<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT SPECIES_ID FROM CAUGHT_POKEMON WHERE PLAYER_ID=?")) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) ids.add(rs.getInt(1));
        }
        return ids;
    }

    public void nicknamePokemon(long caughtId, int playerId, String nickname) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE CAUGHT_POKEMON SET NICKNAME=? WHERE ID=? AND PLAYER_ID=?")) {
            ps.setString(1, nickname);
            ps.setLong(2, caughtId);
            ps.setInt(3, playerId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    // ── Pokestops ─────────────────────────────────────────────────────────────

    public List<Pokestop> getAllPokestops() throws SQLException {
        List<Pokestop> list = new ArrayList<>();
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM POKESTOPS")) {
            while (rs.next()) list.add(mapStop(rs));
        }
        return list;
    }

    public Pokestop getPokestopById(long id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM POKESTOPS WHERE ID=?")) {
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapStop(rs) : null;
        }
    }

    public void spinPokestop(long stopId, int playerId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE POKESTOPS SET LAST_SPUN_BY=?, LAST_SPUN_AT=CURRENT_TIMESTAMP WHERE ID=?")) {
            ps.setInt(1, playerId);
            ps.setLong(2, stopId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    public void addPokestop(String name, double lat, double lng, String biome) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO POKESTOPS (NAME,LAT,LNG,BIOME) VALUES (?,?,?,?)")) {
            ps.setString(1, name);
            ps.setDouble(2, lat);
            ps.setDouble(3, lng);
            ps.setString(4, biome != null ? biome : "NORMAL");
            ps.executeUpdate();
            conn.commit();
        }
    }

    public void lurePokestop(long stopId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE POKESTOPS SET LURE_EXPIRES_AT=? WHERE ID=?")) {
            ps.setTimestamp(1, Timestamp.from(Instant.now().plusSeconds(1800)));
            ps.setLong(2, stopId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    public List<PlayerItem> getPlayerItems(int playerId) throws SQLException {
        List<PlayerItem> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM PLAYER_POKEMON_ITEMS WHERE PLAYER_ID=?")) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new PlayerItem(
                rs.getInt("PLAYER_ID"), rs.getString("ITEM_TYPE"), rs.getInt("QUANTITY")));
        }
        return list;
    }

    public int getItemCount(int playerId, String itemType) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT QUANTITY FROM PLAYER_POKEMON_ITEMS WHERE PLAYER_ID=? AND ITEM_TYPE=?")) {
            ps.setInt(1, playerId);
            ps.setString(2, itemType);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("QUANTITY") : 0;
        }
    }

    public void adjustItem(int playerId, String itemType, int delta) throws SQLException {
        try (Connection conn = getConnection()) {
            PreparedStatement check = conn.prepareStatement(
                "SELECT QUANTITY FROM PLAYER_POKEMON_ITEMS WHERE PLAYER_ID=? AND ITEM_TYPE=?");
            check.setInt(1, playerId);
            check.setString(2, itemType);
            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                int newQty = Math.max(0, rs.getInt("QUANTITY") + delta);
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE PLAYER_POKEMON_ITEMS SET QUANTITY=? WHERE PLAYER_ID=? AND ITEM_TYPE=?");
                upd.setInt(1, newQty);
                upd.setInt(2, playerId);
                upd.setString(3, itemType);
                upd.executeUpdate();
            } else if (delta > 0) {
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO PLAYER_POKEMON_ITEMS (PLAYER_ID,ITEM_TYPE,QUANTITY) VALUES (?,?,?)");
                ins.setInt(1, playerId);
                ins.setString(2, itemType);
                ins.setInt(3, delta);
                ins.executeUpdate();
            }
            conn.commit();
        }
    }

    // ── Species ────────────────────────────────────────────────────────────────

    public List<PokemonSpecies> getAllSpecies() throws SQLException {
        List<PokemonSpecies> list = new ArrayList<>();
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                "SELECT * FROM POKEMON_SPECIES ORDER BY ID")) {
            while (rs.next()) list.add(mapSpecies(rs));
        }
        return list;
    }

    public PokemonSpecies getSpeciesById(int id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM POKEMON_SPECIES WHERE ID=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? mapSpecies(rs) : null;
        }
    }

    // ── Player locations ──────────────────────────────────────────────────────

    public void updatePlayerLocation(int playerId, double lat, double lng) throws SQLException {
        try (Connection conn = getConnection()) {
            PreparedStatement check = conn.prepareStatement(
                "SELECT PLAYER_ID FROM PLAYER_LOCATIONS WHERE PLAYER_ID=?");
            check.setInt(1, playerId);
            ResultSet rs = check.executeQuery();
            if (rs.next()) {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE PLAYER_LOCATIONS SET LAT=?,LNG=?,UPDATED_AT=CURRENT_TIMESTAMP WHERE PLAYER_ID=?");
                upd.setDouble(1, lat); upd.setDouble(2, lng); upd.setInt(3, playerId);
                upd.executeUpdate();
            } else {
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO PLAYER_LOCATIONS (PLAYER_ID,LAT,LNG,UPDATED_AT) VALUES (?,?,?,CURRENT_TIMESTAMP)");
                ins.setInt(1, playerId); ins.setDouble(2, lat); ins.setDouble(3, lng);
                ins.executeUpdate();
            }
            conn.commit();
        }
    }

    public List<double[]> getRecentPlayerLocations() throws SQLException {
        List<double[]> list = new ArrayList<>();
        try (Connection conn = getConnection();
             ResultSet rs = conn.createStatement().executeQuery(
                "SELECT LAT,LNG FROM PLAYER_LOCATIONS WHERE UPDATED_AT > DATEADD('MINUTE',-30,CURRENT_TIMESTAMP)")) {
            while (rs.next()) list.add(new double[]{rs.getDouble("LAT"), rs.getDouble("LNG")});
        }
        return list;
    }

    public int countActiveSpawnsNear(double lat, double lng, double radiusMeters) throws SQLException {
        List<PokemonSpawn> all = getActiveSpawns();
        int count = 0;
        for (PokemonSpawn s : all) {
            double dLat = Math.toRadians(s.getLat() - lat);
            double dLng = Math.toRadians(s.getLng() - lng);
            double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                     + Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(s.getLat()))
                     * Math.sin(dLng/2)*Math.sin(dLng/2);
            double dist = 6_371_000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
            if (dist <= radiusMeters) count++;
        }
        return count;
    }

    // ── Player Stats ──────────────────────────────────────────────────────────

    public int[] getPlayerStats(int playerId) throws SQLException {
        try (Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT XP, POKE_COINS, STARDUST FROM POKEMON_PLAYER_STATS WHERE PLAYER_ID=?");
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int stardust = 0;
                try { stardust = rs.getInt("STARDUST"); } catch (SQLException ignored) {}
                return new int[]{rs.getInt("XP"), rs.getInt("POKE_COINS"), stardust};
            }
            PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO POKEMON_PLAYER_STATS (PLAYER_ID, XP, POKE_COINS, STARDUST) VALUES (?,0,100,0)");
            ins.setInt(1, playerId);
            ins.executeUpdate();
            conn.commit();
            return new int[]{0, 100, 0};
        }
    }

    public void addXp(int playerId, int xpToAdd) throws SQLException {
        getPlayerStats(playerId); // ensure row exists
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE POKEMON_PLAYER_STATS SET XP = XP + ? WHERE PLAYER_ID=?")) {
            ps.setInt(1, Math.max(0, xpToAdd));
            ps.setInt(2, playerId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    public void addCoins(int playerId, int delta) throws SQLException {
        int[] stats = getPlayerStats(playerId);
        int newCoins = Math.max(0, stats[1] + delta);
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE POKEMON_PLAYER_STATS SET POKE_COINS=? WHERE PLAYER_ID=?")) {
            ps.setInt(1, newCoins);
            ps.setInt(2, playerId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    public int countCaughtByPlayer(int playerId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM CAUGHT_POKEMON WHERE PLAYER_ID=?")) {
            ps.setInt(1, playerId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // ── Auth helper ───────────────────────────────────────────────────────────

    public Map<String, Object> getPlayerByUsername(String username) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT ID, USERNAME, PASSWORD FROM PLAYERS WHERE USERNAME=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> p = new HashMap<>();
            p.put("id", rs.getInt("ID"));
            p.put("username", rs.getString("USERNAME"));
            p.put("password", rs.getString("PASSWORD"));
            return p;
        }
    }

    public Map<String, Object> getPlayerById(int id) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT ID, USERNAME FROM PLAYERS WHERE ID=?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, Object> p = new HashMap<>();
            p.put("id", rs.getInt("ID"));
            p.put("username", rs.getString("USERNAME"));
            return p;
        }
    }

    // ── Move seeding ─────────────────────────────────────────────────────────

    private void seedMoves() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM POKEMON_MOVES");
            rs.next();
            if (rs.getInt(1) > 0) return;
        } catch (SQLException e) { System.err.println("Move seed check failed: " + e.getMessage()); return; }

        try (var in = getClass().getClassLoader().getResourceAsStream("moves.csv");
             var reader = new BufferedReader(new InputStreamReader(in));
             Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO POKEMON_MOVES (ID,NAME,TYPE,CATEGORY,POWER,ACCURACY,PP) VALUES (?,?,?,?,?,?,?)");
            String line; boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",", 7);
                if (p.length < 7) continue;
                ps.setInt(1, Integer.parseInt(p[0].trim()));
                ps.setString(2, p[1].trim());
                ps.setString(3, p[2].trim());
                ps.setString(4, p[3].trim());
                ps.setInt(5, Integer.parseInt(p[4].trim()));
                ps.setInt(6, Integer.parseInt(p[5].trim()));
                ps.setInt(7, Integer.parseInt(p[6].trim()));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Pokemon moves seeded.");
        } catch (Exception e) { System.err.println("Move seeding failed: " + e.getMessage()); }
    }

    private void seedLearnsets() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM POKEMON_LEARNSET");
            rs.next();
            if (rs.getInt(1) > 0) return;
        } catch (SQLException e) { System.err.println("Learnset seed check failed: " + e.getMessage()); return; }

        try (var in = getClass().getClassLoader().getResourceAsStream("learnsets.csv");
             var reader = new BufferedReader(new InputStreamReader(in));
             Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO POKEMON_LEARNSET (SPECIES_ID,MOVE_ID,LEVEL_LEARNED) VALUES (?,?,?)");
            String line; boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",", 3);
                if (p.length < 3) continue;
                ps.setInt(1, Integer.parseInt(p[0].trim()));
                ps.setInt(2, Integer.parseInt(p[1].trim()));
                ps.setInt(3, Integer.parseInt(p[2].trim()));
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Pokemon learnsets seeded.");
        } catch (Exception e) { System.err.println("Learnset seeding failed: " + e.getMessage()); }
    }

    private void seedEvolutions() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM POKEMON_EVOLUTIONS");
            rs.next();
            if (rs.getInt(1) > 0) return;
        } catch (SQLException e) { System.err.println("Evolution seed check failed: " + e.getMessage()); return; }

        try (var in = getClass().getClassLoader().getResourceAsStream("evolutions.csv");
             var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in));
             Connection conn = getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO POKEMON_EVOLUTIONS (SPECIES_ID,EVOLVES_TO_ID,MIN_LEVEL,ITEM_REQUIRED) VALUES (?,?,?,?)");
            String line; boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",", -1);
                if (p.length < 4) continue;
                ps.setInt(1, Integer.parseInt(p[0].trim()));
                ps.setInt(2, Integer.parseInt(p[1].trim()));
                ps.setInt(3, p[2].trim().isEmpty() ? 0 : Integer.parseInt(p[2].trim()));
                String item = p[3].trim();
                ps.setString(4, item.isEmpty() ? null : item);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Pokemon evolutions seeded.");
        } catch (Exception e) { System.err.println("Evolution seeding failed: " + e.getMessage()); }
    }

    // ── Move CRUD ─────────────────────────────────────────────────────────────

    public List<pokemon.object.PokemonMove> getCaughtPokemonMoves(long caughtId, int playerId) throws SQLException {
        // Verify ownership by joining with CAUGHT_POKEMON
        String sql = """
            SELECT m.*, cpm.SLOT FROM POKEMON_MOVES m
            JOIN CAUGHT_POKEMON_MOVES cpm ON m.ID = cpm.MOVE_ID
            JOIN CAUGHT_POKEMON cp ON cpm.CAUGHT_ID = cp.ID
            WHERE cpm.CAUGHT_ID = ? AND cp.PLAYER_ID = ?
            ORDER BY cpm.SLOT""";
        List<pokemon.object.PokemonMove> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, caughtId); ps.setInt(2, playerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapMove(rs));
        }
        return list;
    }

    public List<pokemon.object.PokemonMove> getLearnsetForSpecies(int speciesId) throws SQLException {
        String sql = """
            SELECT m.*, ls.LEVEL_LEARNED FROM POKEMON_MOVES m
            JOIN POKEMON_LEARNSET ls ON m.ID = ls.MOVE_ID
            WHERE ls.SPECIES_ID = ?
            ORDER BY ls.LEVEL_LEARNED, m.ID""";
        List<pokemon.object.PokemonMove> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, speciesId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                pokemon.object.PokemonMove m = mapMove(rs);
                m.setLevelLearned(rs.getInt("LEVEL_LEARNED"));
                list.add(m);
            }
        }
        return list;
    }

    public List<pokemon.object.PokemonMove> getLearnsetUpToLevel(int speciesId, int level) throws SQLException {
        String sql = """
            SELECT m.*, ls.LEVEL_LEARNED FROM POKEMON_MOVES m
            JOIN POKEMON_LEARNSET ls ON m.ID = ls.MOVE_ID
            WHERE ls.SPECIES_ID = ? AND ls.LEVEL_LEARNED <= ?
            ORDER BY ls.LEVEL_LEARNED, m.ID""";
        List<pokemon.object.PokemonMove> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, speciesId); ps.setInt(2, level);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                pokemon.object.PokemonMove m = mapMove(rs);
                m.setLevelLearned(rs.getInt("LEVEL_LEARNED"));
                list.add(m);
            }
        }
        return list;
    }

    public List<pokemon.object.PokemonMove> getLearnsetBetweenLevels(int speciesId, int minLevel, int maxLevel) throws SQLException {
        String sql = """
            SELECT m.*, ls.LEVEL_LEARNED FROM POKEMON_MOVES m
            JOIN POKEMON_LEARNSET ls ON m.ID = ls.MOVE_ID
            WHERE ls.SPECIES_ID = ? AND ls.LEVEL_LEARNED >= ? AND ls.LEVEL_LEARNED <= ?
            ORDER BY ls.LEVEL_LEARNED, m.ID""";
        List<pokemon.object.PokemonMove> list = new ArrayList<>();
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, speciesId); ps.setInt(2, minLevel); ps.setInt(3, maxLevel);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                pokemon.object.PokemonMove m = mapMove(rs);
                m.setLevelLearned(rs.getInt("LEVEL_LEARNED"));
                list.add(m);
            }
        }
        return list;
    }

    public void upsertCaughtPokemonMove(long caughtId, int moveId, int slot) throws SQLException {
        try (Connection conn = getConnection()) {
            PreparedStatement check = conn.prepareStatement(
                "SELECT 1 FROM CAUGHT_POKEMON_MOVES WHERE CAUGHT_ID=? AND SLOT=?");
            check.setLong(1, caughtId); check.setInt(2, slot);
            if (check.executeQuery().next()) {
                PreparedStatement upd = conn.prepareStatement(
                    "UPDATE CAUGHT_POKEMON_MOVES SET MOVE_ID=? WHERE CAUGHT_ID=? AND SLOT=?");
                upd.setInt(1, moveId); upd.setLong(2, caughtId); upd.setInt(3, slot);
                upd.executeUpdate();
            } else {
                PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO CAUGHT_POKEMON_MOVES (CAUGHT_ID,MOVE_ID,SLOT) VALUES (?,?,?)");
                ins.setLong(1, caughtId); ins.setInt(2, moveId); ins.setInt(3, slot);
                ins.executeUpdate();
            }
            conn.commit();
        }
    }

    public void deleteCaughtPokemonMoves(long caughtId) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM CAUGHT_POKEMON_MOVES WHERE CAUGHT_ID=?")) {
            ps.setLong(1, caughtId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    private pokemon.object.PokemonMove mapMove(ResultSet rs) throws SQLException {
        pokemon.object.PokemonMove m = new pokemon.object.PokemonMove();
        m.setId(rs.getInt("ID"));
        m.setName(rs.getString("NAME"));
        m.setType(rs.getString("TYPE"));
        m.setCategory(rs.getString("CATEGORY"));
        m.setPower(rs.getInt("POWER"));
        m.setAccuracy(rs.getInt("ACCURACY"));
        m.setPp(rs.getInt("PP"));
        try { m.setSlot(rs.getInt("SLOT")); } catch (SQLException ignored) {}
        return m;
    }

    // ── Evolution CRUD ────────────────────────────────────────────────────────

    public List<Map<String, Object>> getEvolutionsFor(int speciesId) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = """
            SELECT e.EVOLVES_TO_ID, e.MIN_LEVEL, e.ITEM_REQUIRED, sp.NAME
            FROM POKEMON_EVOLUTIONS e JOIN POKEMON_SPECIES sp ON e.EVOLVES_TO_ID = sp.ID
            WHERE e.SPECIES_ID = ?""";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, speciesId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> m = new HashMap<>();
                m.put("evolvesToId",   rs.getInt("EVOLVES_TO_ID"));
                m.put("evolvesToName", rs.getString("NAME"));
                m.put("minLevel",      rs.getInt("MIN_LEVEL"));
                m.put("itemRequired",  rs.getString("ITEM_REQUIRED"));
                list.add(m);
            }
        }
        return list;
    }

    /** Changes the species of a caught Pokemon and recalculates stats using stored IV. */
    public void evolvePokemon(long caughtId, int playerId, int newSpeciesId) throws SQLException {
        CaughtPokemon p = getCaughtById(caughtId, playerId);
        if (p == null) throw new IllegalArgumentException("Pokemon not found");
        PokemonSpecies sp = getSpeciesById(newSpeciesId);
        if (sp == null) throw new IllegalArgumentException("Target species not found");

        double iv = p.getIv() > 0 ? p.getIv() : 0.95;
        int lvl   = p.getPokemonLevel();
        int hp    = statAtLvl(sp.getBaseHp(),      lvl, iv) + lvl;
        int atk   = statAtLvl(sp.getBaseAttack(),  lvl, iv);
        int def   = statAtLvl(sp.getBaseDefense(), lvl, iv);
        int spAtk = statAtLvl(sp.getBaseSpAtk(),   lvl, iv);
        int spDef = statAtLvl(sp.getBaseSpDef(),   lvl, iv);
        int speed = statAtLvl(sp.getBaseSpeed(),   lvl, iv);
        String sprite = String.format("pokemon_icon_%03d_00.png", newSpeciesId);

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                UPDATE CAUGHT_POKEMON
                SET SPECIES_ID=?,HP=?,ATTACK=?,DEFENSE=?,SP_ATK=?,SP_DEF=?,SPEED=?
                WHERE ID=? AND PLAYER_ID=?""")) {
            ps.setInt(1, newSpeciesId); ps.setInt(2, hp);    ps.setInt(3, atk);
            ps.setInt(4, def);         ps.setInt(5, spAtk);  ps.setInt(6, spDef);
            ps.setInt(7, speed);       ps.setLong(8, caughtId); ps.setInt(9, playerId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    // ── Favourite ─────────────────────────────────────────────────────────────

    public void setFavourite(long caughtId, int playerId, boolean fav) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE CAUGHT_POKEMON SET FAVOURITE=? WHERE ID=? AND PLAYER_ID=?")) {
            ps.setBoolean(1, fav);
            ps.setLong(2, caughtId);
            ps.setInt(3, playerId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    // ── Stardust ──────────────────────────────────────────────────────────────

    public void addStardust(int playerId, int amount) throws SQLException {
        getPlayerStats(playerId); // ensure row exists
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "UPDATE POKEMON_PLAYER_STATS SET STARDUST = STARDUST + ? WHERE PLAYER_ID=?")) {
            ps.setInt(1, Math.max(0, amount));
            ps.setInt(2, playerId);
            ps.executeUpdate();
            conn.commit();
        }
    }

    public int getStardust(int playerId) throws SQLException {
        int[] stats = getPlayerStats(playerId);
        return stats.length > 2 ? stats[2] : 0;
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private PokemonSpawn mapSpawn(ResultSet rs) throws SQLException {
        PokemonSpawn s = new PokemonSpawn();
        s.setId(rs.getLong("ID"));
        s.setSpeciesId(rs.getInt("SPECIES_ID"));
        s.setLat(rs.getDouble("LAT"));
        s.setLng(rs.getDouble("LNG"));
        s.setSpawnedAt(rs.getTimestamp("SPAWNED_AT").toInstant());
        s.setExpiresAt(rs.getTimestamp("EXPIRES_AT").toInstant());
        int caughtBy = rs.getInt("CAUGHT_BY_PLAYER");
        if (!rs.wasNull()) s.setCaughtByPlayer(caughtBy);
        try { s.setSpeciesName(rs.getString("NAME")); } catch (SQLException ignored) {}
        try { s.setSpriteKey(rs.getString("SPRITE_KEY")); } catch (SQLException ignored) {}
        return s;
    }

    private CaughtPokemon mapCaught(ResultSet rs) throws SQLException {
        CaughtPokemon c = new CaughtPokemon();
        c.setId(rs.getLong("ID"));
        c.setPlayerId(rs.getInt("PLAYER_ID"));
        c.setSpeciesId(rs.getInt("SPECIES_ID"));
        c.setPokemonLevel(rs.getInt("POKEMON_LEVEL"));
        c.setHp(rs.getInt("HP"));
        c.setAttack(rs.getInt("ATTACK"));
        c.setDefense(rs.getInt("DEFENSE"));
        c.setSpAtk(rs.getInt("SP_ATK"));
        c.setSpDef(rs.getInt("SP_DEF"));
        c.setSpeed(rs.getInt("SPEED"));
        c.setCaughtAt(rs.getTimestamp("CAUGHT_AT").toInstant());
        c.setCaughtLat(rs.getDouble("CAUGHT_LAT"));
        c.setCaughtLng(rs.getDouble("CAUGHT_LNG"));
        c.setNickname(rs.getString("NICKNAME"));
        try { c.setExp(rs.getLong("POKEMON_EXP")); } catch (SQLException ignored) {}
        try { c.setIv(rs.getDouble("POKEMON_IV")); } catch (SQLException ignored) {}
        try { c.setFavourite(rs.getBoolean("FAVOURITE")); } catch (SQLException ignored) {}
        try { c.setSpeciesName(rs.getString("NAME")); } catch (SQLException ignored) {}
        try { c.setSpriteKey(rs.getString("SPRITE_KEY")); } catch (SQLException ignored) {}
        try { c.setType1(rs.getString("TYPE1")); } catch (SQLException ignored) {}
        try { c.setType2(rs.getString("TYPE2")); } catch (SQLException ignored) {}
        return c;
    }

    private Pokestop mapStop(ResultSet rs) throws SQLException {
        Pokestop stop = new Pokestop();
        stop.setId(rs.getLong("ID"));
        stop.setName(rs.getString("NAME"));
        stop.setLat(rs.getDouble("LAT"));
        stop.setLng(rs.getDouble("LNG"));
        int spunBy = rs.getInt("LAST_SPUN_BY");
        if (!rs.wasNull()) stop.setLastSpunBy(spunBy);
        Timestamp spunAt = rs.getTimestamp("LAST_SPUN_AT");
        if (spunAt != null) stop.setLastSpunAt(spunAt.toInstant());
        stop.setCanSpin(spunAt == null ||
            Instant.now().isAfter(spunAt.toInstant().plusSeconds(300)));
        try { stop.setBiome(rs.getString("BIOME")); } catch (SQLException ignored) {}
        try {
            Timestamp lureAt = rs.getTimestamp("LURE_EXPIRES_AT");
            if (lureAt != null) stop.setLureExpiresAt(lureAt.toInstant());
        } catch (SQLException ignored) {}
        return stop;
    }

    private PokemonSpecies mapSpecies(ResultSet rs) throws SQLException {
        PokemonSpecies sp = new PokemonSpecies();
        sp.setId(rs.getInt("ID"));
        sp.setName(rs.getString("NAME"));
        sp.setType1(rs.getString("TYPE1"));
        sp.setType2(rs.getString("TYPE2"));
        sp.setBaseHp(rs.getInt("BASE_HP"));
        sp.setBaseAttack(rs.getInt("BASE_ATTACK"));
        sp.setBaseDefense(rs.getInt("BASE_DEFENSE"));
        sp.setBaseSpAtk(rs.getInt("BASE_SP_ATK"));
        sp.setBaseSpDef(rs.getInt("BASE_SP_DEF"));
        sp.setBaseSpeed(rs.getInt("BASE_SPEED"));
        sp.setRarity(rs.getInt("RARITY"));
        sp.setSpriteKey(rs.getString("SPRITE_KEY"));
        return sp;
    }
}

package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class ShopController {

    record ShopItem(String itemType, String label, int price, String sprite) {}

    private static final List<ShopItem> CATALOG = List.of(
        new ShopItem("POKEBALL",   "Poké Ball",  50,  "pokeball_sprite.png"),
        new ShopItem("GREAT_BALL", "Great Ball", 150, "greatball_sprite.png"),
        new ShopItem("ULTRA_BALL", "Ultra Ball", 300, "ultraball_sprite.png"),
        new ShopItem("POTION",     "Potion",     100, "Item_0101.png"),
        new ShopItem("REVIVE",     "Revive",     150, "Item_0201.png")
    );

    @Autowired
    private PokemonDatabase db;

    @GetMapping("/shop/catalog")
    public ResponseEntity<?> catalog() {
        return ResponseEntity.ok(CATALOG);
    }

    @PostMapping("/shop/buy")
    public ResponseEntity<?> buy(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal().equals("anonymousUser"))
            return ResponseEntity.status(401).body("Login required");
        try {
            int playerId = ((CustomUserDetails) auth.getPrincipal()).getId();
            String itemType = body.get("itemType").toString();
            int quantity = ((Number) body.getOrDefault("quantity", 1)).intValue();
            if (quantity < 1 || quantity > 20) return ResponseEntity.badRequest().body("Quantity 1-20");

            ShopItem item = CATALOG.stream().filter(i -> i.itemType().equals(itemType))
                .findFirst().orElse(null);
            if (item == null) return ResponseEntity.badRequest().body("Unknown item: " + itemType);

            int totalCost = item.price() * quantity;
            int[] stats = db.getPlayerStats(playerId);
            int coins = stats[1];
            if (coins < totalCost)
                return ResponseEntity.status(409).body(
                    "Not enough coins (" + coins + " / " + totalCost + " needed)");

            db.addCoins(playerId, -totalCost);
            db.adjustItem(playerId, itemType, quantity);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "item", itemType,
                "quantity", quantity,
                "coinsSpent", totalCost,
                "coinsRemaining", coins - totalCost,
                "message", "Bought " + quantity + "× " + item.label() + " for " + totalCost + " coins!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

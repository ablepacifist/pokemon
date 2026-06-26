package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/pokemon")
@CrossOrigin(origins = "*")
public class PokedexController {

    @Autowired
    private PokemonDatabase db;

    @Value("${pokemon.sprites.path}")
    private String spritesPath;

    @Value("${pokemon.item-sprites.path}")
    private String itemSpritesPath;

    @Value("${pokemon.item-sprites.fallback}")
    private String itemSpritesFallback;

    @GetMapping("/species")
    public ResponseEntity<?> allSpecies() {
        try {
            return ResponseEntity.ok(db.getAllSpecies());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/species/{id}")
    public ResponseEntity<?> oneSpecies(@PathVariable int id) {
        try {
            var species = db.getSpeciesById(id);
            return species != null ? ResponseEntity.ok(species) : ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    /** Serve sprite images directly from the pogo_assets/Pokemon folder. */
    @GetMapping("/sprites/{filename:.+}")
    public ResponseEntity<Resource> sprite(@PathVariable String filename) {
        try {
            Path path = Paths.get(spritesPath, filename);
            File file = path.toFile();
            if (!file.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /** Serve item images — checks pogo_assets first, falls back to pokesprite. Supports subdirectory paths. */
    @GetMapping("/item-sprites/**")
    public ResponseEntity<Resource> itemSprite(HttpServletRequest request) {
        try {
            String sub = request.getRequestURI().split("/item-sprites/", 2)[1];
            String[] parts = sub.split("/");
            File file = Paths.get(itemSpritesPath, parts).toFile();
            if (!file.exists()) file = Paths.get(itemSpritesFallback, parts).toFile();
            if (!file.exists()) return ResponseEntity.notFound().build();
            String ct = sub.endsWith(".png") ? "image/png" : "image/webp";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct))
                .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

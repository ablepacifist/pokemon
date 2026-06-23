package pokemon.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pokemon.data.PokemonDatabase;

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

    /** Serve item images (pokeballs, etc.) from the pogo_assets/Items folder. */
    @GetMapping("/item-sprites/{filename:.+}")
    public ResponseEntity<Resource> itemSprite(@PathVariable String filename) {
        try {
            Path path = Paths.get(itemSpritesPath, filename);
            File file = path.toFile();
            if (!file.exists()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

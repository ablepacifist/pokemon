package pokemon.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SafeFilesTest {

    @TempDir
    Path base;

    @Test
    void resolvesNormalFilesAndSubfolders() throws Exception {
        Files.createDirectories(base.resolve("balls"));
        Files.writeString(base.resolve("balls").resolve("great.png"), "x");
        File f = SafeFiles.resolveUnder(base.toString(), "balls", "great.png");
        assertNotNull(f);
        assertTrue(f.isFile());
        assertEquals(base.resolve("balls").resolve("great.png").toAbsolutePath().normalize(), f.toPath());
    }

    @Test
    void refusesTraversalAndAbsoluteSegments() {
        assertNull(SafeFiles.resolveUnder(base.toString(), ".."));
        assertNull(SafeFiles.resolveUnder(base.toString(), "..", "secret.txt"));
        assertNull(SafeFiles.resolveUnder(base.toString(), "balls", "..", "..", "x"));
        assertNull(SafeFiles.resolveUnder(base.toString(), "/etc/passwd"));
        assertNull(SafeFiles.resolveUnder(base.toString(), "C:\\Windows\\win.ini"));
        assertNull(SafeFiles.resolveUnder(base.toString(), "a\0b"));
        assertNull(SafeFiles.resolveUnder(base.toString(), ""));
    }

    @Test
    void refusesTheBaseFolderItself() {
        assertNull(SafeFiles.resolveUnder(base.toString(), "."));
    }

    @Test
    void aSiblingWithTheSamePrefixIsNotInside() throws Exception {
        Path sibling = Files.createDirectories(base.resolveSibling(base.getFileName() + "-evil"));
        assertNull(SafeFiles.resolveUnder(base.toString(), "..", sibling.getFileName().toString(), "x"));
    }
}

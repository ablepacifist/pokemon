package pokemon.api;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Resolves a client-supplied file name under a fixed base folder, refusing anything that escapes it. */
final class SafeFiles {

    private SafeFiles() {
    }

    /**
     * @return the file under {@code baseDir} for the given path segments, or null when the result would fall
     *         outside {@code baseDir} (for example through "..", an absolute segment or a drive/UNC prefix)
     */
    static File resolveUnder(String baseDir, String... segments) {
        Path base = Paths.get(baseDir).toAbsolutePath().normalize();
        Path resolved = base;
        try {
            for (String segment : segments) {
                if (segment == null || segment.isEmpty() || segment.indexOf('\0') >= 0) {
                    return null;
                }
                resolved = resolved.resolve(segment);
            }
            resolved = resolved.normalize();
        } catch (RuntimeException e) { // InvalidPathException on Windows for characters like ':' or '*'
            return null;
        }
        return resolved.startsWith(base) && !resolved.equals(base) ? resolved.toFile() : null;
    }
}

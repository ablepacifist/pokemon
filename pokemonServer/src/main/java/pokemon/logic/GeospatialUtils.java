package pokemon.logic;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public class GeospatialUtils {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Return a random lat/lng within radiusMeters of the given center. */
    public static double[] randomOffset(double centerLat, double centerLng, double radiusMeters) {
        double r = radiusMeters / EARTH_RADIUS_M;
        double u = Math.random();
        double v = Math.random();
        double w = r * Math.sqrt(u);
        double t = 2 * Math.PI * v;
        double dLat = w * Math.cos(t);
        double dLng = w * Math.sin(t) / Math.cos(Math.toRadians(centerLat));
        return new double[]{
            centerLat + Math.toDegrees(dLat),
            centerLng + Math.toDegrees(dLng)
        };
    }

    /**
     * Queries Overpass API to detect water or park features within 300m.
     * Returns "WATER", "GRASS", or "NORMAL". Falls back to "NORMAL" on any error or timeout.
     */
    public static String detectBiome(double lat, double lng) {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

            String waterQuery = String.format(Locale.US,
                "[out:json][timeout:5];" +
                "(node[\"natural\"~\"water|wetland\"](around:300,%f,%f);" +
                "way[\"natural\"~\"water|wetland\"](around:300,%f,%f);" +
                "way[\"waterway\"~\"river|stream|canal\"](around:300,%f,%f););" +
                "out count;",
                lat, lng, lat, lng, lat, lng);
            if (overpassCount(client, waterQuery) > 0) return "WATER";

            String grassQuery = String.format(Locale.US,
                "[out:json][timeout:5];" +
                "(way[\"leisure\"=\"park\"](around:300,%f,%f);" +
                "way[\"landuse\"~\"forest|meadow|grass\"](around:300,%f,%f);" +
                "way[\"natural\"=\"wood\"](around:300,%f,%f););" +
                "out count;",
                lat, lng, lat, lng, lat, lng);
            if (overpassCount(client, grassQuery) > 0) return "GRASS";

        } catch (Exception ignored) {}
        return "NORMAL";
    }

    private static int overpassCount(HttpClient client, String query) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://overpass-api.de/api/interpreter"))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("data=" + encoded))
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        int idx = body.indexOf("\"total\":\"");
        if (idx < 0) return 0;
        int start = idx + 9;
        int end = body.indexOf('"', start);
        if (end < 0) return 0;
        try { return Integer.parseInt(body.substring(start, end).trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}

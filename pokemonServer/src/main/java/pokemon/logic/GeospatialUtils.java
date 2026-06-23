package pokemon.logic;

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
}

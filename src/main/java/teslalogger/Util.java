package teslalogger;

/**
 * Shared utilities.
 * <p/>
 * User: sam
 * Date: 3/23/13
 * Time: 5:02 PM
 */
public class Util {
  // http://www.movable-type.co.uk/scripts/latlong.html
  public static double haversine(double lat1, double lon1, double lat2, double lon2) {
    double R = 6371; // km
    double dLat = toRad(lat1 - lat2);
    double dLon = toRad(lon1 - lon2);
    lat2 = toRad(lat2);
    lat1 = toRad(lat1);

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat2) * Math.cos(lat1);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
  }

  private static double toRad(double v) {
    return v * Math.PI / 180;
  }

}

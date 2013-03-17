package teslalogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * Only charges during a time window.
 * <p/>
 * User: sam
 * Date: 3/16/13
 * Time: 10:50 AM
 */
public class ChargingWindow {
  @Argument(alias = "c", description = "Name of the configuration properties file", required = true)
  private static File config;

  @Argument(alias = "l", description = "Latitude, Longitude of your home charger", required = true)
  private static String[] location;

  @Argument(alias = "s", description = "Start HH:mm time of window in 24-hour local time, e.g. 00:00 is midnight")
  private static String start;

  @Argument(alias = "e", description = "End HH:mm time of window in 24-hour local time, e.g. 07:00 is 7am")
  private static String end;

  private static DateFormat format = new SimpleDateFormat("HH:mm");

  public static void main(String[] args) throws IOException, InterruptedException, ParseException {
    Properties properties = new Properties();
    final double startTime;
    final double endTime;
    try {
      Args.parse(ChargingWindow.class, args);
      properties.load(new FileInputStream(config));
      startTime = parse(start);
      endTime = parse(end);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      Args.usage(ChargingWindow.class);
      System.exit(1);
      return;
    }

    final Connection connection = new Connection(properties, 5);
    chargingWindow(connection, startTime, endTime, Double.parseDouble(location[0]), Double.parseDouble(location[1]));

  }

  public static void chargingWindow(final Connection connection, final double startTime, final double endTime, final double lat2, final double lon2) throws InterruptedException {
    // Home location
    connection.monitorTesla(new Connection.VehicleCaller() {
      @Override
      public boolean call(long vehicleId, JsonNode vehicle) {
        try {
          JsonNode driveState = connection.getResult("vehicles/" + vehicleId + "/command/drive_state");
          JsonNode shiftState = driveState.get("shift_state");
          if (shiftState == null || shiftState.isNull()) {
            // Not driving around
            double d = haversine(driveState.get("latitude").asDouble(), driveState.get("longitude").asDouble(), lat2, lon2);
            if (d < .05) {
              // 50 meters, certainly at home
              JsonNode chargeState = connection.getResult("vehicles/" + vehicleId + "/command/charge_state");
              JsonNode chargingState = chargeState.get("charging_state");
              String charging = chargingState.asText();
              if (!"Disconnected".equals(charging)) {
                // We are plugged in, lets figure out what we should do then
                double current = parse(format.format(new Date()));
                if (current > startTime && current < endTime) {
                  // In the zone
                  if (charging.equals("Stopped")) {
                    // Currently stopped
                    System.out.println("Starting charging");
                    connection.getResult("vehicles/" + vehicleId + "/command/charge_start");
                  } else {
                    System.out.println("Vehicle already charging");
                  }
                } else {
                  // Outside the zone
                  if (charging.equals("Charging")) {
                    // Currently started
                    System.out.println("Stopping charging");
                    connection.getResult("vehicles/" + vehicleId + "/command/charge_stop");
                  } else {
                    System.out.println("Vehicle already not charging");
                  }
                }
              } else {
                System.out.println("Vehicle not plugged in");
              }
            } else {
              System.out.println("Vehicle not at home");
            }
          }

          return true;
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
      }

      // http://www.movable-type.co.uk/scripts/latlong.html
      private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // km
        double dLat = toRad(lat1 - lat2);
        double dLon = toRad(lon1 - lon2);
        lat2 = toRad(lat2);
        lat1 = toRad(lat1);

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat2) * Math.cos(lat1);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
      }

      private double toRad(double v) {
        return v * Math.PI / 180;
      }
    });
  }

  private static double parse(String time) {
    String[] startSplit = time.split(":");
    return Integer.parseInt(startSplit[0]) + Double.parseDouble(startSplit[1]) / 60;
  }

}

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import static teslalogger.Util.haversine;

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

  @Argument(alias = "l", description = "Latitude, Longitude of your home charger, if no location given uses current location")
  private static String[] location;

  @Argument(alias = "s", description = "Start HH:mm time of window in 24-hour local time, e.g. 00:00 is midnight", required = true)
  private static String start;

  @Argument(alias = "e", description = "End HH:mm time of window in 24-hour local time, e.g. 07:00 is 7am", required = true)
  private static String end;

  @Argument(alias = "p", description = "Minutes between checks of charging state")
  private static Integer period = 5;

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

    final Connection connection = new Connection(properties, period);
    double lat = location == null ? 0 : Double.parseDouble(location[0]);
    double lon = location == null ? 0 : Double.parseDouble(location[1]);
    chargingWindow(connection, startTime, endTime, lat, lon);
  }

  public static void chargingWindow(final Connection connection, final double startTime, final double endTime, final double lat, final double lon) throws InterruptedException {
    // Home location
    final Logger log = connection.logger;
    connection.monitorTesla(new Connection.VehicleCaller() {

      private double homeLat = lat;
      private double homeLon = lon;

      @Override
      public boolean call(long vehicleId, JsonNode vehicle) {
        List<Object> debug = new ArrayList<>();
        debug.add(vehicle);
        try {
          JsonNode driveState = connection.getResult("vehicles/" + vehicleId + "/command/drive_state");
          debug.add(driveState);
          JsonNode shiftState = driveState.get("shift_state");
          if (shiftState == null || shiftState.isNull()) {
            // Not driving around
            double latitude = driveState.get("latitude").asDouble();
            double longitude = driveState.get("longitude").asDouble();
            if (homeLat == 0 && homeLon == 0) {
              homeLat = latitude;
              homeLon = longitude;
              log.info("Setting home location to " + latitude + ", " + longitude);
            }
            double d = haversine(latitude, longitude, homeLat, homeLon);
            if (d < .05) {
              // 50 meters, certainly at home
              JsonNode chargeState = connection.getResult("vehicles/" + vehicleId + "/command/charge_state");
              debug.add(chargeState);
              JsonNode chargingState = chargeState.get("charging_state");
              String charging = chargingState.asText();
              if (!"Disconnected".equals(charging)) {
                // We are plugged in, lets figure out what we should do then
                double current = parse(format.format(new Date()));
                if (current > startTime && current < endTime) {
                  // In the zone
                  if (charging.equals("Stopped")) {
                    // Currently stopped
                    log.info("Starting charging");
                    debug.add(connection.getResult("vehicles/" + vehicleId + "/command/charge_start"));
                  } else {
                    log.info("Vehicle already charging");
                  }
                } else {
                  // Outside the zone
                  if (charging.equals("Charging")) {
                    // Currently started
                    log.info("Stopping charging");
                    debug.add(connection.getResult("vehicles/" + vehicleId + "/command/charge_stop"));
                  } else {
                    log.info("Vehicle already not charging");
                  }
                }
              } else {
                log.info("Vehicle not plugged in");
              }
            } else {
              log.info("Vehicle not at home");
            }
          }

          return true;
        } catch (Exception e) {
          log.log(Level.SEVERE, "Failed: " + debug, e);
          return false;
        }
      }
    });
  }

  private static double parse(String time) {
    String[] startSplit = time.split(":");
    return Integer.parseInt(startSplit[0]) + Double.parseDouble(startSplit[1]) / 60;
  }

}

package teslalogger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * Read a Tesla vehicles current status and log it every 5 minutes.
 */
public class App {

  @Argument(alias = "c", description = "Name of the authentication properties file", required = true)
  private static String config;

  @Argument(alias = "p", description = "Minutes between log entries")
  private static Integer period = 5;

  public static void main(String[] args) throws Exception {
    Properties auth = new Properties();
    try {
      Args.parse(App.class, args);
      auth.load(new FileInputStream(config));
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      Args.usage(App.class);
      System.exit(1);
    }

    final Connection connection = new Connection(auth, period);
    connection.monitorTesla(new Connection.VehicleCaller() {
      MappingJsonFactory jf = new MappingJsonFactory();

      @Override
      public boolean call(long vehicleId, JsonNode vehicle) {
        final Writer log;
        try {
          log = new OutputStreamWriter(new FileOutputStream("vehicle_" + vehicleId + ".log", true), "UTF-8");
          JsonGenerator logentry = jf.createGenerator(log);
          logentry.writeStartObject();
          logentry.writeObjectField("vehicle", vehicle);
          logentry.writeObjectField("state", connection.getResult("vehicles/" + vehicleId + "/command/vehicle_state"));
          logentry.writeObjectField("charge", connection.getResult("vehicles/" + vehicleId + "/command/charge_state"));
          logentry.writeObjectField("climate", connection.getResult("vehicles/" + vehicleId + "/command/climate_state"));
          logentry.writeObjectField("drive", connection.getResult("vehicles/" + vehicleId + "/command/drive_state"));
          logentry.writeObjectField("settings", connection.getResult("vehicles/" + vehicleId + "/command/gui_settings"));
          logentry.writeEndObject();
          logentry.flush();
          log.write("\n");
          log.close();
          return true;
        } catch (Exception e) {
          return false;
        }
      }
    });
  }

}

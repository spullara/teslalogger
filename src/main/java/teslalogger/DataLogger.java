package teslalogger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

/**
 * Read a Tesla vehicles current status and log it every 5 minutes.
 */
public class DataLogger {

  public static final byte[] LF = "\n".getBytes();
  @Argument(alias = "c", description = "Name of the authentication properties file", required = true)
  private static String config;

  @Argument(alias = "p", description = "Minutes between log entries")
  private static Integer period = 5;

  @Argument(alias = "h", description = "Hosts to report metrics")
  private static String[] hosts;

  private static String token;

  public static void main(String[] args) throws Exception {
    Properties auth = new Properties();
    try {
      Args.parse(DataLogger.class, args);
      auth.load(new FileInputStream(config));
      token = auth.getProperty("token");
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      Args.usage(DataLogger.class);
      System.exit(1);
    }

    final Connection connection = new Connection(auth, period);
    logging(connection);
  }

  public static void logging(final Connection connection) throws InterruptedException {
    connection.monitorTesla(new Connection.VehicleCaller() {
      MappingJsonFactory jf = new MappingJsonFactory();

      @Override
      public boolean call(long vehicleId, JsonNode vehicle) {
        try {
          byte[] bytes = createLogEntry(vehicleId, vehicle);
          OutputStream log = new FileOutputStream("vehicle_" + vehicleId + ".log", true);
          log.write(bytes);
          log.write(LF);
          log.close();
          if (hosts != null) {
            for (String host : hosts) {
              send(bytes, host);
            }
          }
          return true;
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
      }

      private byte[] createLogEntry(long vehicleId, JsonNode vehicle) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator logentry = jf.createGenerator(baos);
        logentry.writeStartObject();
        logentry.writeObjectField("vehicle", vehicle);
        logentry.writeObjectField("state", connection.getResult("vehicles/" + vehicleId + "/command/vehicle_state"));
        logentry.writeObjectField("charge", connection.getResult("vehicles/" + vehicleId + "/command/charge_state"));
        logentry.writeObjectField("climate", connection.getResult("vehicles/" + vehicleId + "/command/climate_state"));
        logentry.writeObjectField("drive", connection.getResult("vehicles/" + vehicleId + "/command/drive_state"));
        logentry.writeObjectField("settings", connection.getResult("vehicles/" + vehicleId + "/command/gui_settings"));
        logentry.writeEndObject();
        logentry.flush();
        return baos.toByteArray();
      }
    });
  }

  private static void send(byte[] bytes, String host) throws IOException {
    URL url = new URL("https://" + host + "/report/metrics?t=" + token + "&h=discusstesla.com&p=vehicle");
    HttpURLConnection urlc = (HttpURLConnection) url.openConnection();
    urlc.setDoOutput(true);
    urlc.addRequestProperty("Content-Type", "application/json");
    OutputStream urlout = urlc.getOutputStream();
    urlout.write(bytes);
    urlout.close();
    System.out.println("Sent to " + url + " " + urlc.getResponseCode());
  }

}

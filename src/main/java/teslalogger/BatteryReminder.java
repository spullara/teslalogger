package teslalogger;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Remind me to plug it in at home.
 * <p/>
 * User: sam
 * Date: 2/21/13
 * Time: 9:51 PM
 */
public class BatteryReminder {

  @Argument(alias = "c", description = "Name of the authentication properties file", required = true)
  private static File config;

  @Argument(alias = "p", description = "Minutes between log entries")
  private static Integer period = 5;

  @Argument(alias = "l", description = "Latitude, Longitude of your home charger", required = true)
  private static String[] location;

  @Argument(alias = "r", description = "Alert Rocket configuration file", required = true)
  private static File alertrocket;

  public static void main(String[] args) throws IOException, InterruptedException {
    Properties auth = new Properties();
    final Properties alertAuth = new Properties();
    try {
      Args.parse(BatteryReminder.class, args);
      auth.load(new FileInputStream(config));
      alertAuth.load(new FileInputStream(alertrocket));
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      Args.usage(BatteryReminder.class);
      System.exit(1);
    }

    // Home location
    final double lat2 = Double.parseDouble(location[0]);
    final double lon2 = Double.parseDouble(location[1]);
    final Connection connection = new Connection(auth, 5);
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
              if ("Disconnected".equals(chargingState.asText())) {
                // Not connected to the power
                byte[] buf = createAlert(alertAuth);
                DefaultHttpClient client = createClient(alertAuth);
                HttpPost post = new HttpPost("https://launch.alertrocket.com/api/push");
                post.setEntity(new ByteArrayEntity(buf, ContentType.APPLICATION_JSON));
                HttpResponse response = client.execute(post);
                response.getStatusLine();
                client.getConnectionManager().shutdown();
                System.out.println("Charger disconnected, sending notification");
              } else {
                System.out.println("Plugged in");
              }
            }
          }
          return true;
        } catch (IOException e) {
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

  private static DefaultHttpClient createClient(Properties alertAuth) {
    DefaultHttpClient client = new DefaultHttpClient();
    BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(alertAuth.getProperty("configKey"), alertAuth.getProperty("secretKey"));
    credsProvider.setCredentials(AuthScope.ANY, credentials);
    client.setCredentialsProvider(credsProvider);
    return client;
  }

  private static byte[] createAlert(Properties alertAuth) throws IOException {
    JsonFactory jf = new MappingJsonFactory();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    JsonGenerator g = jf.createGenerator(out);
    g.writeStartObject();
    g.writeStringField("alert", "Connect your Tesla to the charger");
    g.writeStringField("url", "http://launch.alertrocket.com/demo");
    g.writeArrayFieldStart("device_tokens");
    g.writeString(alertAuth.getProperty("token"));
    g.writeEndArray();
    g.writeEndObject();
    g.flush();
    return out.toByteArray();
  }
}

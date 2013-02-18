package teslalogger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.google.common.io.ByteStreams;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Properties;

/**
 * Read a Tesla vehicles current status and log it every 5 minutes.
 */
public class App {

  private static final int _5_MIN_MILLIS = 300000;
  private static DefaultHttpClient client;
  private static MappingJsonFactory jf;
  private static String baseURL;

  public static void main(String[] args) throws Exception {
    Properties auth = new Properties();
    auth.load(App.class.getResourceAsStream("/auth.properties"));

    jf = new MappingJsonFactory();
    client = new DefaultHttpClient();
    baseURL = "https://portal.vn.teslamotors.com/";
    HttpPost login = new HttpPost(baseURL + "login");
    BasicNameValuePair email = new BasicNameValuePair("user_session[email]", auth.getProperty("email"));
    BasicNameValuePair password = new BasicNameValuePair("user_session[password]", auth.getProperty("password"));
    login.setEntity(new UrlEncodedFormEntity(Arrays.asList(email, password)));
    HttpResponse loginResponse = client.execute(login);
    if (loginResponse.getStatusLine().getStatusCode() == 302) {
      if (loginResponse.getFirstHeader("Location").getValue().equals(baseURL)) {
        login.releaseConnection();
        while (true) {
          JsonNode vehiclesArray = getResult("vehicles");
          for (JsonNode vehicle : vehiclesArray) {
            String state = vehicle.get("state").asText();
            if (state.equals("online")) {
              long vehicleId = vehicle.get("id").asLong();
              JsonNode chargeState = getResult("vehicles/" + vehicleId + "/command/charge_state");
              JsonNode climateState = getResult("vehicles/" + vehicleId + "/command/climate_state");
              JsonNode driveState = getResult("vehicles/" + vehicleId + "/command/drive_state");
              JsonNode guiSettings = getResult("vehicles/" + vehicleId + "/command/gui_settings");
              JsonNode vehicleState = getResult("vehicles/" + vehicleId + "/command/vehicle_state");
              Writer log = new OutputStreamWriter(new FileOutputStream("vehicle_" + vehicleId + ".log", true), "UTF-8");
              JsonGenerator logentry = jf.createGenerator(log);
              logentry.writeStartObject();
              logentry.writeObjectField("vehicle", vehicle);
              logentry.writeObjectField("state", vehicleState);
              logentry.writeObjectField("charge", chargeState);
              logentry.writeObjectField("climate", climateState);
              logentry.writeObjectField("drive", driveState);
              logentry.writeObjectField("settings", guiSettings);
              logentry.writeEndObject();
              logentry.flush();
              log.write("\n");
              log.close();
            }
          }
          Thread.sleep(_5_MIN_MILLIS);
        }
      }
    }
  }

  private static JsonNode getResult(String url) throws IOException {
    HttpGet get = new HttpGet(baseURL + url);
    HttpResponse vehiclesResponse = client.execute(get);
    InputStream content = vehiclesResponse.getEntity().getContent();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ByteStreams.copy(content, baos);
    try {
      JsonNode vehiclesArray = jf.createParser(baos.toByteArray()).readValueAsTree();
      get.releaseConnection();
      return vehiclesArray;
    } catch (IOException e) {
      System.out.println("Failed to parse: " + new String(baos.toByteArray()));
      throw e;
    }
  }

  private void call(String url) throws IOException {
    HttpGet get = new HttpGet(baseURL + url);
    client.execute(get);
    get.releaseConnection();
  }
}

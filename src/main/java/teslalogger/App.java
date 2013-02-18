package teslalogger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.google.common.io.ByteStreams;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Read a Tesla vehicles current status and log it every 5 minutes.
 */
public class App {

  private static final int _1_MIN_MILLIS = 60000;
  private static DefaultHttpClient client;
  private static MappingJsonFactory jf;
  private static String baseURL;
  private static Logger logger = Logger.getLogger("Tesla");

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

    jf = new MappingJsonFactory();
    client = new DefaultHttpClient();
    baseURL = "https://portal.vn.teslamotors.com/";
    while (true) {
      try {
        HttpPost login = new HttpPost(baseURL + "login");
        BasicNameValuePair email = new BasicNameValuePair("user_session[email]", auth.getProperty("email"));
        BasicNameValuePair password = new BasicNameValuePair("user_session[password]", auth.getProperty("password"));
        login.setEntity(new UrlEncodedFormEntity(Arrays.asList(email, password)));
        HttpResponse loginResponse = client.execute(login);
        if (loginResponse.getStatusLine().getStatusCode() == 302) {
          if (loginResponse.getFirstHeader("Location").getValue().equals(baseURL)) {
            login.releaseConnection();
            logger.log(Level.INFO, "Successfully connected to Tesla");
            while (true) {
              JsonNode vehiclesArray = getResult("vehicles");
              for (JsonNode vehicle : vehiclesArray) {
                String state = vehicle.get("state").asText();
                if (state.equals("online")) {
                  long vehicleId = vehicle.get("id").asLong();
                  Writer log = new OutputStreamWriter(new FileOutputStream("vehicle_" + vehicleId + ".log", true), "UTF-8");
                  JsonGenerator logentry = jf.createGenerator(log);
                  logentry.writeStartObject();
                  logentry.writeObjectField("vehicle", vehicle);
                  logentry.writeObjectField("state", getResult("vehicles/" + vehicleId + "/command/vehicle_state"));
                  logentry.writeObjectField("charge", getResult("vehicles/" + vehicleId + "/command/charge_state"));
                  logentry.writeObjectField("climate", getResult("vehicles/" + vehicleId + "/command/climate_state"));
                  logentry.writeObjectField("drive", getResult("vehicles/" + vehicleId + "/command/drive_state"));
                  logentry.writeObjectField("settings", getResult("vehicles/" + vehicleId + "/command/gui_settings"));
                  logentry.writeEndObject();
                  logentry.flush();
                  log.write("\n");
                  log.close();
                  logger.info("Successfully reported vehicle information for " + vehicleId);
                }
              }
              Thread.sleep(period * _1_MIN_MILLIS);
            }
          }
        }
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Failed to get vehicle information", e);
        client.getConnectionManager().shutdown();
        client = new DefaultHttpClient();
        Thread.sleep(period * _1_MIN_MILLIS);
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
}

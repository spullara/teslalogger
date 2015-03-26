package teslalogger;

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
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO: Edit this
 * <p/>
 * User: sam
 * Date: 2/21/13
 * Time: 9:55 PM
 */
public class Connection {
  private static final int _1_MIN_MILLIS = 60000;
  private final Properties config;
  public Logger logger = Logger.getLogger("Tesla");
  private DefaultHttpClient client = new DefaultHttpClient();
  private MappingJsonFactory jf= new MappingJsonFactory();
  private String baseURL = "https://portal.vn.teslamotors.com/";
  private int period;

  public Connection(Properties config, int period) {
    this.config = config;
    this.period = period;
  }

  public void monitorTesla(VehicleCaller execute) throws InterruptedException {
    while (true) {
      try {
        HttpGet loginPage = new HttpGet(baseURL + "login");
        client.execute(loginPage).getStatusLine();
        HttpPost login = new HttpPost(baseURL + "login");
        BasicNameValuePair email = new BasicNameValuePair("user_session[email]", config.getProperty("email"));
        BasicNameValuePair password = new BasicNameValuePair("user_session[password]", config.getProperty("password"));
        login.setEntity(new UrlEncodedFormEntity(Arrays.asList(email, password)));
        HttpResponse loginResponse = client.execute(login);
        if (loginResponse.getStatusLine().getStatusCode() == 302) {
          String location = loginResponse.getFirstHeader("Location").getValue();
          login.releaseConnection();
          if (location.equals(baseURL)) {
            logger.log(Level.INFO, "Successfully connected to Tesla");
            while (true) {
              JsonNode vehiclesArray = getResult("vehicles");
              for (final JsonNode vehicle : vehiclesArray) {
                String state = vehicle.get("state").asText();
                if (state.equals("online")) {
                  final long vehicleId = vehicle.get("id").asLong();
                  if (!execute.call(vehicleId, vehicle)) {
                    break;
                  }
                }
              }
              Thread.sleep(period * _1_MIN_MILLIS);
            }
          }
        } else {
          login.releaseConnection();
          logger.info("Connected and got status: " + loginResponse.getStatusLine().getStatusCode());
          Thread.sleep(10000);
        }
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Failed to get vehicle information", e);
        client.getConnectionManager().shutdown();
        client = new DefaultHttpClient();
        Thread.sleep(period * _1_MIN_MILLIS);
      }
    }
  }

  public JsonNode getResult(String url) throws IOException {
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

  interface VehicleCaller {
    boolean call(long vehicleId, JsonNode vehicle);
  }
}

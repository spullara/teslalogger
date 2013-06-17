package teslalogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Estimate the energy per mile
 * <p/>
 * User: sam
 * Date: 3/23/13
 * Time: 5:00 PM
 */
public class EstimatedVsActual {
  @Argument(alias = "f", description = "Log file to parse", required = true)
  private static File file;

  public static void main(String[] args) throws IOException {
    try {
      Args.parse(EstimatedVsActual.class, args);
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      Args.usage(EstimatedVsActual.class);
      System.exit(1);
      return;
    }
    BufferedReader br = new BufferedReader(new FileReader(file));
    MappingJsonFactory jf = new MappingJsonFactory();
    String line;
    double lastlat = 0;
    double lastlon = 0;
    while ((line = br.readLine()) != null) {
      JsonNode jsonNode = jf.createParser(line).readValueAsTree();
      JsonNode drive = jsonNode.get("drive");
      JsonNode charge = jsonNode.get("charge");

      double latitude = drive.get("latitude").asDouble();
      double longitude = drive.get("longitude").asDouble();
      if (drive.get("speed") != null) {
        // Moving

      }

      lastlat = latitude;
      lastlon = longitude;
    }
  }
}

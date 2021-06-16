package org.embulk.input.google_ads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.embulk.config.ConfigSource;
import org.embulk.spi.InputPlugin;
import org.embulk.test.TestingEmbulk;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TestGoogleAdsReporter
{
    @Rule
    public TestingEmbulk embulk = TestingEmbulk.builder()
            .registerPlugin(InputPlugin.class, "google_ads", GoogleAdsInputPlugin.class)
            .build();

    @Test
    public void testTraverse()
    {
        String json =
                "{" +
                        "    \"person\": {" +
                        "        \"firstName\": \"John\"," +
                        "        \"lastName\": \"Doe\"," +
                        "        \"address\": \"NewYork\"," +
                        "        \"pets\": [" +
                        "            {\"type\": \"Dog\", \"animalName\": \"Jolly\"}," +
                        "            {\"type\": \"Cat\", \"animalName\": \"Grizabella\"}," +
                        "            {\"type\": \"Fish\", \"animalName\": \"Nimo\"}" +
                        "        ]" +
                        "    }" +
                        "}";
        ObjectMapper mapper = new ObjectMapper();
        ConfigSource conf = TestHelper.getBaseConfig(embulk);
        GoogleAdsReporter reporter = new GoogleAdsReporter(conf.loadConfig(PluginTask.class));
        try {
            JsonNode jsonNode = mapper.readTree(json);
            JsonNode resultJson = reporter.traverse(jsonNode);
            Assert.assertEquals("{\"person\":{\"first_name\":\"John\",\"last_name\":\"Doe\",\"address\":\"NewYork\",\"pets\":[{\"type\":\"Dog\",\"animal_name\":\"Jolly\"},{\"type\":\"Cat\",\"animal_name\":\"Grizabella\"},{\"type\":\"Fish\",\"animal_name\":\"Nimo\"}]}}",
                    mapper.writeValueAsString(resultJson));
        }
        catch (Exception ignored) {
        }
    }
}

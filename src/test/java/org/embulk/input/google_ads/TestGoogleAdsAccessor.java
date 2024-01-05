package org.embulk.input.google_ads;

import org.embulk.config.ConfigSource;
import org.embulk.spi.InputPlugin;
import org.embulk.test.TestingEmbulk;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;

public class TestGoogleAdsAccessor
{
    @Rule
    public TestingEmbulk embulk = TestingEmbulk.builder()
            .registerPlugin(InputPlugin.class, "google_ads", GoogleAdsInputPlugin.class)
            .build();

    @Test
    public void testAccessor()
    {
        HashMap<String, String> row = new HashMap<>();
        row.put("name", "value");
        row.put("name2", "value2");

        ConfigSource conf = TestHelper.getBaseConfig(embulk);
        GoogleAdsAccessor accessor = new GoogleAdsAccessor(TestHelper.loadTask(conf), row);
        String result = accessor.get("name");
        String notExist = accessor.get("not_exist");

        Assert.assertEquals("value", result);
        Assert.assertNull(notExist);
    }
}

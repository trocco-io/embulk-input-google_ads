package org.embulk.input.google_ads;

import org.embulk.config.ConfigSource;
import org.embulk.spi.InputPlugin;
import org.embulk.test.TestingEmbulk;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TestGoogleAdsUtil
{
    @Rule
    public TestingEmbulk embulk = TestingEmbulk.builder()
            .registerPlugin(InputPlugin.class, "google_ads", GoogleAdsInputPlugin.class)
            .build();

    @Test
    public void testEscapeColumnName_not_escaped()
    {
        ConfigSource conf = TestHelper.getBaseConfig(embulk);
        String escapedName = GoogleAdsUtil.escapeColumnName("ad.ad_group.ad", TestHelper.loadTask(conf));
        Assert.assertEquals("ad.ad_group.ad", escapedName);
    }

    @Test
    public void testEscapeColumnName_escaped()
    {
        ConfigSource conf = TestHelper.getBaseConfig(embulk);
        conf.set("_replace_dot_in_column", "true");
        String escapedName = GoogleAdsUtil.escapeColumnName("ad.ad_group.ad", TestHelper.loadTask(conf));
        Assert.assertEquals("ad_ad_group_ad", escapedName);
    }

    @Test
    public void testEscapeColumnName_escaped_with_slash()
    {
        ConfigSource conf = TestHelper.getBaseConfig(embulk);
        conf.set("_replace_dot_in_column", "true");
        conf.set("_replace_dot_in_column_with", "/");
        String escapedName = GoogleAdsUtil.escapeColumnName("ad.ad_group.ad", TestHelper.loadTask(conf));
        Assert.assertEquals("ad/ad_group/ad", escapedName);
    }
}

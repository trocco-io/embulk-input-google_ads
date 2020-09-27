package org.embulk.input.google_ads;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TestGoogleAdsValueConverter {

    @Test
    public void testShouldApplyMicro(){
        List<String> microFields = Arrays.asList("metrics.cost_micros", "metrics.average_cpc", "metrics.cost_per_all_conversions");
        for (String field : microFields){
            Assert.assertTrue(GoogleAdsValueConverter.shouldApplyMicro(field));
        }
    }

    @Test
    public void testShouldApplyMicro_false(){
        Assert.assertFalse(GoogleAdsValueConverter.shouldApplyMicro("campaign.name"));
    }

    @Test
    public void testApplyMicro(){
        Assert.assertEquals(GoogleAdsValueConverter.applyMicro("10000000"), "10.0");
        Assert.assertEquals(GoogleAdsValueConverter.applyMicro("12300000"), "12.3");
        Assert.assertEquals(GoogleAdsValueConverter.applyMicro("12345678"), "12.345678");
    }
}

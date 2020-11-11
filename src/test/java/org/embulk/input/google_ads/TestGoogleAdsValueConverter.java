package org.embulk.input.google_ads;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TestGoogleAdsValueConverter {

    @Test
    public void testShouldApplyMicro(){
        List<String> microFields = Arrays.asList(
                "metrics.cost_micros",
                "metrics.average_cpc",
                "metrics.cost_per_all_conversions",
                "metrics.active_view_cpm",
                "metrics.active_view_measurable_cost_micros",
                "metrics.average_cost",
                "metrics.average_cpm",
                "metrics.cost_per_conversion",
                "metrics.benchmark_average_max_cpc",
                "ad_group_criterion.effective_cpc_bid_micros",
                "ad_group_criterion.effective_cpm_bid_micros",
                "ad_group_criterion.position_estimates.estimated_add_cost_at_first_position_cpc",
                "ad_group.cpc_bid_micros",
                "ad_group.effective_target_cpa_micros",
                "bidding_strategy.target_cpa.cpc_bid_ceiling_micros",
                "bidding_strategy.target_cpa.cpc_bid_floor_micros",
                "bidding_strategy.target_roas.cpc_bid_ceiling_micros",
                "bidding_strategy.target_roas.cpc_bid_floor_micros",
                "bidding_strategy.target_spend.cpc_bid_ceiling_micros",
                "bidding_strategy.target_spend.target_spend_micros",
                "campaign_budget.amount_micros",
                "campaign_budget.recommended_budget_amount_micros"
        );
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

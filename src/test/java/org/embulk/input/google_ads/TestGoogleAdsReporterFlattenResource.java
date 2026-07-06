package org.embulk.input.google_ads;

import com.google.ads.googleads.v24.resources.Campaign;
import com.google.ads.googleads.v24.resources.AdGroup;
import com.google.ads.googleads.v24.services.GoogleAdsRow;
import org.embulk.util.config.units.ColumnConfig;
import org.embulk.util.config.units.SchemaConfig;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for flattenResource with protobuf 4.x messages.
 *
 * Background: google-ads 43.x uses protobuf 4.x, where generated classes extend
 * GeneratedMessage instead of GeneratedMessageV3. The previous instanceof GeneratedMessageV3
 * check in flattenResource always returned false for v24 messages, preventing recursion
 * into nested messages and causing all column values to be null.
 *
 * These tests do not use TestingEmbulk to avoid a pre-existing JAXB incompatibility
 * with Java 11+. PluginTask and its dependencies are mocked with Mockito instead.
 */
public class TestGoogleAdsReporterFlattenResource
{
    private GoogleAdsReporter createReporter(String... columnNames)
    {
        PluginTask mockTask = Mockito.mock(PluginTask.class);
        Mockito.when(mockTask.getClientId()).thenReturn("dummy");
        Mockito.when(mockTask.getClientSecret()).thenReturn("dummy");
        Mockito.when(mockTask.getRefreshToken()).thenReturn("dummy");

        List<ColumnConfig> columns = new ArrayList<>();
        for (String name : columnNames) {
            ColumnConfig col = Mockito.mock(ColumnConfig.class);
            Mockito.when(col.getName()).thenReturn(name);
            columns.add(col);
        }

        SchemaConfig mockSchema = Mockito.mock(SchemaConfig.class);
        Mockito.when(mockSchema.getColumns()).thenReturn(columns);
        Mockito.when(mockTask.getFields()).thenReturn(mockSchema);

        return new GoogleAdsReporter(mockTask);
    }

    @Test
    public void testFlattenResourceExtractsNestedStringField()
    {
        GoogleAdsReporter reporter = createReporter("campaign.name");

        Campaign campaign = Campaign.newBuilder().setName("test_campaign").build();
        GoogleAdsRow row = GoogleAdsRow.newBuilder().setCampaign(campaign).build();

        Map<String, String> result = new HashMap<>();
        reporter.flattenResource(null, row.getAllFields(), result);

        Assert.assertEquals("test_campaign", result.get("campaign.name"));
    }

    @Test
    public void testFlattenResourceExtractsNestedLongField()
    {
        GoogleAdsReporter reporter = createReporter("campaign.id");

        Campaign campaign = Campaign.newBuilder().setId(12345L).build();
        GoogleAdsRow row = GoogleAdsRow.newBuilder().setCampaign(campaign).build();

        Map<String, String> result = new HashMap<>();
        reporter.flattenResource(null, row.getAllFields(), result);

        Assert.assertEquals("12345", result.get("campaign.id"));
    }

    @Test
    public void testFlattenResourceExtractsMultipleNestedFields()
    {
        GoogleAdsReporter reporter = createReporter("campaign.id", "campaign.name");

        Campaign campaign = Campaign.newBuilder()
                .setId(12345L)
                .setName("test_campaign")
                .build();
        GoogleAdsRow row = GoogleAdsRow.newBuilder().setCampaign(campaign).build();

        Map<String, String> result = new HashMap<>();
        reporter.flattenResource(null, row.getAllFields(), result);

        Assert.assertEquals("12345", result.get("campaign.id"));
        Assert.assertEquals("test_campaign", result.get("campaign.name"));
    }

    @Test
    public void testFlattenResourceExtractsFieldsFromMultipleResources()
    {
        GoogleAdsReporter reporter = createReporter("campaign.name", "ad_group.name");

        Campaign campaign = Campaign.newBuilder().setName("test_campaign").build();
        AdGroup adGroup = AdGroup.newBuilder().setName("test_ad_group").build();
        GoogleAdsRow row = GoogleAdsRow.newBuilder()
                .setCampaign(campaign)
                .setAdGroup(adGroup)
                .build();

        Map<String, String> result = new HashMap<>();
        reporter.flattenResource(null, row.getAllFields(), result);

        Assert.assertEquals("test_campaign", result.get("campaign.name"));
        Assert.assertEquals("test_ad_group", result.get("ad_group.name"));
    }

    @Test
    public void testFlattenResourceReturnsNullForUnsetField()
    {
        GoogleAdsReporter reporter = createReporter("campaign.name");

        // Campaign with no name set — protobuf default is empty string,
        // which is omitted from getAllFields(), so the key won't appear in result.
        Campaign campaign = Campaign.newBuilder().setId(1L).build();
        GoogleAdsRow row = GoogleAdsRow.newBuilder().setCampaign(campaign).build();

        Map<String, String> result = new HashMap<>();
        reporter.flattenResource(null, row.getAllFields(), result);

        Assert.assertNull(result.get("campaign.name"));
    }
}

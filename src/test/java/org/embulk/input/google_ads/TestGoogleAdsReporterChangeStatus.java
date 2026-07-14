package org.embulk.input.google_ads;

import org.embulk.util.config.units.ColumnConfig;
import org.embulk.util.config.units.SchemaConfig;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tests for buildQuery with the change_status resource.
 *
 * https://developers.google.com/google-ads/api/docs/change-status
 * change_status queries must be filtered by change_status.last_change_date_time,
 * in the same way change_event queries are filtered by change_event.change_date_time.
 *
 * These tests do not use TestingEmbulk to avoid a pre-existing JAXB incompatibility
 * with Java 11+. PluginTask and its dependencies are mocked with Mockito instead.
 */
public class TestGoogleAdsReporterChangeStatus
{
    private PluginTask mockTask(String resourceType, Optional<GoogleAdsDateRange> dateRange)
    {
        return mockTask(resourceType, dateRange, resourceType + ".resource_name");
    }

    private PluginTask mockTask(String resourceType, Optional<GoogleAdsDateRange> dateRange, String columnName)
    {
        PluginTask task = Mockito.mock(PluginTask.class);
        Mockito.when(task.getClientId()).thenReturn("dummy");
        Mockito.when(task.getClientSecret()).thenReturn("dummy");
        Mockito.when(task.getRefreshToken()).thenReturn("dummy");
        Mockito.when(task.getResourceType()).thenReturn(resourceType);
        Mockito.when(task.getLimit()).thenReturn(Optional.empty());
        Mockito.when(task.getDateRange()).thenReturn(dateRange);
        Mockito.when(task.getConditions()).thenReturn(Optional.empty());

        ColumnConfig col = Mockito.mock(ColumnConfig.class);
        Mockito.when(col.getName()).thenReturn(columnName);
        List<ColumnConfig> columns = new ArrayList<>();
        columns.add(col);
        SchemaConfig schema = Mockito.mock(SchemaConfig.class);
        Mockito.when(schema.getColumns()).thenReturn(columns);
        Mockito.when(task.getFields()).thenReturn(schema);

        return task;
    }

    private static final Optional<GoogleAdsDateRange> DATE_RANGE =
            Optional.of(new GoogleAdsDateRange("2026-07-01 00:00:00", "2026-07-14 00:00:00"));

    @Test
    public void testChangeStatusQueryContainsDateTimeFilter()
    {
        PluginTask task = mockTask("change_status", DATE_RANGE);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        String query = reporter.buildQuery(task, new HashMap<>());

        Assert.assertTrue(query.contains("change_status.last_change_date_time  >= '2026-07-01 00:00:00'"));
        Assert.assertTrue(query.contains("change_status.last_change_date_time  <= '2026-07-14 00:00:00'"));
        Assert.assertTrue(query.contains("ORDER BY change_status.last_change_date_time ASC"));
    }

    @Test
    public void testChangeStatusQueryUsesStartDatetimeParamForPagination()
    {
        PluginTask task = mockTask("change_status", DATE_RANGE);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        Map<String, String> params = new HashMap<>();
        params.put("start_datetime", "2026-07-05 12:34:56");
        String query = reporter.buildQuery(task, params);

        // Inclusive lower bound: rows sharing the boundary timestamp are re-fetched
        // and deduplicated by resource name, so they are not lost at the LIMIT boundary.
        Assert.assertTrue(query.contains("change_status.last_change_date_time  >= '2026-07-05 12:34:56'"));
        Assert.assertFalse(query.contains("'2026-07-01 00:00:00'"));
        Assert.assertTrue(query.contains("change_status.last_change_date_time  <= '2026-07-14 00:00:00'"));
    }

    @Test
    public void testChangeStatusQuerySelectsPaginationFields()
    {
        PluginTask task = mockTask("change_status", DATE_RANGE);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        String query = reporter.buildQuery(task, new HashMap<>());

        String select = query.substring(0, query.indexOf(" FROM "));
        Assert.assertTrue(select.contains("change_status.last_change_date_time"));
        Assert.assertTrue(select.contains("change_status.resource_name"));
    }

    @Test
    public void testChangeEventQuerySelectsPaginationFields()
    {
        PluginTask task = mockTask("change_event", DATE_RANGE);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        String query = reporter.buildQuery(task, new HashMap<>());

        String select = query.substring(0, query.indexOf(" FROM "));
        Assert.assertTrue(select.contains("change_event.change_date_time"));
        Assert.assertTrue(select.contains("change_event.resource_name"));
    }

    @Test
    public void testPaginationFieldsAreNotDuplicatedInSelect()
    {
        PluginTask task = mockTask("change_status", DATE_RANGE, "change_status.last_change_date_time");
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        String query = reporter.buildQuery(task, new HashMap<>());

        String select = query.substring(0, query.indexOf(" FROM "));
        Assert.assertEquals(1, countOccurrences(select, "change_status.last_change_date_time"));
    }

    private int countOccurrences(String haystack, String needle)
    {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    public void testChangeEventQueryStillContainsDateTimeFilter()
    {
        PluginTask task = mockTask("change_event", DATE_RANGE);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        String query = reporter.buildQuery(task, new HashMap<>());

        Assert.assertTrue(query.contains("change_event.change_date_time  >= '2026-07-01 00:00:00'"));
        Assert.assertTrue(query.contains("change_event.change_date_time  <= '2026-07-14 00:00:00'"));
        Assert.assertTrue(query.contains("ORDER BY change_event.change_date_time ASC"));
    }

    @Test
    public void testNonChangeResourceUsesSegmentsDate()
    {
        PluginTask task = mockTask("campaign", DATE_RANGE);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);

        String query = reporter.buildQuery(task, new HashMap<>());

        Assert.assertTrue(query.contains("segments.date BETWEEN '2026-07-01 00:00:00' AND '2026-07-14 00:00:00'"));
        Assert.assertFalse(query.contains("change_status"));
    }
}

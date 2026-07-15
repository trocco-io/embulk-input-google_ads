package org.embulk.input.google_ads;

import com.google.ads.googleads.v24.resources.ChangeEvent;
import com.google.ads.googleads.v24.resources.ChangeStatus;
import com.google.ads.googleads.v24.services.GoogleAdsRow;
import com.google.ads.googleads.v24.services.GoogleAdsServiceClient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tests for the pagination loop in search(Consumer, params): dedup across rounds,
 * start_datetime propagation, early termination below LIMIT, and termination when
 * no new rows appear. The per-round query is stubbed by overriding search(Map).
 */
public class TestGoogleAdsReporterSearchLoop
{
    private static class FakeReporter extends GoogleAdsReporter
    {
        private final List<List<GoogleAdsRow>> rounds;
        final List<Map<String, String>> receivedParams = new ArrayList<>();
        private int calls = 0;

        FakeReporter(PluginTask task, List<List<GoogleAdsRow>> rounds)
        {
            super(task);
            this.rounds = rounds;
        }

        @Override
        Iterable<GoogleAdsServiceClient.SearchPage> search(Map<String, String> params)
        {
            receivedParams.add(new HashMap<>(params));
            List<GoogleAdsRow> rows = calls < rounds.size() ? rounds.get(calls) : Collections.emptyList();
            calls++;
            GoogleAdsServiceClient.SearchPage page = Mockito.mock(GoogleAdsServiceClient.SearchPage.class);
            Mockito.when(page.getValues()).thenReturn(rows);
            return Collections.singletonList(page);
        }
    }

    private PluginTask mockTask(String resourceType, Optional<String> limit)
    {
        PluginTask task = Mockito.mock(PluginTask.class);
        Mockito.when(task.getClientId()).thenReturn("dummy");
        Mockito.when(task.getClientSecret()).thenReturn("dummy");
        Mockito.when(task.getRefreshToken()).thenReturn("dummy");
        Mockito.when(task.getResourceType()).thenReturn(resourceType);
        Mockito.when(task.getLimit()).thenReturn(limit);
        return task;
    }

    private GoogleAdsRow changeStatusRow(String dateTime, String resourceName)
    {
        return GoogleAdsRow.newBuilder()
                .setChangeStatus(ChangeStatus.newBuilder()
                        .setLastChangeDateTime(dateTime)
                        .setResourceName(resourceName)
                        .build())
                .build();
    }

    private GoogleAdsRow changeEventRow(String dateTime, String resourceName)
    {
        return GoogleAdsRow.newBuilder()
                .setChangeEvent(ChangeEvent.newBuilder()
                        .setChangeDateTime(dateTime)
                        .setResourceName(resourceName)
                        .build())
                .build();
    }

    private List<String> emittedChangeStatusNames(FakeReporter reporter)
    {
        List<GoogleAdsRow> emitted = new ArrayList<>();
        reporter.search(emitted::add, new HashMap<>());
        return emitted.stream().map(row -> row.getChangeStatus().getResourceName()).collect(Collectors.toList());
    }

    private static final String T1 = "2026-07-01 00:00:01";
    private static final String T2 = "2026-07-01 00:00:02";
    private static final String T3 = "2026-07-01 00:00:03";

    @Test
    public void testDedupAcrossRoundsAndStartDatetimePropagation()
    {
        // LIMIT=3; the LIMIT boundary cuts inside the T2 group twice.
        FakeReporter reporter = new FakeReporter(mockTask("change_status", Optional.of("3")), Arrays.asList(
                Arrays.asList(changeStatusRow(T1, "a"), changeStatusRow(T2, "b"), changeStatusRow(T2, "c")),
                Arrays.asList(changeStatusRow(T2, "b"), changeStatusRow(T2, "c"), changeStatusRow(T2, "d")),
                Arrays.asList(changeStatusRow(T2, "c"), changeStatusRow(T2, "d"), changeStatusRow(T3, "e"))
        ));

        List<String> emitted = emittedChangeStatusNames(reporter);

        Assert.assertEquals(Arrays.asList("a", "b", "c", "d", "e"), emitted);
        Assert.assertEquals(4, reporter.receivedParams.size());
        Assert.assertNull(reporter.receivedParams.get(0).get("start_datetime"));
        Assert.assertEquals(T2, reporter.receivedParams.get(1).get("start_datetime"));
        Assert.assertEquals(T2, reporter.receivedParams.get(2).get("start_datetime"));
        Assert.assertEquals(T3, reporter.receivedParams.get(3).get("start_datetime"));
    }

    @Test
    public void testEarlyTerminationBelowLimit()
    {
        FakeReporter reporter = new FakeReporter(mockTask("change_status", Optional.of("10")), Collections.singletonList(
                Arrays.asList(changeStatusRow(T1, "a"), changeStatusRow(T2, "b"))
        ));

        List<String> emitted = emittedChangeStatusNames(reporter);

        Assert.assertEquals(Arrays.asList("a", "b"), emitted);
        Assert.assertEquals(1, reporter.receivedParams.size());
    }

    @Test
    public void testNoLimitStopsAfterFirstRound()
    {
        FakeReporter reporter = new FakeReporter(mockTask("change_status", Optional.empty()), Collections.singletonList(
                Arrays.asList(changeStatusRow(T1, "a"), changeStatusRow(T2, "b"), changeStatusRow(T3, "c"))
        ));

        List<String> emitted = emittedChangeStatusNames(reporter);

        Assert.assertEquals(Arrays.asList("a", "b", "c"), emitted);
        Assert.assertEquals(1, reporter.receivedParams.size());
    }

    @Test
    public void testStopsWithoutDuplicatesWhenNoNewRowsAppear()
    {
        // More rows than LIMIT share T1: the second round returns the same rows only.
        FakeReporter reporter = new FakeReporter(mockTask("change_status", Optional.of("2")), Arrays.asList(
                Arrays.asList(changeStatusRow(T1, "a"), changeStatusRow(T1, "b")),
                Arrays.asList(changeStatusRow(T1, "a"), changeStatusRow(T1, "b"))
        ));

        List<String> emitted = emittedChangeStatusNames(reporter);

        Assert.assertEquals(Arrays.asList("a", "b"), emitted);
        Assert.assertEquals(2, reporter.receivedParams.size());
    }

    @Test
    public void testChangeEventUsesChangeDateTimeForNextRound()
    {
        FakeReporter reporter = new FakeReporter(mockTask("change_event", Optional.of("2")), Collections.singletonList(
                Arrays.asList(changeEventRow(T1, "x"), changeEventRow(T2, "y"))
        ));

        List<GoogleAdsRow> emitted = new ArrayList<>();
        reporter.search(emitted::add, new HashMap<>());

        Assert.assertEquals(2, emitted.size());
        Assert.assertEquals(2, reporter.receivedParams.size());
        Assert.assertEquals(T2, reporter.receivedParams.get(1).get("start_datetime"));
    }

    @Test
    public void testNonChangeResourceIssuesSingleQuery()
    {
        FakeReporter reporter = new FakeReporter(mockTask("campaign", Optional.of("2")), Collections.singletonList(
                Arrays.asList(GoogleAdsRow.newBuilder().build(), GoogleAdsRow.newBuilder().build())
        ));

        List<GoogleAdsRow> emitted = new ArrayList<>();
        reporter.search(emitted::add, new HashMap<>());

        Assert.assertEquals(2, emitted.size());
        Assert.assertEquals(1, reporter.receivedParams.size());
    }
}

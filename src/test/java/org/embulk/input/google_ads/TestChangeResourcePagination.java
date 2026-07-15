package org.embulk.input.google_ads;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Optional;

/**
 * Tests for the deduplicating date-time pagination used by change_event / change_status.
 *
 * The next query round uses an inclusive lower bound (>=) on the last row's timestamp,
 * so rows sharing that timestamp across the LIMIT boundary are re-fetched instead of
 * being lost. Rows already emitted at the boundary timestamp are skipped by resource name.
 */
public class TestChangeResourcePagination
{
    @Test
    public void testFirstRoundEmitsAllRows()
    {
        GoogleAdsReporter.ChangeResourcePagination pagination = new GoogleAdsReporter.ChangeResourcePagination();

        pagination.startRound();
        Assert.assertTrue(pagination.shouldEmit("2026-07-01 00:00:01", "a"));
        Assert.assertTrue(pagination.shouldEmit("2026-07-01 00:00:01", "b"));
        Assert.assertTrue(pagination.shouldEmit("2026-07-01 00:00:02", "c"));

        Assert.assertTrue(pagination.hasEmittedNewRow());
        Assert.assertFalse(pagination.isRoundEmpty());
        Assert.assertEquals(3, pagination.getRowsReturned());
        Assert.assertEquals("2026-07-01 00:00:02", pagination.getBoundaryDateTime());
    }

    @Test
    public void testSecondRoundSkipsRowsAlreadyEmittedAtBoundary()
    {
        GoogleAdsReporter.ChangeResourcePagination pagination = new GoogleAdsReporter.ChangeResourcePagination();

        // Round 1: LIMIT cuts in the middle of the 00:00:02 group (c emitted, d not fetched).
        pagination.startRound();
        pagination.shouldEmit("2026-07-01 00:00:01", "a");
        pagination.shouldEmit("2026-07-01 00:00:02", "b");
        pagination.shouldEmit("2026-07-01 00:00:02", "c");

        // Round 2 re-fetches from >= 00:00:02: b and c are skipped, d and e are new.
        pagination.startRound();
        Assert.assertFalse(pagination.shouldEmit("2026-07-01 00:00:02", "b"));
        Assert.assertFalse(pagination.shouldEmit("2026-07-01 00:00:02", "c"));
        Assert.assertTrue(pagination.shouldEmit("2026-07-01 00:00:02", "d"));
        Assert.assertTrue(pagination.shouldEmit("2026-07-01 00:00:03", "e"));

        Assert.assertTrue(pagination.hasEmittedNewRow());
        Assert.assertEquals(4, pagination.getRowsReturned());
        Assert.assertEquals("2026-07-01 00:00:03", pagination.getBoundaryDateTime());
    }

    @Test
    public void testFinalRoundWithOnlyEmittedRowsReportsNoNewRow()
    {
        GoogleAdsReporter.ChangeResourcePagination pagination = new GoogleAdsReporter.ChangeResourcePagination();

        pagination.startRound();
        pagination.shouldEmit("2026-07-01 00:00:01", "a");
        pagination.shouldEmit("2026-07-01 00:00:02", "b");

        // Round 2 re-fetches from >= 00:00:02 and returns only the already emitted row.
        pagination.startRound();
        Assert.assertFalse(pagination.shouldEmit("2026-07-01 00:00:02", "b"));

        Assert.assertFalse(pagination.hasEmittedNewRow());
        Assert.assertFalse(pagination.isRoundEmpty());
        Assert.assertEquals(1, pagination.getRowsReturned());
    }

    @Test
    public void testEmptyRound()
    {
        GoogleAdsReporter.ChangeResourcePagination pagination = new GoogleAdsReporter.ChangeResourcePagination();

        pagination.startRound();

        Assert.assertTrue(pagination.isRoundEmpty());
        Assert.assertFalse(pagination.hasEmittedNewRow());
    }

    @Test
    public void testIsRowCountAtLimit()
    {
        // A round returning fewer rows than LIMIT means the result was not truncated,
        // so pagination stops without issuing an extra confirmation query.
        Assert.assertFalse(reporterWithLimit(Optional.of("10000")).isRowCountAtLimit(9999));
        Assert.assertTrue(reporterWithLimit(Optional.of("10000")).isRowCountAtLimit(10000));
        Assert.assertFalse(reporterWithLimit(Optional.empty()).isRowCountAtLimit(10000));
        Assert.assertFalse(reporterWithLimit(Optional.of("abc")).isRowCountAtLimit(10000));
    }

    private GoogleAdsReporter reporterWithLimit(Optional<String> limit)
    {
        PluginTask task = Mockito.mock(PluginTask.class);
        Mockito.when(task.getClientId()).thenReturn("dummy");
        Mockito.when(task.getClientSecret()).thenReturn("dummy");
        Mockito.when(task.getRefreshToken()).thenReturn("dummy");
        Mockito.when(task.getLimit()).thenReturn(limit);
        return new GoogleAdsReporter(task);
    }

    @Test
    public void testAdvancingTimestampResetsBoundaryNames()
    {
        GoogleAdsReporter.ChangeResourcePagination pagination = new GoogleAdsReporter.ChangeResourcePagination();

        pagination.startRound();
        pagination.shouldEmit("2026-07-01 00:00:01", "a");
        pagination.shouldEmit("2026-07-01 00:00:02", "b");

        // The same resource name is allowed again once the boundary has advanced past
        // its timestamp (change_event rows for one resource have distinct timestamps).
        pagination.startRound();
        Assert.assertFalse(pagination.shouldEmit("2026-07-01 00:00:02", "b"));
        Assert.assertTrue(pagination.shouldEmit("2026-07-01 00:00:03", "a"));
        Assert.assertEquals("2026-07-01 00:00:03", pagination.getBoundaryDateTime());
    }
}

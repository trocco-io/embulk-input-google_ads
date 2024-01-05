package org.embulk.input.google_ads;

import com.google.common.collect.ImmutableList;
import org.embulk.EmbulkTestRuntime;
import org.embulk.config.ConfigSource;
import org.embulk.spi.*;
import org.embulk.spi.json.JsonObject;
import org.embulk.spi.json.JsonValue;
import org.embulk.spi.time.Instants;
import org.embulk.spi.time.Timestamp;
import org.embulk.test.TestingEmbulk;
import org.embulk.util.config.units.ColumnConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;

public class TestGoogleAdsColumnVisitor
{
    @Rule
    public TestingEmbulk embulk = TestingEmbulk.builder()
            .registerPlugin(InputPlugin.class, "google_ads", GoogleAdsInputPlugin.class)
            .build();

    @Rule public EmbulkTestRuntime runtime;

    @Before
    public void setUp() {
        runtime = new EmbulkTestRuntime();
    }

    @Test
    public void testStringColumn()
    {
        PageReader pageReader = visitColumns("100", embulk.newConfig().set("name", "name").set("type", "string"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertEquals("100", pageReader.getString(0));
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testLongColumn()
    {
        PageReader pageReader = visitColumns("100", embulk.newConfig().set("name", "name").set("type", "long"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertEquals(100, pageReader.getLong(0));
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testBooleanColumnTrue()
    {
        PageReader pageReader = visitColumns("true", embulk.newConfig().set("name", "name").set("type", "boolean"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertTrue(pageReader.getBoolean(0));
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testBooleanColumnFalse()
    {
        PageReader pageReader = visitColumns("false", embulk.newConfig().set("name", "name").set("type", "boolean"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertFalse(pageReader.getBoolean(0));
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testDoubleColumn()
    {
        PageReader pageReader = visitColumns("1.23", embulk.newConfig().set("name", "name").set("type", "double"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertEquals(1.23, pageReader.getDouble(0), 0.01);
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testTimestampColumn()
    {
        PageReader pageReader = visitColumns("1970-01-01T09:00:10+09:00", embulk.newConfig().set("name", "name").set("type", "timestamp"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertEquals(Instant.ofEpochSecond(10), pageReader.getTimestampInstant(0));
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testTimestampColumnWithFormat()
    {
        PageReader pageReader = visitColumns("1970/01/01 00:00:10", embulk.newConfig().set("name", "name").set("type", "timestamp").set("format", "%Y/%m/%d %H:%M:%S"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertEquals(Instant.ofEpochSecond(10), pageReader.getTimestampInstant(0));
        Assert.assertFalse(pageReader.nextRecord());
    }

    @Test
    public void testJsonColumn()
    {
        PageReader pageReader = visitColumns("{\"key0\":\"value0\"}", embulk.newConfig().set("name", "name").set("type", "json"));
        Assert.assertTrue(pageReader.nextRecord());
        Assert.assertEquals("{\"key0\":\"value0\"}", pageReader.getJsonValue(0).toJson());
        Assert.assertFalse(pageReader.nextRecord());
    }

    private PageReader visitColumns(String value, ConfigSource configSource) {
        ConfigSource conf = TestHelper.getBaseConfigWithFields(embulk, configSource);

        PluginTask task = TestHelper.loadTask(conf);
        Schema schema = buildSchema(task);
        HashMap<String, String> row = new HashMap<>();
        row.put("name", value);
        GoogleAdsAccessor accessor = new GoogleAdsAccessor(task, row);
        final TestPageBuilderReader.MockPageOutput output = new TestPageBuilderReader.MockPageOutput();
        final PageBuilder pageBuilder = new PageBuilderImpl(runtime.getBufferAllocator(), schema, output);
        GoogleAdsColumnVisitor columnVisitor = new GoogleAdsColumnVisitor(accessor, pageBuilder, task);
        schema.visitColumns(columnVisitor);
        pageBuilder.addRecord();
        pageBuilder.finish();

        Assert.assertEquals(1, output.pages.size());

        PageReader pageReader = new PageReader(schema);
        pageReader.setPage(output.pages.get(0));

        return pageReader;
    }

    private Schema buildSchema(PluginTask task)
    {
        ImmutableList.Builder<Column> builder = ImmutableList.builder();
        for (int i = 0; i < task.getFields().size(); i++) {
            ColumnConfig columnConfig = task.getFields().getColumn(i);
            ColumnConfig escapedColumnConfig = new ColumnConfig(GoogleAdsUtil.escapeColumnName(columnConfig.getName(), task), columnConfig.getType(), columnConfig.getOption());
            builder.add(escapedColumnConfig.toColumn(i));
        }
        return new Schema(builder.build());
    }
}

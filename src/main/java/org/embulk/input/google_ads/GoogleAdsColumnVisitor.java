package org.embulk.input.google_ads;

import com.google.gson.JsonElement;
import org.embulk.spi.Column;

import org.embulk.spi.ColumnVisitor;
import org.embulk.spi.PageBuilder;

import org.embulk.spi.time.Timestamp;
import org.embulk.util.config.units.ColumnConfig;
import org.embulk.util.json.JsonParser;

import java.util.List;

public class GoogleAdsColumnVisitor implements ColumnVisitor
{
    private final PageBuilder pageBuilder;
    private final PluginTask task;
    private final GoogleAdsAccessor accessor;
    private static final String DEFAULT_TIMESTAMP_PATTERN = "%Y-%m-%dT%H:%M:%S%z";

    public GoogleAdsColumnVisitor(final GoogleAdsAccessor accessor, final PageBuilder pageBuilder, final PluginTask task)
    {
        this.accessor = accessor;
        this.pageBuilder = pageBuilder;
        this.task = task;
    }

    @Override
    public void stringColumn(Column column)
    {
        String data = accessor.get(column.getName());
        if (data == null) {
            pageBuilder.setNull(column);
        } else {
            pageBuilder.setString(column, data);
        }
    }

    @Override
    public void longColumn(Column column)
    {
        String data = accessor.get(column.getName());
        if (data == null) {
            pageBuilder.setNull(column);
        } else {
            pageBuilder.setLong(column, (long) Double.parseDouble(data));
        }
    }

    @Override
    public void booleanColumn(Column column)
    {
        String data = accessor.get(column.getName());
        if (data == null) {
            pageBuilder.setNull(column);
        } else {
            pageBuilder.setBoolean(column, Boolean.parseBoolean(data));
        }
    }

    @Override
    public void doubleColumn(Column column)
    {
        try {
            String data = accessor.get(column.getName());
            pageBuilder.setDouble(column, Double.parseDouble(data));
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void timestampColumn(Column column)
    {
        try {
            List<ColumnConfig> columnConfigs = task.getFields().getColumns();
            String pattern = DEFAULT_TIMESTAMP_PATTERN;
            for (ColumnConfig config : columnConfigs) {
                String configColumnName = GoogleAdsUtil.escapeColumnName(config.getName(), task);
                if (configColumnName.equals(column.getName())
                        && config.getConfigSource() != null
                        && config.getConfigSource().get(String.class, "format", null) != null) {
                    pattern = config.getConfigSource().get(String.class, "format", null);
                    break;
                }
            }
            Timestamp result = Timestamp.ofString(pattern);
            pageBuilder.setTimestamp(column, result);
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }

    @Override
    public void jsonColumn(Column column)
    {
        try {
            JsonElement data = com.google.gson.JsonParser.parseString(accessor.get(column.getName()));
            if (data.isJsonNull() || data.isJsonPrimitive()) {
                pageBuilder.setNull(column);
            } else {
                pageBuilder.setJson(column, new JsonParser().parse(data.toString()));
            }
        } catch (Exception e) {
            pageBuilder.setNull(column);
        }
    }
}

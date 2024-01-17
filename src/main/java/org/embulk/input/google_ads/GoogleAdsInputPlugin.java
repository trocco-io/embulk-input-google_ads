package org.embulk.input.google_ads;

import com.google.ads.googleads.v14.services.GoogleAdsRow;
import com.google.ads.googleads.v14.services.GoogleAdsServiceClient;
import com.google.common.collect.ImmutableList;

import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;

import org.embulk.spi.Column;
import org.embulk.spi.ColumnConfig;
import org.embulk.spi.Exec;
import org.embulk.spi.InputPlugin;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleAdsInputPlugin
        implements InputPlugin
{
    private final Logger logger = LoggerFactory.getLogger(GoogleAdsInputPlugin.class);

    @Override
    public ConfigDiff transaction(ConfigSource config,
                                  InputPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);
        Schema schema = buildSchema(task);

        int taskCount = 1;  // number of run() method calls

        return resume(task.dump(), schema, taskCount, control);
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
                             Schema schema, int taskCount,
                             InputPlugin.Control control)
    {
        control.run(taskSource, schema, taskCount);
        return Exec.newConfigDiff();
    }

    @Override
    public void cleanup(TaskSource taskSource,
                        Schema schema, int taskCount,
                        List<TaskReport> successTaskReports)
    {
    }

    @Override
    public TaskReport run(TaskSource taskSource,
                          Schema schema, int taskIndex,
                          PageOutput output)
    {
        PluginTask task = taskSource.loadTask(PluginTask.class);
        GoogleAdsReporter reporter = new GoogleAdsReporter(task);
        reporter.connect();
        try {
            try (PageBuilder pageBuilder = getPageBuilder(schema, output)) {
                Map<String, String> params = new HashMap<>();
                reporter.search(
                    searchPage -> {
                        for (GoogleAdsRow row : searchPage.getValues()) {
                            Map<String, String> result = new HashMap<>();
                            reporter.flattenResource(null, row.getAllFields(), result);
                            schema.visitColumns(new GoogleAdsColumnVisitor(new GoogleAdsAccessor(task, result), pageBuilder, task));
                            pageBuilder.addRecord();
                        }
                        pageBuilder.flush();
                    },
                    params
                );
                pageBuilder.finish();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw e;
        }

        return Exec.newTaskReport();
    }

    @Override
    public ConfigDiff guess(ConfigSource config)
    {
        return Exec.newConfigDiff();
    }

    public GoogleAdsReporter getClient(PluginTask task)
    {
        return new GoogleAdsReporter(task);
    }

    public Schema buildSchema(PluginTask task)
    {
        ImmutableList.Builder<Column> builder = ImmutableList.builder();
        for (int i = 0; i < task.getFields().size(); i++) {
            ColumnConfig columnConfig = task.getFields().getColumn(i);
            ColumnConfig escapedColumnConfig = new ColumnConfig(GoogleAdsUtil.escapeColumnName(columnConfig.getName(), task), columnConfig.getType(), columnConfig.getOption());
            builder.add(escapedColumnConfig.toColumn(i));
        }
        return new Schema(builder.build());
    }

    protected PageBuilder getPageBuilder(final Schema schema, final PageOutput output)
    {
        return new PageBuilder(Exec.getBufferAllocator(), schema, output);
    }
}

package org.embulk.input.google_ads;

import com.google.ads.googleads.v16.services.GoogleAdsRow;
import com.google.ads.googleads.v16.services.GoogleAdsServiceClient;
import com.google.common.collect.ImmutableList;

import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;

import org.embulk.spi.Column;
import org.embulk.spi.Exec;
import org.embulk.spi.InputPlugin;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;
import org.embulk.util.config.TaskMapper;
import org.embulk.util.config.units.ColumnConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoogleAdsInputPlugin
        implements InputPlugin
{
    private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY = ConfigMapperFactory.builder().addDefaultModules().build();
    private final Logger logger = LoggerFactory.getLogger(GoogleAdsInputPlugin.class);

    @Override
    public ConfigDiff transaction(ConfigSource config,
                                  InputPlugin.Control control)
    {
        final ConfigMapper configMapper = CONFIG_MAPPER_FACTORY.createConfigMapper();
        final PluginTask task = configMapper.map(config, PluginTask.class);
        Schema schema = buildSchema(task);

        int taskCount = 1;  // number of run() method calls

        return resume(task.toTaskSource(), schema, taskCount, control);
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
                             Schema schema, int taskCount,
                             InputPlugin.Control control)
    {
        control.run(taskSource, schema, taskCount);
        return CONFIG_MAPPER_FACTORY.newConfigDiff();
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
        final TaskMapper taskMapper = CONFIG_MAPPER_FACTORY.createTaskMapper();
        final PluginTask task = taskMapper.map(taskSource, PluginTask.class);

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

        return CONFIG_MAPPER_FACTORY.newTaskReport();
    }

    @Override
    public ConfigDiff guess(ConfigSource config)
    {
        return CONFIG_MAPPER_FACTORY.newConfigDiff();
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

    @SuppressWarnings("deprecation") // After　the end of embulk v0.9 support, use Exec.getPageBuilder
    protected PageBuilder getPageBuilder(final Schema schema, final PageOutput output)
    {
        return new PageBuilder(Exec.getBufferAllocator(), schema, output);
    }
}

package org.embulk.input.google_ads;

import org.embulk.config.ConfigSource;
import org.embulk.test.TestingEmbulk;
import org.embulk.util.config.ConfigMapper;
import org.embulk.util.config.ConfigMapperFactory;

import java.util.ArrayList;
import java.util.List;

public class TestHelper
{
    private static final ConfigMapperFactory CONFIG_MAPPER_FACTORY = ConfigMapperFactory.builder().addDefaultModules().build();

    private TestHelper()
    {
    }

    public static PluginTask loadTask(ConfigSource conf) {
        final ConfigMapper configMapper = CONFIG_MAPPER_FACTORY.createConfigMapper();
        return configMapper.map(conf, PluginTask.class);
    }

    public static ConfigSource getBaseConfig(TestingEmbulk embulk)
    {
        ConfigSource configSource = embulk.newConfig();
        configSource.set("customer_id", "customer_id");
        configSource.set("client_id", "client_id");
        configSource.set("client_secret", "client_secret");
        configSource.set("refresh_token", "refresh_token");
        configSource.set("developer_token", "developer_token");
        configSource.set("resource_type", "resource_type");
        ConfigSource field = embulk.newConfig();
        List<ConfigSource> fields = new ArrayList<ConfigSource>()
        {
        };
        field.set("name", "name");
        field.set("type", "string");
        fields.add(field);
        configSource.set("fields", fields);

        return configSource;
    }
}

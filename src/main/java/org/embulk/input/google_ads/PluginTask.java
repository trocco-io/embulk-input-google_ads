package org.embulk.input.google_ads;

import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.Task;
import org.embulk.spi.SchemaConfig;

import java.util.List;
import java.util.Optional;

public interface PluginTask
        extends Task
{
    @Config("customer_id")
    public String getCustomerId();

    @Config("login_customer_id")
    @ConfigDefault("null")
    public Optional<String> getLoginCustomerId();

    @Config("client_id")
    public String getClientId();

    @Config("client_secret")
    public String getClientSecret();

    @Config("refresh_token")
    public String getRefreshToken();

    @Config("developer_token")
    public String getDeveloperToken();

    @Config("resource_type")
    public String getResourceType();

    @Config("fields")
    public SchemaConfig getFields();

    @Config("conditions")
    @ConfigDefault("[]")
    public List<String> getConditions();

    // @Config("daterange")
    // @ConfigDefault("null")
    // public Optional<GoogleAdsDateRange> getDateRange();

    @Config("_replace_dot_in_column")
    @ConfigDefault("false")
    public boolean getReplaceDotInColumn();

    @Config("_replace_dot_in_column_with")
    @ConfigDefault("\"_\"")
    public String getReplaceDotInColumnWith();

}

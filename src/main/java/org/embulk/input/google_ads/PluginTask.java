package org.embulk.input.google_ads;

import org.embulk.util.config.Config;
import org.embulk.util.config.ConfigDefault;

import org.embulk.util.config.Task;
import org.embulk.util.config.units.SchemaConfig;

import java.util.List;
import java.util.Optional;

public interface PluginTask extends Task
{
    @Config("customer_id")
    String getCustomerId();

    @Config("login_customer_id")
    @ConfigDefault("null")
    Optional<String> getLoginCustomerId();

    @Config("client_id")
    String getClientId();

    @Config("client_secret")
    String getClientSecret();

    @Config("refresh_token")
    String getRefreshToken();

    @Config("developer_token")
    String getDeveloperToken();

    @Config("resource_type")
    String getResourceType();

    @Config("fields")
    SchemaConfig getFields();

    @Config("conditions")
    @ConfigDefault("null")
    Optional<List<String>> getConditions();

    @Config("daterange")
    @ConfigDefault("null")
    Optional<GoogleAdsDateRange> getDateRange();

    @Config("limit")
    @ConfigDefault("null")
    Optional<String> getLimit();

    @Config("_use_micro")
    @ConfigDefault("true")
    boolean getUseMicro();

    @Config("_replace_dot_in_column")
    @ConfigDefault("false")
    boolean getReplaceDotInColumn();

    @Config("_replace_dot_in_column_with")
    @ConfigDefault("\"_\"")
    String getReplaceDotInColumnWith();
}

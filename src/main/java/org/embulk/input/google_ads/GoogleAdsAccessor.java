package org.embulk.input.google_ads;

import java.util.Map;

public class GoogleAdsAccessor {
    private Map<String, String> row;
    private PluginTask task;
    public GoogleAdsAccessor(PluginTask task, Map<String, String> row){
        this.row = row;
        this.task = task;
    }
    public String get(String name){
        return row.get(GoogleAdsUtil.escapeColumnName(name, task));
    }
}

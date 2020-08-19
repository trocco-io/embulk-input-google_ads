package org.embulk.input.google_ads;

import java.util.Map;

public class GoogleAdsAccessor {
    private final Map<String, String> row;
    private final PluginTask task;

    public GoogleAdsAccessor(PluginTask task, Map<String, String> row) {
        this.row = row;
        this.task = task;
    }

    public String get(String name) {
        if (!task.getReplaceDotInColumn()) {
            return row.get(name);
        }

        for (Map.Entry<String, String> entry : row.entrySet()) {
            if (name.equals(GoogleAdsUtil.escapeColumnName(entry.getKey(), task))) {
                return entry.getValue();
            }
        }
        return null;
    }
}

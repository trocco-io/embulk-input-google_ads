package org.embulk.input.google_ads;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GoogleAdsDateRange {
    private final String min;
    private final String max;

    @JsonCreator
    public GoogleAdsDateRange(@JsonProperty("min") String min, @JsonProperty("max") String max) {
        this.min = min;
        this.max = max;
    }

    public String getMin() {
        return min;
    }

    public String getMax() {
        return max;
    }


}

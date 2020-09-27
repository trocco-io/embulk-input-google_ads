package org.embulk.input.google_ads;

import java.util.Arrays;
import java.util.List;

public class GoogleAdsValueConverter {
    private static final List<String> MICRO_FIELDS = Arrays.asList("metrics.cost_micros", "metrics.average_cpc", "metrics.cost_per_all_conversions");

    public static boolean shouldApplyMicro(String name){
        return MICRO_FIELDS.contains(name);
    }

    public static String applyMicro(String num){
        return String.valueOf(Double.parseDouble(num) / Math.pow(10, 6));
    }

}

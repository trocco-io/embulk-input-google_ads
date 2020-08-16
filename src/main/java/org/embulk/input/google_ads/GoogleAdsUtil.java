package org.embulk.input.google_ads;

public class GoogleAdsUtil {
    public static String escapeColumnName(String name, PluginTask task){
        if(task.getReplaceDotInColumn()){
            return  name.replaceAll("\\.", task.getReplaceDotInColumnWith());
        }else{
            return name;
        }
    }
}

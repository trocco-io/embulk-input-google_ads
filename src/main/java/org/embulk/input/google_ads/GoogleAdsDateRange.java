package org.embulk.input.google_ads;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GoogleAdsDateRange
{
    private final String startDate;
    private final String endDate;

    @JsonCreator
    public GoogleAdsDateRange(@JsonProperty("start_date") String startDate, @JsonProperty("end_date") String endDate)
    {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getStartDate()
    {
        return startDate;
    }

    public String getEndDate()
    {
        return endDate;
    }
}

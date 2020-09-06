package org.embulk.input.google_ads;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v5.services.GoogleAdsRow;
import com.google.ads.googleads.v5.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v5.services.SearchGoogleAdsRequest;
import com.google.auth.oauth2.UserCredentials;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import org.embulk.spi.ColumnConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.msgpack.core.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GoogleAdsReporter {
    private static final int PAGE_SIZE = 1000;
    private final Logger logger = LoggerFactory.getLogger(GoogleAdsReporter.class);
    private final PluginTask task;
    private final UserCredentials credentials;
    private GoogleAdsClient client;

    public GoogleAdsReporter(PluginTask task) {
        this.task = task;
        this.credentials = buildCredential(task);
    }

    private UserCredentials buildCredential(PluginTask task) {
        return UserCredentials.newBuilder()
                .setClientId(task.getClientId())
                .setClientSecret(task.getClientSecret())
                .setRefreshToken(task.getRefreshToken())
                .build();
    }

    public List<Map<String, String>> getReport() {
        List<Map<String, String>> reports = new ArrayList<Map<String, String>>() {
        };
        String query = buildQuery(task);
        logger.info(query);
        SearchGoogleAdsRequest request = buildRequest(task, query);
        GoogleAdsServiceClient googleAdsService = client.getVersion5().createGoogleAdsServiceClient();
        GoogleAdsServiceClient.SearchPagedResponse response = googleAdsService.search(request);

        Map<String, String> result;
        for (GoogleAdsRow row : response.iterateAll()) {
            result = new HashMap<String, String>() {
            };
            flattenResource(null, row.getAllFields(), result);
            reports.add(result);
        }
        return reports;
    }

    public void flattenResource(String resourceName, Map<Descriptors.FieldDescriptor, Object> fields, Map<String, String> result) {
        for (Descriptors.FieldDescriptor key : fields.keySet()) {
            // skip resource_name
            if (key.getName().equals("resource_name")) {
                continue;
            }

            if (key.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
                if (key.isRepeated()) {
                    // TODO: support repeated field
                } else {
                    GeneratedMessageV3 message = (GeneratedMessageV3) fields.get(key);
                    String nestedResource;
                    if (resourceName == null) {
                        nestedResource = key.getName();
                    } else {
                        nestedResource = String.format("%s.%s", resourceName, key.getName());
                    }

                    flattenResource(nestedResource, message.getAllFields(), result);
                }
            }
            if (key.getName().equals("value")) {
                result.put(resourceName, String.valueOf(fields.get(key)));
            }else if (key.getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
                String attributeName = String.format("%s.%s", resourceName, key.getName());
                result.put(attributeName, String.valueOf(fields.get(key)));
            }
        }
    }

    public SearchGoogleAdsRequest buildRequest(PluginTask task, String query) {
        return SearchGoogleAdsRequest.newBuilder()
                .setCustomerId(task.getCustomerId())
                .setPageSize(PAGE_SIZE)
                .setQuery(query)
                .build();
    }

    public String buildQuery(PluginTask task) {
        StringBuilder sb = new StringBuilder();

        sb.append("SELECT ");
        String columns = task.getFields().getColumns().stream().map(ColumnConfig::getName).collect(Collectors.joining(", "));
        sb.append(columns);
        sb.append(" FROM ");
        sb.append(task.getResourceType());

        List<String> whereClause = buildWhereClauseConditions(task);
        if (!whereClause.isEmpty()) {
            sb.append(" WHERE ");
            sb.append(String.join(" AND ", whereClause));
        }

        return sb.toString();
    }

    @VisibleForTesting
    public List<String> buildWhereClauseConditions(PluginTask task) {
        List<String> whereConditions = new ArrayList<String>() {
        };

        if (task.getDateRange().isPresent()) {
            StringBuilder dateSb = new StringBuilder();
            dateSb.append("segments.date BETWEEN '");
            dateSb.append(task.getDateRange().get().getStartDate());
            dateSb.append("' AND '");
            dateSb.append(task.getDateRange().get().getEndDate());
            dateSb.append("'");
            whereConditions.add(dateSb.toString());
        }

        if (task.getConditions().isPresent()) {
            List<String> conditionList = task.getConditions().get();
            return Stream.concat(conditionList.stream(), whereConditions.stream()).collect(Collectors.toList());
        } else {
            return whereConditions;
        }
    }

    public void connect() {
        GoogleAdsClient.Builder builder = GoogleAdsClient.newBuilder()
                .setDeveloperToken(task.getDeveloperToken())
                .setCredentials(credentials);
        if (task.getLoginCustomerId().isPresent()) {
            builder.setLoginCustomerId(Long.parseLong(task.getLoginCustomerId().get()));
        }
        this.client = builder.build();
    }

}

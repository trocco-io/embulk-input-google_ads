package org.embulk.input.google_ads;

import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v5.services.GoogleAdsRow;
import com.google.ads.googleads.v5.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v5.services.SearchGoogleAdsRequest;
import com.google.auth.oauth2.UserCredentials;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
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
            result = new HashMap<String, String>() {};
            flattenResource(null, row.getAllFields(), result);
            reports.add(result);
        }
        return reports;
    }

    public void flattenResource(String resourceName, Map<Descriptors.FieldDescriptor, Object> fields, Map<String, String> result) {
        for (Descriptors.FieldDescriptor key : fields.keySet()) {
            String attributeName;
            if (resourceName == null){
                attributeName = key.getName();
            }else{
                attributeName = String.format("%s.%s", resourceName, key.getName());
            }

            if (isLeaf(attributeName)){
                result.put(attributeName, convertLeafNodeValue(fields, key));
            }else{
                GeneratedMessageV3 message = (GeneratedMessageV3) fields.get(key);
                flattenResource(attributeName, message.getAllFields(), result);
            }
        }
    }

    public String convertLeafNodeValue(Map<Descriptors.FieldDescriptor, Object> fields, Descriptors.FieldDescriptor key){
        if (key.getType() != Descriptors.FieldDescriptor.Type.MESSAGE) {
            return String.valueOf(fields.get(key));
        }

        if (key.isRepeated()) {
            List<String> values = new ArrayList<>();
            List<GeneratedMessageV3> field = (List<GeneratedMessageV3>) fields.get(key);
            for (GeneratedMessageV3 msg : field) {
                try{
                    values.add(JsonFormat.printer().print(msg));
                }catch (InvalidProtocolBufferException ignored){}
            }
            return "[" + String.join(",", values) + "]";
        }else{
            try{
                return JsonFormat.printer().print((GeneratedMessageV3) fields.get(key));
            }catch (InvalidProtocolBufferException ignored){}
        }
        return null;
    }

    public boolean isLeaf(String attributeName){
        for (ColumnConfig columnConfig:task.getFields().getColumns()){
            if (columnConfig.getName().equals(attributeName)){
                return true;
            }
        }
        return false;
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

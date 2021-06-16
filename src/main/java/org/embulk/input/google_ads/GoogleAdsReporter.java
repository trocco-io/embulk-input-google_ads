package org.embulk.input.google_ads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v6.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v6.services.SearchGoogleAdsRequest;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.base.CaseFormat;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.util.JsonFormat;
import org.embulk.spi.ColumnConfig;

import org.msgpack.core.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GoogleAdsReporter
{
    private static final int PAGE_SIZE = 1000;
    private final Logger logger = LoggerFactory.getLogger(GoogleAdsReporter.class);
    private final PluginTask task;
    private final UserCredentials credentials;
    private GoogleAdsClient client;
    private ObjectMapper mapper = new ObjectMapper();

    public GoogleAdsReporter(PluginTask task)
    {
        this.task = task;
        this.credentials = buildCredential(task);
    }

    private UserCredentials buildCredential(PluginTask task)
    {
        return UserCredentials.newBuilder()
                .setClientId(task.getClientId())
                .setClientSecret(task.getClientSecret())
                .setRefreshToken(task.getRefreshToken())
                .build();
    }

    public Iterable<GoogleAdsServiceClient.SearchPage> getReportPage()
    {
        String query = buildQuery(task);
        logger.info(query);
        SearchGoogleAdsRequest request = buildRequest(task, query);
        GoogleAdsServiceClient googleAdsService = client.getVersion6().createGoogleAdsServiceClient();
        GoogleAdsServiceClient.SearchPagedResponse response = googleAdsService.search(request);
        return response.iteratePages();
    }

    public void flattenResource(String resourceName, Map<Descriptors.FieldDescriptor, Object> fields, Map<String, String> result)
    {
        for (Descriptors.FieldDescriptor key : fields.keySet()) {
            String attributeName;
            if (resourceName == null) {
                attributeName = key.getName();
            }
            else {
                attributeName = String.format("%s.%s", resourceName, key.getName());
            }

            if (isLeaf(attributeName)) {
                result.put(attributeName, convertLeafNodeValue(fields, key));
            }
            else {
                if (!key.getName().equals("resource_name")) {
                    GeneratedMessageV3 message = (GeneratedMessageV3) fields.get(key);
                    flattenResource(attributeName, message.getAllFields(), result);
                }
            }
        }
    }

    public String convertLeafNodeValue(Map<Descriptors.FieldDescriptor, Object> fields, Descriptors.FieldDescriptor key)
    {
        if (key.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
            return convertMessageType(key, fields);
        }
        else if (key.getType() == Descriptors.FieldDescriptor.Type.ENUM) {
            return convertEnumType(key, fields);
        }
        return convertNonMessageType(key, fields);
    }

    public String convertEnumType(Descriptors.FieldDescriptor key, Map<Descriptors.FieldDescriptor, Object> fields)
    {
        if (key.isRepeated()) {
            List<Descriptors.GenericDescriptor> enumValues = (List<Descriptors.GenericDescriptor>) fields.get(key);
            ArrayNode arrayNode = mapper.createArrayNode();
            for (Descriptors.GenericDescriptor enumValue : enumValues) {
                arrayNode.add(enumValue.toString());
            }
            try {
                return mapper.writeValueAsString(arrayNode);
            }
            catch (JsonProcessingException ignored) {
                return null;
            }
        }
        else {
            return String.valueOf(fields.get(key));
        }
    }

    public String convertNonMessageType(Descriptors.FieldDescriptor key, Map<Descriptors.FieldDescriptor, Object> fields)
    {
        if (key.isRepeated()) {
            List<String> values = (List<String>) fields.get(key);
            ArrayNode arrayNode = mapper.createArrayNode();
            for (String val : values) {
                arrayNode.add(val);
            }
            try {
                return mapper.writeValueAsString(arrayNode);
            }
            catch (JsonProcessingException ignored) {
                return null;
            }
        }
        else {
            return String.valueOf(fields.get(key));
        }
    }

    public String convertMessageType(Descriptors.FieldDescriptor key, Map<Descriptors.FieldDescriptor, Object> fields)
    {
        if (key.isRepeated()) {
            ArrayNode result = mapper.createArrayNode();
            List<GeneratedMessageV3> field = (List<GeneratedMessageV3>) fields.get(key);
            try {
                for (GeneratedMessageV3 msg : field) {
                    JsonNode jsonNode = mapper.readTree(JsonFormat.printer().print(msg));
                    JsonNode jsonNodeWithSnakeCase = traverse(jsonNode);
                    result.add(jsonNodeWithSnakeCase);
                }
                return mapper.writeValueAsString(result);
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
                return null;
            }
        }
        else {
            try {
                String jsonStr = JsonFormat.printer().print((GeneratedMessageV3) fields.get(key));
                JsonNode jsonNode = mapper.readTree(jsonStr);
                JsonNode jsonNodeWithSnakeCase = traverse(jsonNode);
                return mapper.writeValueAsString(jsonNodeWithSnakeCase);
            }
            catch (Exception e) {
                System.out.println(e.getMessage());
                return null;
            }
        }
    }

    public JsonNode traverse(JsonNode node)
    {
        if (node.isValueNode()) {
            if (JsonNodeType.NULL == node.getNodeType()) {
                return null;
            }
            else {
                return node;
            }
        }
        else if (node.isNull()) {
            return null;
        }
        else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            ArrayNode cleanedNewArrayNode = mapper.createArrayNode();
            for (JsonNode jsonNode : arrayNode) {
                cleanedNewArrayNode.add(traverse(jsonNode));
            }
            return cleanedNewArrayNode;
        }
        else {
            ObjectNode encodedObjectNode = mapper.createObjectNode();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> entry = it.next();
                encodedObjectNode.set(CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entry.getKey()),
                        traverse(entry.getValue()));
            }
            return encodedObjectNode;
        }
    }

    public boolean isLeaf(String attributeName)
    {
        for (ColumnConfig columnConfig : task.getFields().getColumns()) {
            if (columnConfig.getName().equals(attributeName)) {
                return true;
            }
        }
        return false;
    }

    public SearchGoogleAdsRequest buildRequest(PluginTask task, String query)
    {
        return SearchGoogleAdsRequest.newBuilder()
                .setCustomerId(task.getCustomerId())
                .setPageSize(PAGE_SIZE)
                .setQuery(query)
                .build();
    }

    public String buildQuery(PluginTask task)
    {
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
    public List<String> buildWhereClauseConditions(PluginTask task)
    {
        List<String> whereConditions = new ArrayList<String>()
        {
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
        }
        else {
            return whereConditions;
        }
    }

    public void connect()
    {
        GoogleAdsClient.Builder builder = GoogleAdsClient.newBuilder()
                .setDeveloperToken(task.getDeveloperToken())
                .setCredentials(credentials);
        if (task.getLoginCustomerId().isPresent()) {
            builder.setLoginCustomerId(Long.parseLong(task.getLoginCustomerId().get()));
        }
        this.client = builder.build();
    }
}

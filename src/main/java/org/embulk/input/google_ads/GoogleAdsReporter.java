package org.embulk.input.google_ads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.ads.googleads.lib.GoogleAdsClient;
import com.google.ads.googleads.v16.resources.CustomerName;
import com.google.ads.googleads.v16.services.CustomerServiceClient;
import com.google.ads.googleads.v16.services.GoogleAdsRow;
import com.google.ads.googleads.v16.services.GoogleAdsServiceClient;
import com.google.ads.googleads.v16.services.ListAccessibleCustomersRequest;
import com.google.ads.googleads.v16.services.SearchGoogleAdsRequest;
import com.google.ads.googleads.v16.services.SearchGoogleAdsStreamRequest;
import com.google.ads.googleads.v16.services.SearchGoogleAdsStreamResponse;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.base.CaseFormat;
import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.util.JsonFormat;

import org.embulk.util.config.units.ColumnConfig;
import org.msgpack.core.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GoogleAdsReporter
{
    private final Logger logger = LoggerFactory.getLogger(GoogleAdsReporter.class);
    private final PluginTask task;
    private final UserCredentials credentials;
    private final ObjectMapper mapper = new ObjectMapper();
    private GoogleAdsClient client;

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

    private Iterable<GoogleAdsServiceClient.SearchPage> search(Map<String, String> params) {
        String query = buildQuery(task, params);
        logger.info(query);
        SearchGoogleAdsRequest request = buildRequest(task, query);
        GoogleAdsServiceClient googleAdsService = client.getLatestVersion().createGoogleAdsServiceClient();
        GoogleAdsServiceClient.SearchPagedResponse response = googleAdsService.search(request);
        return response.iteratePages();
    }

    public void search(Consumer<GoogleAdsServiceClient.SearchPage> consumer, Map<String, String> params) {
        GoogleAdsServiceClient.SearchPage lastPage = null;
        for(GoogleAdsServiceClient.SearchPage page: search(params)) {
            consumer.accept(page);
            lastPage = page;
        }

        if (task.getResourceType().equals("change_event")) {
            if (lastPage == null) return ;
            GoogleAdsRow lastRow = null;
            for (GoogleAdsRow row: lastPage.getValues()) {
                lastRow = row;
            }
            if (lastRow == null) return ;

            Map<String, String> nextParams = new HashMap<>();
            nextParams.put("start_datetime", lastRow.getChangeEvent().getChangeDateTime());
            search(consumer, nextParams);
        }
    }

    public void flattenResource(String resourceName, Map<Descriptors.FieldDescriptor, Object> fields, Map<String, String> result)
    {
        for (Descriptors.FieldDescriptor key : fields.keySet()) {
            String attributeName;
            if (resourceName == null) {
                attributeName = key.getName();
            } else {
                attributeName = String.format("%s.%s", resourceName, key.getName());
            }

            if (isLeaf(attributeName)) {
                result.put(attributeName, convertLeafNodeValue(fields, key));
            } else if (fields.get(key) instanceof GeneratedMessageV3) {
                GeneratedMessageV3 message = (GeneratedMessageV3) fields.get(key);
                flattenResource(attributeName, message.getAllFields(), result);
            }
        }
    }

    public String convertLeafNodeValue(Map<Descriptors.FieldDescriptor, Object> fields, Descriptors.FieldDescriptor key)
    {
        if (key.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
            return convertMessageType(key, fields);
        } else if (key.getType() == Descriptors.FieldDescriptor.Type.ENUM) {
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
            } catch (JsonProcessingException ignored) {
                return null;
            }
        } else {
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
            } catch (JsonProcessingException ignored) {
                return null;
            }
        } else {
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
            } catch (Exception e) {
                System.out.println(e.getMessage());
                return null;
            }
        } else {
            try {
                String jsonStr = JsonFormat.printer().print((GeneratedMessageV3) fields.get(key));
                JsonNode jsonNode = mapper.readTree(jsonStr);
                JsonNode jsonNodeWithSnakeCase = traverse(jsonNode);
                return mapper.writeValueAsString(jsonNodeWithSnakeCase);
            } catch (Exception e) {
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
            } else {
                return node;
            }
        } else if (node.isNull()) {
            return null;
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            ArrayNode cleanedNewArrayNode = mapper.createArrayNode();
            for (JsonNode jsonNode : arrayNode) {
                cleanedNewArrayNode.add(traverse(jsonNode));
            }
            return cleanedNewArrayNode;
        } else {
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
                .setQuery(query)
                .build();
    }

    public String buildQuery(PluginTask task, Map<String, String> params)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("SELECT ");
        String columns = task.getFields().getColumns().stream().map(ColumnConfig::getName).collect(Collectors.joining(", "));
        sb.append(columns);
        sb.append(" FROM ");
        sb.append(task.getResourceType());

        List<String> whereClause = buildWhereClauseConditions(task, params);
        if (!whereClause.isEmpty()) {
            sb.append(" WHERE ");
            sb.append(String.join(" AND ", whereClause));
        }

        if (task.getLimit().isPresent()) {
            sb.append(" LIMIT ");
            sb.append(task.getLimit().get());
        }

        return sb.toString();
    }

    @VisibleForTesting
    public List<String> buildWhereClauseConditions(PluginTask task, Map<String, String> params)
    {
        List<String> whereConditions = new ArrayList<String>()
        {
        };

        if (task.getDateRange().isPresent()) {
            StringBuilder dateSb = new StringBuilder();
            if (task.getResourceType().equals("change_event")) {
                dateSb.append(buildWhereClauseConditionsForChangeEvent(params.get("start_datetime")));
            } else {
                dateSb.append("segments.date BETWEEN '");
                dateSb.append(task.getDateRange().get().getStartDate());
                dateSb.append("' AND '");
                dateSb.append(task.getDateRange().get().getEndDate());
                dateSb.append("'");
            }
            whereConditions.add(dateSb.toString());
        }

        if (task.getConditions().isPresent()) {
            List<String> conditionList = task.getConditions().get();
            return Stream.concat(conditionList.stream(), whereConditions.stream()).collect(Collectors.toList());
        } else {
            return whereConditions;
        }
    }

    public void connect()
    {
        this.client = buildClient(task.getLoginCustomerId().orElseGet(() -> getLoginCustomerId(task.getCustomerId())));
    }

    private Long getLoginCustomerId(String customerId)
    {
        List<Long> loginCustomerIds = getLoginCustomerIds(customerId);
        if (loginCustomerIds.isEmpty()) {
            throw new RuntimeException("login customer not found [customer id: " + customerId + "]");
        }
        if (loginCustomerIds.size() > 1) {
            logger.info("multiple login customers found [login customer ids: {}]", loginCustomerIds.stream().map(Object::toString).collect(Collectors.joining(", ")));
        }
        Long loginCustomerId = loginCustomerIds.get(0);
        logger.info("use this customer [customer id: {}, login customer id: {}] to login", customerId, loginCustomerId);
        return loginCustomerId;
    }

    private List<Long> getLoginCustomerIds(String customerId)
    {
        try (CustomerServiceClient client = buildClient(null).getLatestVersion().createCustomerServiceClient()) {
            return client.listAccessibleCustomers(ListAccessibleCustomersRequest.newBuilder().build())
                    .getResourceNamesList()
                    .stream()
                    .map(CustomerName::parse)
                    .map(CustomerName::getCustomerId)
                    .map(this::getLoginCustomerClients)
                    .flatMap(Collection::stream)
                    .filter(loginCustomerClient -> loginCustomerClient.customerClientId.equals(customerId))
                    .map(loginCustomerClient -> Long.valueOf(loginCustomerClient.loginCustomerId))
                    .collect(Collectors.toList());
        }
    }

    private List<LoginCustomerClient> getLoginCustomerClients(String customerId)
    {
        try (GoogleAdsServiceClient client = buildClient(Long.valueOf(customerId)).getLatestVersion().createGoogleAdsServiceClient()) {
            return client.searchStreamCallable().call(SearchGoogleAdsStreamRequest.newBuilder()
                            .setCustomerId(customerId)
                            .setQuery("SELECT customer_client.id FROM customer_client")
                            .build())
                    .stream()
                    .map(SearchGoogleAdsStreamResponse::getResultsList)
                    .flatMap(Collection::stream)
                    .map(GoogleAdsRow::getCustomerClient)
                    .map(customerClient -> new LoginCustomerClient(customerId, customerClient.getId()))
                    .collect(Collectors.toList());
        }
    }

    private static class LoginCustomerClient
    {
        LoginCustomerClient(String loginCustomerId, Long customerClientId)
        {
            this.loginCustomerId = loginCustomerId;
            this.customerClientId = String.valueOf(customerClientId);
        }

        final String loginCustomerId;
        final String customerClientId;
    }

    private GoogleAdsClient buildClient(Long loginCustomerId)
    {
        GoogleAdsClient.Builder builder = GoogleAdsClient.newBuilder()
                .setDeveloperToken(task.getDeveloperToken())
                .setCredentials(credentials);
        if (loginCustomerId != null) {
            builder.setLoginCustomerId(loginCustomerId);
        }
        return builder.build();
    }

    private String buildWhereClauseConditionsForChangeEvent(String startDateTime)
    {
        StringBuilder dateSb = new StringBuilder();
        dateSb.append("change_event.change_date_time ");
        if (startDateTime == null) {
            dateSb.append(" >= '");
            dateSb.append(task.getDateRange().get().getStartDate());
        } else {
            dateSb.append(" > '");
            dateSb.append(startDateTime);
        }
        dateSb.append("' AND ");
        dateSb.append("change_event.change_date_time ");
        dateSb.append(" <= '");
        dateSb.append(task.getDateRange().get().getEndDate());
        dateSb.append("'");
        dateSb.append(" ORDER BY change_event.change_date_time ASC");

        return dateSb.toString();
    }
}

package com.dfixtester.cucumber;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.awaitility.Awaitility;
import org.springframework.boot.system.ApplicationHome;

import com.dfixtester.engine.ScenarioContext;
import com.dfixtester.engine.FixApplication;

import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.StringField;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;
import quickfix.field.Price;
import quickfix.field.OrderQty;

import java.time.Duration;
import java.io.InputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class FixStepDefinitions {

    private ScenarioContext scenarioContext;
    
    private SessionID sessionID;
    private String lastClOrdId;
    
    private static final DateTimeFormatter FIX_UTC_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneId.of("UTC"));

    public FixStepDefinitions() {
        this.scenarioContext = FixApplication.getActiveScenarioContext();
    }

    private static final Map<String, Map<String, Integer>> dictionaries = new HashMap<>();

    private void reportMessage(String direction, String session, Message msg) {
        reportMessage(direction, session, msg, null);
    }

    private void reportMessage(String direction, String session, Message msg, java.util.Set<Integer> validatedTags) {
        String msgStr = msg.toString().replace("\u0001", "|");
        String tagsJson = "[]";
        if (validatedTags != null && !validatedTags.isEmpty()) {
            tagsJson = "[" + validatedTags.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        String json = "@@TEST_MSG@@{\"direction\":\"" + direction + "\",\"session\":\"" + session + "\",\"message\":\"" + msgStr + "\",\"validatedTags\":" + tagsJson + "}";
        System.out.println(json);
    }

    private Map<String, Integer> getDictionaryForVersion(String version) {
        if (dictionaries.containsKey(version)) {
            return dictionaries.get(version);
        }
        
        Map<String, Integer> tagNameToId = new HashMap<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> idToName = null;
            
            ApplicationHome home = new ApplicationHome(FixStepDefinitions.class);
            File baseDir = home.getDir();

            String baseName = version.isEmpty() ? "fix-tag-dictionary.json" : "fix-tag-dictionary-" + version + ".json";
            
            File extFile = new File(baseDir, "config/" + baseName);
            if (!extFile.exists()) {
                extFile = new File(baseDir, baseName);
            }
            
            if (!extFile.exists() && !version.isEmpty()) {
                extFile = new File(baseDir, "config/fix-tag-dictionary.json");
                if (!extFile.exists()) extFile = new File(baseDir, "fix-tag-dictionary.json");
            }
            
            if (extFile.exists()) {
                idToName = mapper.readValue(extFile, new TypeReference<Map<String, String>>() {});
            } else {
                Resource resource = new ClassPathResource("config/" + baseName);
                if (!resource.exists()) resource = new ClassPathResource(baseName);
                
                if (!resource.exists() && !version.isEmpty()) {
                    resource = new ClassPathResource("config/fix-tag-dictionary.json");
                    if (!resource.exists()) resource = new ClassPathResource("fix-tag-dictionary.json");
                }
                if (resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        idToName = mapper.readValue(is, new TypeReference<Map<String, String>>() {});
                    }
                }
            }
            
            if (idToName != null) {
                for (Map.Entry<String, String> entry : idToName.entrySet()) {
                    tagNameToId.put(entry.getValue(), Integer.parseInt(entry.getKey()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        dictionaries.put(version, tagNameToId);
        return tagNameToId;
    }

    private Map<String, String> processDynamicFields(Map<String, String> fields) {
        Map<String, String> processed = new HashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.startsWith("<NOW")) {
                Instant time = Instant.now();
                if (value.contains("+") || value.contains("-")) {
                    int sign = value.contains("+") ? 1 : -1;
                    String[] parts = value.split("[\\+\\-]");
                    if (parts.length == 2) {
                        String amountStr = parts[1].replace(">", "").trim();
                        try {
                            int amount = Integer.parseInt(amountStr.substring(0, amountStr.length() - 1));
                            if (amountStr.endsWith("m")) time = time.plus(sign * amount, ChronoUnit.MINUTES);
                            else if (amountStr.endsWith("h")) time = time.plus(sign * amount, ChronoUnit.HOURS);
                            else if (amountStr.endsWith("s")) time = time.plus(sign * amount, ChronoUnit.SECONDS);
                            else if (amountStr.endsWith("d")) time = time.plus(sign * amount, ChronoUnit.DAYS);
                        } catch (Exception e) { /* fallback on parsing error */ }
                    }
                }
                processed.put(entry.getKey(), FIX_UTC_TIMESTAMP_FORMATTER.format(time));
            } else {
                processed.put(entry.getKey(), value);
            }
        }
        return processed;
    }

    @Before
    public void setup() {
        scenarioContext.clear();
    }

    @Given("I map session alias {string} to {string}")
    public void i_map_session_alias_to(String alias, String sessionString) {
        scenarioContext.addSessionAlias(alias, sessionString);
    }

    @Given("the session {string} is logged on")
    public void the_session_is_logged_on(String sessionString) {
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        sessionID = new SessionID(resolvedSession);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> 
            Session.lookupSession(sessionID) != null && Session.lookupSession(sessionID).isLoggedOn()
        );
    }

    @Given("I define parameter template {string} with fields:")
    public void i_define_parameter_template(String templateName, DataTable dataTable) {
        scenarioContext.addParameterTemplate(templateName, dataTable.asMap());
    }

    @When("I send a NewOrderSingle with alias {string} and fields:")
    public void i_send_a_newordersingle(String alias, DataTable dataTable) throws Exception {
        Map<String, String> fields = dataTable.asMap();
        lastClOrdId = UUID.randomUUID().toString();
        
        scenarioContext.registerNewOrder(alias, lastClOrdId);

        NewOrderSingle order = new NewOrderSingle(
                new ClOrdID(lastClOrdId),
                new Side(fields.get("Side").charAt(0)),
                new TransactTime(),
                new OrdType(fields.get("OrdType").charAt(0))
        );
        order.set(new Symbol(fields.get("Symbol")));
        
        String version = sessionID != null ? sessionID.getBeginString() : "";
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (key.equals("Side") || key.equals("OrdType") || key.equals("Symbol")) {
                continue;
            }
            int tagId = getTagId(key, version);
            if (tagId != -1) {
                order.setField(new StringField(tagId, entry.getValue()));
            } else if (key.equals("CustomBrokerTag")) {
                order.setField(new StringField(5000, entry.getValue()));
            }
        }

        Session.sendToTarget(order, sessionID);
        reportMessage("OUT", sessionID.toString(), order);
    }

    @When("I send a NewOrderSingle with alias {string} to session {string} with fields:")
    public void i_send_a_newordersingle_to_session(String alias, String sessionString, DataTable dataTable) throws Exception {
        sendOrderSingleHelper(alias, sessionString, dataTable.asMap());
    }

    @When("I send a NewOrderSingle with alias {string} to session {string} using templates {string}")
    public void i_send_a_newordersingle_to_session_using_templates_only(String alias, String sessionString, String templatesStr) throws Exception {
        Map<String, String> mergedFields = new HashMap<>();
        for (String templateName : templatesStr.split(",")) {
            mergedFields.putAll(scenarioContext.getParameterTemplate(templateName.trim()));
        }
        sendOrderSingleHelper(alias, sessionString, mergedFields);
    }

    @When("I send a NewOrderSingle with alias {string} to session {string} using templates {string} with fields:")
    public void i_send_a_newordersingle_to_session_using_templates(String alias, String sessionString, String templatesStr, DataTable dataTable) throws Exception {
        Map<String, String> mergedFields = new HashMap<>();
        for (String templateName : templatesStr.split(",")) {
            mergedFields.putAll(scenarioContext.getParameterTemplate(templateName.trim()));
        }
        if (dataTable != null) {
            // Any explicit fields passed in the step will override the template defaults
            mergedFields.putAll(dataTable.asMap());
        }
        sendOrderSingleHelper(alias, sessionString, mergedFields);
    }

    private void sendOrderSingleHelper(String alias, String sessionString, Map<String, String> fields) throws Exception {
        fields = processDynamicFields(fields);
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        lastClOrdId = UUID.randomUUID().toString();
        
        scenarioContext.registerNewOrder(alias, lastClOrdId);

        SessionID targetSession = new SessionID(resolvedSession);
        String version = targetSession.getBeginString();

        NewOrderSingle order = new NewOrderSingle(
                new ClOrdID(lastClOrdId),
                new Side(fields.getOrDefault("Side", "1").charAt(0)),
                new TransactTime(),
                new OrdType(fields.getOrDefault("OrdType", "2").charAt(0))
        );
        
        if (fields.containsKey("Symbol")) order.set(new Symbol(fields.get("Symbol")));
        if (fields.containsKey("Price")) order.set(new Price(Double.parseDouble(fields.get("Price"))));
        if (fields.containsKey("OrderQty")) order.set(new OrderQty(Double.parseDouble(fields.get("OrderQty"))));

        // Automatically map any unknown tags (by name or number) supplied in the feature file
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (key.equals("Side") || key.equals("OrdType") || key.equals("Symbol") || key.equals("Price") || key.equals("OrderQty")) {
                continue; // Already handled by explicit setters
            }
            int tagId = getTagId(key, version);
            if (tagId != -1) {
                order.setField(new StringField(tagId, entry.getValue()));
            }
        }

        Session.sendToTarget(order, targetSession);
        reportMessage("OUT", resolvedSession, order);
    }

    private void sendGenericMessageHelper(String msgType, String alias, String origAlias, String sessionString, Map<String, String> fields) throws Exception {
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        SessionID targetSession = new SessionID(resolvedSession);
        String version = targetSession.getBeginString();

        Message msg = new Message();
        msg.getHeader().setString(35, msgType);

        if (alias != null && !alias.isEmpty()) {
            lastClOrdId = UUID.randomUUID().toString();
            scenarioContext.registerNewOrder(alias, lastClOrdId);
            msg.setString(11, lastClOrdId);
        }
        if (origAlias != null && !origAlias.isEmpty()) {
            String origClOrdId = scenarioContext.getClOrdIdByAlias(origAlias);
            if (origClOrdId != null) msg.setString(41, origClOrdId);
        }

        fields = processDynamicFields(fields);

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            int tagId = getTagId(entry.getKey(), version);
            if (tagId != -1) msg.setString(tagId, entry.getValue());
        }

        Session.sendToTarget(msg, targetSession);
        reportMessage("OUT", resolvedSession, msg);
    }

    @When("I send an OrderCancelRequest with alias {string} for original order {string} to session {string} with fields:")
    public void i_send_an_order_cancel_request(String alias, String origAlias, String sessionString, DataTable dataTable) throws Exception {
        sendGenericMessageHelper("F", alias, origAlias, sessionString, dataTable.asMap());
    }

    @When("I send an OrderCancelReplaceRequest with alias {string} for original order {string} to session {string} with fields:")
    public void i_send_an_order_cancel_replace_request(String alias, String origAlias, String sessionString, DataTable dataTable) throws Exception {
        sendGenericMessageHelper("G", alias, origAlias, sessionString, dataTable.asMap());
    }

    @When("I send a raw FIX message to session {string}: {string}")
    public void i_send_a_raw_fix_message(String sessionString, String rawMessage) throws Exception {
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        SessionID targetSession = new SessionID(resolvedSession);
        Message msg = new Message(rawMessage.replace("|", "\u0001"));
        Session.sendToTarget(msg, targetSession);
        reportMessage("OUT", resolvedSession, msg);
    }

    @Then("I expect an ExecutionReport for alias {string} within {int} seconds")
    public void i_expect_an_execution_report(String alias, int timeoutSeconds) {
        String expectedClOrdId = scenarioContext.getClOrdIdByAlias(alias);

        Awaitility.await()
            .atMost(Duration.ofSeconds(timeoutSeconds))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> {
                for (ScenarioContext.MessageEvent event : scenarioContext.getMessageQueue()) {
                    Message msg = event.message;
                    if (msg.getHeader().getString(35).equals("8")) {
                        if (msg.getString(ClOrdID.FIELD).endsWith(expectedClOrdId)) {
                            java.util.Set<Integer> validated = new java.util.HashSet<>(java.util.Arrays.asList(35, 11));
                            reportMessage("IN", event.sessionID.toString(), msg, validated);
                            return true;
                        }
                    }
                }
                return false;
            });
    }

    @Then("I expect a message with MsgType {string} on session {string} for alias {string} within {int} seconds with fields:")
    public void i_expect_a_message_on_session_with_fields(String msgType, String sessionString, String alias, int timeoutSeconds, DataTable dataTable) {
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        String expectedClOrdId = scenarioContext.getClOrdIdByAlias(alias);
        Map<String, String> expectedFields = dataTable.asMap();
        SessionID expectedSession = new SessionID(resolvedSession);
        String version = expectedSession.getBeginString();

        java.util.Set<String> debuggedMessages = new java.util.HashSet<>();
        java.util.List<String> rejectionReasons = new java.util.ArrayList<>();

        try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(timeoutSeconds))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    for (ScenarioContext.MessageEvent event : scenarioContext.getMessageQueue()) {
                        Message msg = event.message;
                        try {
                            if (!msg.getHeader().getString(35).equals(msgType)) continue;

                            // Ensure this message arrived ON the correct session
                            if (!event.sessionID.toString().equals(resolvedSession)) {
                                continue;
                            }
                            
                            String rawMsg = msg.toString();
                            boolean isNewMessage = debuggedMessages.add(rawMsg);
                            if (isNewMessage) {
                                System.out.println("\n[DEBUG] Found candidate message on " + resolvedSession + " with MsgType " + msgType);
                            }

                            // Verify it is tied to our specific order alias via Tag 11 (ClOrdID) or 41 (OrigClOrdID)
                            String msgClOrdId = msg.isSetField(11) ? msg.getString(11) : (msg.isSetField(41) ? msg.getString(41) : "");
                            
                            if (!msgClOrdId.endsWith(expectedClOrdId)) {
                                if (isNewMessage) {
                                    String reason = "ClOrdID mismatch. Expected to end with: '" + expectedClOrdId + "', Actual: '" + msgClOrdId + "'";
                                    System.out.println("   -> REJECTED: " + reason);
                                    rejectionReasons.add(reason);
                                }
                                continue;
                            }

                            boolean allFieldsMatch = true;
                            for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
                                int tag = getTagId(entry.getKey(), version);
                                if (tag != -1) {
                                    String expectedValue = entry.getValue();
                                    if ("<ABSENT>".equals(expectedValue)) {
                                        if (msg.isSetField(tag)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") is PRESENT but expected to be ABSENT.";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        }
                                    } else {
                                        if (!msg.isSetField(tag)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") is MISSING.";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        } else if (!msg.getString(tag).equals(expectedValue)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") mismatch. Expected: '" + expectedValue + "', Actual: '" + msg.getString(tag) + "'";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (allFieldsMatch) {
                                if (isNewMessage) System.out.println("   -> MATCHED! Message successfully validated.");
                                java.util.Set<Integer> validated = new java.util.HashSet<>(java.util.Arrays.asList(35));
                                if (msg.isSetField(11)) validated.add(11);
                                if (msg.isSetField(41)) validated.add(41);
                                for (String key : expectedFields.keySet()) {
                                    int tag = getTagId(key, version);
                                    if (tag != -1) validated.add(tag);
                                }
                                reportMessage("IN", resolvedSession, msg, validated);
                                return true;
                            }
                        } catch (quickfix.FieldNotFound fnf) {
                            // Ignore field not found during validation loop
                        } catch (Exception e) {
                            System.err.println("Unexpected error during message validation: " + e.getMessage());
                        }
                    }
                    return false;
                });
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            StringBuilder errorMsg = new StringBuilder("Timeout after " + timeoutSeconds + "s. Expected message for alias '" + alias + "' (ClOrdID ending with: " + expectedClOrdId + ") not found on session " + resolvedSession + ".\n");
            if (!rejectionReasons.isEmpty()) {
                errorMsg.append("Candidate messages were rejected due to:\n - ").append(String.join("\n - ", rejectionReasons)).append("\n");
            }
            errorMsg.append("Total messages in queue: ").append(scenarioContext.getMessageQueue().size()).append(".\n")
                    .append("Queue contents: ").append(scenarioContext.getMessageQueue().toString().replace("\u0001", "|"));
            throw new AssertionError(errorMsg.toString(), e);
        }
    }

    @Then("I expect a routed message with MsgType {string} on session {string} and assign alias {string} within {int} seconds with fields:")
    public void i_expect_a_routed_message_on_session_and_assign_alias(String msgType, String sessionString, String alias, int timeoutSeconds, DataTable dataTable) {
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        Map<String, String> expectedFields = dataTable.asMap();
        SessionID expectedSession = new SessionID(resolvedSession);
        String version = expectedSession.getBeginString();
        
        java.util.Set<String> debuggedMessages = new java.util.HashSet<>();
        java.util.List<String> rejectionReasons = new java.util.ArrayList<>();

        try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(timeoutSeconds))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    for (ScenarioContext.MessageEvent event : scenarioContext.getMessageQueue()) {
                        Message msg = event.message;
                        try {
                            if (!msg.getHeader().getString(35).equals(msgType)) continue;

                            if (!event.sessionID.toString().equals(resolvedSession)) {
                                continue;
                            }
                            
                            String rawMsg = msg.toString();
                            boolean isNewMessage = debuggedMessages.add(rawMsg);
                            if (isNewMessage) {
                                System.out.println("\n[DEBUG] Found candidate routed message on " + resolvedSession + " with MsgType " + msgType);
                            }

                            boolean allFieldsMatch = true;
                            for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
                                int tag = getTagId(entry.getKey(), version);
                                if (tag != -1) {
                                    String expectedValue = entry.getValue();
                                    if ("<ABSENT>".equals(expectedValue)) {
                                        if (msg.isSetField(tag)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") is PRESENT but expected to be ABSENT.";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        }
                                    } else {
                                        if (!msg.isSetField(tag)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") is MISSING.";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        } else if (!msg.getString(tag).equals(expectedValue)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") mismatch. Expected: '" + expectedValue + "', Actual: '" + msg.getString(tag) + "'";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (allFieldsMatch) {
                                if (isNewMessage) System.out.println("   -> MATCHED! Routed message successfully validated.");
                                if (msg.isSetField(11)) {
                                    scenarioContext.registerNewOrder(alias, msg.getString(11));
                                    System.out.println("   -> Assigned downstream ClOrdID '" + msg.getString(11) + "' to alias '" + alias + "'");
                                }
                                java.util.Set<Integer> validated = new java.util.HashSet<>(java.util.Arrays.asList(35));
                                if (msg.isSetField(11)) validated.add(11);
                                for (String key : expectedFields.keySet()) {
                                    int tag = getTagId(key, version);
                                    if (tag != -1) validated.add(tag);
                                }
                                reportMessage("IN", resolvedSession, msg, validated);
                                return true;
                            }
                        } catch (quickfix.FieldNotFound fnf) {
                            // Ignore
                        } catch (Exception e) {
                            System.err.println("Unexpected error during message validation: " + e.getMessage());
                        }
                    }
                    return false;
                });
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            StringBuilder errorMsg = new StringBuilder("Timeout after " + timeoutSeconds + "s. Expected routed message not found on session " + resolvedSession + ".\n");
            if (!rejectionReasons.isEmpty()) {
                errorMsg.append("Candidate messages were rejected due to:\n - ").append(String.join("\n - ", rejectionReasons)).append("\n");
            }
            errorMsg.append("Total messages in queue: ").append(scenarioContext.getMessageQueue().size()).append(".\n")
                    .append("Queue contents: ").append(scenarioContext.getMessageQueue().toString().replace("\u0001", "|"));
            throw new AssertionError(errorMsg.toString(), e);
        }
    }

    @Then("I expect a BusinessMessageReject on session {string} within {int} seconds with fields:")
    public void i_expect_a_business_message_reject(String sessionString, int timeoutSeconds, DataTable dataTable) {
        i_expect_an_admin_message_on_session_with_fields("j", sessionString, timeoutSeconds, dataTable);
    }

    @Then("I expect an admin message with MsgType {string} on session {string} within {int} seconds with fields:")
    public void i_expect_an_admin_message_on_session_with_fields(String msgType, String sessionString, int timeoutSeconds, DataTable dataTable) {
        final String resolvedSession = scenarioContext.resolveSessionAlias(sessionString);
        Map<String, String> expectedFields = dataTable.asMap();
        SessionID expectedSession = new SessionID(resolvedSession);
        String version = expectedSession.getBeginString();
        
        java.util.Set<String> debuggedMessages = new java.util.HashSet<>();
        java.util.List<String> rejectionReasons = new java.util.ArrayList<>();

        try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(timeoutSeconds))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    for (ScenarioContext.MessageEvent event : scenarioContext.getMessageQueue()) {
                        Message msg = event.message;
                        try {
                            if (!msg.getHeader().getString(35).equals(msgType)) continue;

                            if (!event.sessionID.toString().equals(resolvedSession)) {
                                continue;
                            }
                            
                            String rawMsg = msg.toString();
                            boolean isNewMessage = debuggedMessages.add(rawMsg);
                            if (isNewMessage) {
                                System.out.println("\n[DEBUG] Found candidate admin message on " + resolvedSession + " with MsgType " + msgType);
                            }

                            boolean allFieldsMatch = true;
                            for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
                                int tag = getTagId(entry.getKey(), version);
                                if (tag != -1) {
                                    String expectedValue = entry.getValue();
                                    if ("<ABSENT>".equals(expectedValue)) {
                                        if (msg.isSetField(tag)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") is PRESENT but expected to be ABSENT.";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        }
                                    } else {
                                        if (!msg.isSetField(tag)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") is MISSING.";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        } else if (!msg.getString(tag).equals(expectedValue)) {
                                            if (isNewMessage) {
                                                String reason = "Tag " + tag + " (" + entry.getKey() + ") mismatch. Expected: '" + expectedValue + "', Actual: '" + msg.getString(tag) + "'";
                                                System.out.println("   -> REJECTED: " + reason);
                                                rejectionReasons.add(reason);
                                            }
                                            allFieldsMatch = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (allFieldsMatch) {
                                if (isNewMessage) System.out.println("   -> MATCHED! Admin message successfully validated.");
                                
                                java.util.Set<Integer> validated = new java.util.HashSet<>(java.util.Arrays.asList(35));
                                for (String key : expectedFields.keySet()) {
                                    int tag = getTagId(key, version);
                                    if (tag != -1) validated.add(tag);
                                }
                                reportMessage("IN", resolvedSession, msg, validated);
                                return true;
                            }
                        } catch (quickfix.FieldNotFound fnf) {
                            // Ignore
                        } catch (Exception e) {
                            System.err.println("Unexpected error during message validation: " + e.getMessage());
                        }
                    }
                    return false;
                });
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            StringBuilder errorMsg = new StringBuilder("Timeout after " + timeoutSeconds + "s. Expected admin message not found on session " + resolvedSession + ".\n");
            if (!rejectionReasons.isEmpty()) {
                errorMsg.append("Candidate messages were rejected due to:\n - ").append(String.join("\n - ", rejectionReasons)).append("\n");
            }
            errorMsg.append("Total messages in queue: ").append(scenarioContext.getMessageQueue().size()).append(".\n")
                    .append("Queue contents: ").append(scenarioContext.getMessageQueue().toString().replace("\u0001", "|"));
            throw new AssertionError(errorMsg.toString(), e);
        }
    }

    private int getTagId(String fieldName, String version) {
        Map<String, Integer> dict = getDictionaryForVersion(version);
        if (dict.containsKey(fieldName)) {
            return dict.get(fieldName);
        }
        switch (fieldName) {
            case "Side": return 54;
            case "Symbol": return 55;
            case "OrdType": return 40;
            case "Price": return 44;
            case "OrderQty": return 38;
            default: 
                try { return Integer.parseInt(fieldName); } 
                catch (NumberFormatException e) { return -1; }
        }
    }
}

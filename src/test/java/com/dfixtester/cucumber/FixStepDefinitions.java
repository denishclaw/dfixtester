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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class FixStepDefinitions {

    private ScenarioContext scenarioContext;
    
    private SessionID sessionID;
    private String lastClOrdId;

    public FixStepDefinitions() {
        this.scenarioContext = FixApplication.getActiveScenarioContext();
    }

    private static final Map<String, Map<String, Integer>> dictionaries = new HashMap<>();

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

    @Before
    public void setup() {
        scenarioContext.clear();
    }

    @Given("the session {string} is logged on")
    public void the_session_is_logged_on(String sessionString) {
        sessionID = new SessionID(sessionString);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> 
            Session.lookupSession(sessionID) != null && Session.lookupSession(sessionID).isLoggedOn()
        );
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
    }

    @When("I send a NewOrderSingle with alias {string} to session {string} with fields:")
    public void i_send_a_newordersingle_to_session(String alias, String sessionString, DataTable dataTable) throws Exception {
        Map<String, String> fields = dataTable.asMap();
        lastClOrdId = UUID.randomUUID().toString();
        
        scenarioContext.registerNewOrder(alias, lastClOrdId);

        SessionID targetSession = new SessionID(sessionString);
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
                        if (msg.getString(ClOrdID.FIELD).equals(expectedClOrdId)) {
                            return true;
                        }
                    }
                }
                return false;
            });
    }

    @Then("I expect a message with MsgType {string} on session {string} for alias {string} within {int} seconds with fields:")
    public void i_expect_a_message_on_session_with_fields(String msgType, String sessionString, String alias, int timeoutSeconds, DataTable dataTable) {
        String expectedClOrdId = scenarioContext.getClOrdIdByAlias(alias);
        Map<String, String> expectedFields = dataTable.asMap();
        SessionID expectedSession = new SessionID(sessionString);
        String version = expectedSession.getBeginString();

        try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(timeoutSeconds))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> {
                    for (ScenarioContext.MessageEvent event : scenarioContext.getMessageQueue()) {
                        Message msg = event.message;
                        try {
                            if (!msg.getHeader().getString(35).equals(msgType)) continue;

                            // Ensure this message arrived ON the correct exact session requested
                            if (!event.sessionID.toString().equals(sessionString)) {
                                continue;
                            }

                            // Verify it is tied to our specific order alias via Tag 11 (ClOrdID) or 41 (OrigClOrdID)
                            String msgClOrdId = msg.isSetField(11) ? msg.getString(11) : (msg.isSetField(41) ? msg.getString(41) : "");
                            
                            if (msgClOrdId.equals(expectedClOrdId)) {
                                boolean allFieldsMatch = true;
                                for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
                                    int tag = getTagId(entry.getKey(), version);
                                    if (tag != -1 && (!msg.isSetField(tag) || !msg.getString(tag).equals(entry.getValue()))) {
                                        allFieldsMatch = false;
                                        break;
                                    }
                                }
                                if (allFieldsMatch) return true;
                            }
                        } catch (quickfix.FieldNotFound fnf) {
                            // This is an expected part of validation as we check different messages, ignore.
                        } catch (Exception e) {
                            // Any other exception is a potential bug in the test logic.
                            System.err.println("Unexpected error during message validation: " + e.getMessage());
                        }
                    }
                    return false;
                });
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            String errorMsg = "Timeout after " + timeoutSeconds + "s. Expected message for alias '" + alias + "' (ClOrdID: " + expectedClOrdId + ") not found on session " + sessionString + ". " +
                              "Total messages in queue: " + scenarioContext.getMessageQueue().size() + ". " +
                              "Queue contents: " + scenarioContext.getMessageQueue().toString().replace("\u0001", "|");
            throw new AssertionError(errorMsg, e);
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

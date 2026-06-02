package com.dfixtester.cucumber;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.cucumber.spring.CucumberContextConfiguration;

import com.dfixtester.engine.ScenarioContext;
import com.dfixtester.DFixTesterApplication;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@CucumberContextConfiguration
@SpringBootTest(classes = DFixTesterApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FixStepDefinitions {

    @Autowired
    private ScenarioContext scenarioContext;
    
    private SessionID sessionID;
    private String lastClOrdId;

    private static final Map<String, Integer> tagNameToId = new HashMap<>();

    static {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Resource resource = new ClassPathResource("config/fix-tag-dictionary-FIX.4.2.json");
            if (!resource.exists()) {
                resource = new ClassPathResource("config/fix-tag-dictionary.json");
            }
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, String> idToName = mapper.readValue(is, new TypeReference<Map<String, String>>() {});
                    for (Map.Entry<String, String> entry : idToName.entrySet()) {
                        tagNameToId.put(entry.getValue(), Integer.parseInt(entry.getKey()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            if (key.equals("Side") || key.equals("OrdType") || key.equals("Symbol")) {
                continue;
            }
            int tagId = getTagId(key);
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
            int tagId = getTagId(key);
            if (tagId != -1) {
                order.setField(new StringField(tagId, entry.getValue()));
            }
        }

        Session.sendToTarget(order, new SessionID(sessionString));
    }

    @Then("I expect an ExecutionReport for alias {string} within {int} seconds")
    public void i_expect_an_execution_report(String alias, int timeoutSeconds) {
        String expectedClOrdId = scenarioContext.getClOrdIdByAlias(alias);

        Awaitility.await()
            .atMost(Duration.ofSeconds(timeoutSeconds))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> {
                for (Message msg : scenarioContext.getMessageQueue()) {
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

        Awaitility.await()
            .atMost(Duration.ofSeconds(timeoutSeconds))
            .pollInterval(Duration.ofMillis(200))
            .until(() -> {
                for (Message msg : scenarioContext.getMessageQueue()) {
                    try {
                        if (!msg.getHeader().getString(35).equals(msgType)) continue;

                        // Ensure this message arrived ON the correct session (matching TargetCompID/SenderCompID)
                        String msgSender = msg.getHeader().getString(49);
                        String msgTarget = msg.getHeader().getString(56);
                        if (!expectedSession.getSenderCompID().equals(msgTarget) || 
                            !expectedSession.getTargetCompID().equals(msgSender)) {
                            continue;
                        }

                        // Verify it is tied to our specific order alias via Tag 11 (ClOrdID) or 41 (OrigClOrdID)
                        String msgClOrdId = msg.isSetField(11) ? msg.getString(11) : (msg.isSetField(41) ? msg.getString(41) : "");
                        
                        if (msgClOrdId.equals(expectedClOrdId)) {
                            boolean allFieldsMatch = true;
                            for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
                                int tag = getTagId(entry.getKey());
                                if (tag != -1 && (!msg.isSetField(tag) || !msg.getString(tag).equals(entry.getValue()))) {
                                    allFieldsMatch = false;
                                    break;
                                }
                            }
                            if (allFieldsMatch) return true;
                        }
                    } catch (Exception e) {
                        // Ignore field not found during validation loop
                    }
                }
                return false;
            });
    }

    private int getTagId(String fieldName) {
        if (tagNameToId.containsKey(fieldName)) {
            return tagNameToId.get(fieldName);
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

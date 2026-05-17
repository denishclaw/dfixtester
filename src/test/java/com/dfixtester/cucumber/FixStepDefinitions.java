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

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@CucumberContextConfiguration
@SpringBootTest(classes = DFixTesterApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class FixStepDefinitions {

    @Autowired
    private ScenarioContext scenarioContext;
    
    private SessionID sessionID;
    private String lastClOrdId;

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
        
        if (fields.containsKey("CustomBrokerTag")) {
            order.setField(new StringField(5000, fields.get("CustomBrokerTag")));
        }

        Session.sendToTarget(order, sessionID);
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
}

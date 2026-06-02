package com.dfixtester.engine;

import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.ClOrdID;
import quickfix.field.OrderID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.time.Instant;

@Component
public class FixApplication extends MessageCracker implements Application {

    private final ScenarioContext scenarioContext;
    private final List<Map<String, Object>> messageLog = new CopyOnWriteArrayList<>();
    private long messageIdCounter = 0;

    // Static reference so embedded Cucumber tests can access the active context
    private static ScenarioContext activeScenarioContext;

    public FixApplication(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
        activeScenarioContext = scenarioContext;
    }

    public static ScenarioContext getActiveScenarioContext() {
        return activeScenarioContext;
    }

    public List<Map<String, Object>> getMessageLog() {
        return messageLog;
    }

    private synchronized void logMessage(Message message, SessionID sessionID, String direction) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("id", ++messageIdCounter);
        entry.put("timestamp", Instant.now().toString());
        entry.put("session", sessionID.toString());
        entry.put("direction", direction);
        entry.put("message", message.toString().replace("\u0001", "|"));
        messageLog.add(entry);
        if (messageLog.size() > 1000) {
            messageLog.remove(0);
        }
    }

    @Override
    public void onCreate(SessionID sessionID) {}

    @Override
    public void onLogon(SessionID sessionID) {
        System.out.println("Session Logged On: " + sessionID);
    }

    @Override
    public void onLogout(SessionID sessionID) {
        System.out.println("Session Logged Out: " + sessionID);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        logMessage(message, sessionID, "OUT");
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        logMessage(message, sessionID, "IN");
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        logMessage(message, sessionID, "OUT");
        scenarioContext.addMessageEvent(sessionID, ScenarioContext.Direction.OUT, message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        logMessage(message, sessionID, "IN");
        scenarioContext.addMessageEvent(sessionID, ScenarioContext.Direction.IN, message);

        if (message.getHeader().getString(35).equals("8")) {
            if (message.isSetField(ClOrdID.FIELD) && message.isSetField(OrderID.FIELD)) {
                scenarioContext.recordExchangeOrderId(
                    message.getString(ClOrdID.FIELD), 
                    message.getString(OrderID.FIELD)
                );
            }
        }
    }
}

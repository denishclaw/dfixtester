package com.dfixtester.engine;

import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.ClOrdID;
import quickfix.field.OrderID;

@Component
public class FixApplication extends MessageCracker implements Application {

    private final ScenarioContext scenarioContext;

    public FixApplication(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
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
    public void toAdmin(Message message, SessionID sessionID) {}

    @Override
    public void fromAdmin(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {}

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {}

    @Override
    public void fromApp(Message message, SessionID sessionID) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        scenarioContext.addReceivedMessage(message);

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

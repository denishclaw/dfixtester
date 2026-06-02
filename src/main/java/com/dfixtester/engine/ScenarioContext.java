package com.dfixtester.engine;

import org.springframework.stereotype.Component;
import quickfix.Message;
import quickfix.SessionID;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class ScenarioContext {
    public enum Direction { IN, OUT }

    public static class MessageEvent {
        public final SessionID sessionID;
        public final Direction direction;
        public final Message message;

        public MessageEvent(SessionID sessionID, Direction direction, Message message) {
            this.sessionID = sessionID;
            this.direction = direction;
            this.message = message;
        }

        @Override
        public String toString() {
            return "[" + direction + " on " + sessionID.toString() + "] " + message.toString();
        }
    }

    private final Map<String, String> activeOrders = new ConcurrentHashMap<>();
    private final Map<String, String> aliasToClOrdId = new ConcurrentHashMap<>();
    private final Queue<MessageEvent> messageQueue = new ConcurrentLinkedQueue<>();

    public void registerNewOrder(String alias, String clOrdId) {
        aliasToClOrdId.put(alias, clOrdId);
    }

    public void recordExchangeOrderId(String clOrdId, String exchangeOrderId) {
        if (clOrdId != null && exchangeOrderId != null) {
            activeOrders.put(clOrdId, exchangeOrderId);
        }
    }
    
    public void addMessageEvent(SessionID sessionID, Direction direction, Message message) {
        messageQueue.add(new MessageEvent(sessionID, direction, message));
    }
    
    public Queue<MessageEvent> getMessageQueue() {
        return messageQueue;
    }

    public String getClOrdIdByAlias(String alias) {
        return aliasToClOrdId.get(alias);
    }

    public void clear() {
        activeOrders.clear();
        aliasToClOrdId.clear();
        messageQueue.clear();
    }
}

package com.dfixtester.engine;

import org.springframework.stereotype.Component;
import quickfix.Message;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class ScenarioContext {
    private final Map<String, String> activeOrders = new ConcurrentHashMap<>();
    private final Map<String, String> aliasToClOrdId = new ConcurrentHashMap<>();
    private final Queue<Message> messageQueue = new ConcurrentLinkedQueue<>();

    public void registerNewOrder(String alias, String clOrdId) {
        aliasToClOrdId.put(alias, clOrdId);
    }

    public void recordExchangeOrderId(String clOrdId, String exchangeOrderId) {
        if (clOrdId != null && exchangeOrderId != null) {
            activeOrders.put(clOrdId, exchangeOrderId);
        }
    }
    
    public void addReceivedMessage(Message message) {
        messageQueue.add(message);
    }
    
    public Queue<Message> getMessageQueue() {
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

package com.dfixtester.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.ThreadedSocketInitiator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private ThreadedSocketInitiator initiator;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Session session : initiator.getManagedSessions()) {
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionString", session.getSessionID().toString());
            sessionData.put("isLoggedOn", session.isLoggedOn());
            result.add(sessionData);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{sessionString}/reset")
    public ResponseEntity<String> resetSequence(@PathVariable String sessionString) {
        try {
            SessionID sessionId = new SessionID(sessionString);
            Session session = Session.lookupSession(sessionId);
            
            if (session == null) {
                return ResponseEntity.status(404).body("Session not found.");
            }
            
            session.reset();
            return ResponseEntity.ok("Sequence reset successfully for: " + sessionString);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{sessionString}/logon")
    public ResponseEntity<String> logon(@PathVariable String sessionString) {
        Session session = Session.lookupSession(new SessionID(sessionString));
        if (session != null) {
            session.logon();
            return ResponseEntity.ok("Logon triggered");
        }
        return ResponseEntity.status(404).body("Session not found");
    }

    @PostMapping("/{sessionString}/logout")
    public ResponseEntity<String> logout(@PathVariable String sessionString) {
        Session session = Session.lookupSession(new SessionID(sessionString));
        if (session != null) {
            session.logout();
            return ResponseEntity.ok("Logout triggered");
        }
        return ResponseEntity.status(404).body("Session not found");
    }

    @PostMapping("/{sessionString}/send")
    public ResponseEntity<String> sendMessage(@PathVariable String sessionString, @RequestBody Map<String, String> tags) {
        try {
            SessionID sessionId = new SessionID(sessionString);
            Message msg = buildMessage(tags);
            Session.sendToTarget(msg, sessionId);
            return ResponseEntity.ok("Message sent");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{sessionString}/replay")
    public ResponseEntity<String> replayMessages(@PathVariable String sessionString, @RequestBody List<Map<String, String>> messages) {
        try {
            SessionID sessionId = new SessionID(sessionString);
            for (Map<String, String> tags : messages) {
                Message msg = buildMessage(tags);
                Session.sendToTarget(msg, sessionId);
                Thread.sleep(100); // Small 100ms delay between replay messages
            }
            return ResponseEntity.ok("Replayed " + messages.size() + " messages successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    private Message buildMessage(Map<String, String> tags) {
        Message msg = new Message();
        for (Map.Entry<String, String> entry : tags.entrySet()) {
            int tag = Integer.parseInt(entry.getKey());
            if (tag == 35) { // MsgType belongs in the header
                msg.getHeader().setString(35, entry.getValue());
            } else {
                msg.setString(tag, entry.getValue());
            }
        }
        return msg;
    }
}

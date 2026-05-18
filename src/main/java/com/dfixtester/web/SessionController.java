package com.dfixtester.web;

import com.dfixtester.engine.FixApplication;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private ThreadedSocketInitiator initiator;

    @Autowired
    private FixApplication fixApplication;
    
    private static final DateTimeFormatter FIX_UTC_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HH:mm:ss.SSS").withZone(ZoneId.of("UTC"));

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getSessions() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (Session session : initiator.getManagedSessions()) {
            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionString", session.getSessionID().toString());
            sessionData.put("isLoggedOn", session.isLoggedOn());
            sessionData.put("inSeq", session.getExpectedTargetNum());
            sessionData.put("outSeq", session.getExpectedSenderNum());
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
            if (session.isLoggedOn()) {
                return ResponseEntity.status(400).body("Cannot reset sequence numbers while session is logged on.");
            }
            
            session.reset();
            return ResponseEntity.ok("Sequence reset successfully for: " + sessionString);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/{sessionString}/setseq")
    public ResponseEntity<String> setSequence(@PathVariable String sessionString, @RequestBody Map<String, Integer> seqNums) {
        try {
            SessionID sessionId = new SessionID(sessionString);
            Session session = Session.lookupSession(sessionId);

            if (session == null) {
                return ResponseEntity.status(404).body("Session not found.");
            }

            if (session.isLoggedOn()) {
                return ResponseEntity.status(400).body("Cannot set sequence numbers while session is logged on.");
            }

            if (seqNums.containsKey("inSeq")) {
                session.setNextTargetMsgSeqNum(seqNums.get("inSeq"));
            }
            if (seqNums.containsKey("outSeq")) {
                session.setNextSenderMsgSeqNum(seqNums.get("outSeq"));
            }
            return ResponseEntity.ok("Sequence numbers updated for: " + sessionString);
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
    public ResponseEntity<String> replayMessages(
            @PathVariable String sessionString,
            @RequestParam(defaultValue = "100") long throttle,
            @RequestParam(defaultValue = "1") int repeat,
            @RequestBody List<Map<String, String>> messages) {
        try {
            SessionID sessionId = new SessionID(sessionString);
            int count = 0;
            
            for (int i = 0; i < repeat; i++) {
                for (Map<String, String> originalTags : messages) {
                    Map<String, String> tags = new HashMap<>(originalTags);
                    
                    // Dynamically generate Tags 11 and 60 just like Send Single Message
                    if (tags.containsKey("11")) {
                        tags.put("11", "ORD_" + System.currentTimeMillis() + "_" + count);
                    }
                    if (tags.containsKey("60")) {
                        tags.put("60", FIX_UTC_TIMESTAMP_FORMATTER.format(Instant.now()));
                    }
                    
                    Message msg = buildMessage(tags);
                    Session.sendToTarget(msg, sessionId);
                    count++;
                    
                    if (throttle > 0) {
                        Thread.sleep(throttle);
                    }
                }
            }
            return ResponseEntity.ok("Replayed " + count + " messages successfully.");
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

    @GetMapping("/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@RequestParam(defaultValue = "0") long since) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> msg : fixApplication.getMessageLog()) {
            if ((Long) msg.get("id") > since) {
                result.add(msg);
            }
        }
        return ResponseEntity.ok(result);
    }
}

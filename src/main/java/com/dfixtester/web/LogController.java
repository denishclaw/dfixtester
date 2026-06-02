package com.dfixtester.web;

import com.dfixtester.engine.SystemLogCapture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    @GetMapping
    public ResponseEntity<List<SystemLogCapture.LogEntry>> getLogs(@RequestParam(defaultValue = "0") int since) {
        List<SystemLogCapture.LogEntry> result = new ArrayList<>();
        for (SystemLogCapture.LogEntry entry : SystemLogCapture.getLogEntries()) {
            if (entry.id > since) result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/clear")
    public ResponseEntity<String> clearLogs() {
        SystemLogCapture.clear();
        return ResponseEntity.ok("Cleared");
    }
}
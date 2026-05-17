package com.dfixtester.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@RestController
@RequestMapping("/api/tests")
public class TestController {

    @PostMapping("/run")
    public ResponseEntity<String> runTest(@RequestParam(required = false) String feature) {
        StringBuilder output = new StringBuilder();
        try {
            // Base command to run tests, overriding the default skipTests property in pom.xml
            String cmd = "mvn test -DskipTests=false";
            
            // Optionally filter to run a specific feature or scenario via system properties
            if (feature != null && !feature.isEmpty()) {
                cmd += " -Dcucumber.features=\"src/test/resources/features/" + feature + "\"";
            }
            
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            return ResponseEntity.ok(output.toString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to run tests: " + e.getMessage() + "\n" + output.toString());
        }
    }
}

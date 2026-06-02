package com.dfixtester.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/tests")
public class TestController {

    @GetMapping("/features")
    public ResponseEntity<List<String>> getFeatures() {
        List<String> featureFiles = new ArrayList<>();
        File featuresDir = new File("features"); // Expects a 'features' folder in the same directory as the jar
        if (featuresDir.exists() && featuresDir.isDirectory()) {
            File[] files = featuresDir.listFiles((dir, name) -> name.endsWith(".feature"));
            if (files != null) {
                for (File file : files) {
                    featureFiles.add(file.getName());
                }
            }
        }
        return ResponseEntity.ok(featureFiles);
    }

    @PostMapping("/run")
    public ResponseEntity<String> runTest(@RequestParam(required = false) String feature) {
        StringBuilder output = new StringBuilder();
        try {
            // Base command to run tests, overriding the default skipTests property in pom.xml
            String cmd = "mvn test -DskipTests=false";
            
            // Point cucumber to the external 'features/' directory next to the JAR
            if (feature != null && !feature.isEmpty()) {
                String[] features = feature.split(",");
                StringBuilder featuresArg = new StringBuilder();
                for (int i = 0; i < features.length; i++) {
                    if (i > 0) featuresArg.append(",");
                    featuresArg.append("features/").append(features[i].trim());
                }
                cmd += " -Dcucumber.features=\"" + featuresArg.toString() + "\"";
            } else {
                cmd += " -Dcucumber.features=\"features/\"";
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

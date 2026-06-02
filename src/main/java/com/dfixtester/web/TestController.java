package com.dfixtester.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;

        try {
            // Redirect standard output to capture Cucumber's console logging
            System.setOut(out);
            System.setErr(out);
            
            List<String> args = new ArrayList<>();
            args.add("-g");
            args.add("com.dfixtester.cucumber"); // Package where your Step Definitions live
            args.add("-p");
            args.add("pretty"); // Format output nicely

            if (feature != null && !feature.isEmpty()) {
                String[] features = feature.split(",");
                for (int i = 0; i < features.length; i++) {
                    args.add("features/" + features[i].trim());
                }
            } else {
                args.add("features/");
            }
            
            // Run Cucumber programmatically
            byte exitCode = io.cucumber.core.cli.Main.run(args.toArray(new String[0]), Thread.currentThread().getContextClassLoader());
            
            out.flush();
            return ResponseEntity.ok(baos.toString() + "\n\nExit Status: " + exitCode);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to run tests: " + e.getMessage() + "\n" + baos.toString());
        } finally {
            // Always restore the original streams
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
    }
}

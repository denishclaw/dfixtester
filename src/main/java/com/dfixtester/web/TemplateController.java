package com.dfixtester.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getTemplates() {
        return ResponseEntity.ok(loadTemplatesFromFile("message-templates.json"));
    }

    @GetMapping("/atdl")
    public ResponseEntity<List<Map<String, Object>>> getAtdlTemplates() {
        return ResponseEntity.ok(loadTemplatesFromFile("atdl-message-templates.json"));
    }
    
    @GetMapping("/multi-order")
    public ResponseEntity<List<List<Map<String, String>>>> getMultiOrderTemplates() {
        return ResponseEntity.ok(loadMultiOrderTemplatesFromFile("multi-order-templates.json"));
    }

    private List<Map<String, Object>> loadTemplatesFromFile(String filename) {
        try {
            ApplicationHome home = new ApplicationHome(TemplateController.class);
            File baseDir = home.getDir();

            File extFile = new File(baseDir, "config/" + filename);
            if (!extFile.exists()) {
                extFile = new File(baseDir, filename);
            }
            if (!extFile.exists()) {
                extFile = new File("config/" + filename);
            }

            if (extFile.exists()) {
                return objectMapper.readValue(extFile, new TypeReference<>() {});
            }
            
            Resource resource = new ClassPathResource("config/" + filename);
            if (resource.exists()) {
                return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private List<List<Map<String, String>>> loadMultiOrderTemplatesFromFile(String filename) {
        try {
            ApplicationHome home = new ApplicationHome(TemplateController.class);
            File baseDir = home.getDir();

            File extFile = new File(baseDir, "multi-order-templates/" + filename);
            if (!extFile.exists()) {
                extFile = new File(baseDir, filename); // Fallback to root for convenience
            }

            if (extFile.exists()) {
                return objectMapper.readValue(extFile, new TypeReference<>() {});
            }

            Resource resource = new ClassPathResource("multi-order-templates/" + filename);
            if (resource.exists()) {
                return objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
package com.dfixtester.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        try {
            File extFile = new File("config/message-templates.json");
            if (extFile.exists()) {
                List<Map<String, Object>> templates = objectMapper.readValue(extFile, new TypeReference<List<Map<String, Object>>>() {});
                return ResponseEntity.ok(templates);
            }
            
            Resource resource = new ClassPathResource("config/message-templates.json");
            if (resource.exists()) {
                List<Map<String, Object>> templates = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<Map<String, Object>>>() {});
                return ResponseEntity.ok(templates);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok(new ArrayList<>());
    }
}
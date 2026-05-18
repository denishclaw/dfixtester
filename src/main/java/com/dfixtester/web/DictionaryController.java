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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<Map<String, String>> getDictionary() {
        try {
            File extFile = new File("config/fix-tag-dictionary.json");
            if (extFile.exists()) {
                Map<String, String> dictionary = objectMapper.readValue(extFile, new TypeReference<Map<String, String>>() {});
                return ResponseEntity.ok(dictionary);
            }
            
            Resource resource = new ClassPathResource("config/fix-tag-dictionary.json");
            if (resource.exists()) {
                Map<String, String> dictionary = objectMapper.readValue(resource.getInputStream(), new TypeReference<Map<String, String>>() {});
                return ResponseEntity.ok(dictionary);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResponseEntity.ok(new HashMap<>());
    }
}
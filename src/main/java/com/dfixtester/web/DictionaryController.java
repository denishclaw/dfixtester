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
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public ResponseEntity<Map<String, String>> getDictionary(@RequestParam(required = false, defaultValue = "") String version) {
        String fileName = version.isEmpty() ? "config/fix-tag-dictionary.json" : "config/fix-tag-dictionary-" + version + ".json";
        Map<String, String> dictionary = tryLoadDictionary(fileName);
        
        if (dictionary.isEmpty() && !version.isEmpty()) {
            // Fallback to default dictionary if version-specific file is missing
            dictionary = tryLoadDictionary("config/fix-tag-dictionary.json");
        }
        
        return ResponseEntity.ok(dictionary);
    }

    private Map<String, String> tryLoadDictionary(String path) {
        try {
            File extFile = new File(path);
            if (extFile.exists()) {
                return objectMapper.readValue(extFile, new TypeReference<Map<String, String>>() {});
            }
            
            Resource resource = new ClassPathResource(path);
            if (resource.exists()) {
                return objectMapper.readValue(resource.getInputStream(), new TypeReference<Map<String, String>>() {});
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new HashMap<>();
    }
}
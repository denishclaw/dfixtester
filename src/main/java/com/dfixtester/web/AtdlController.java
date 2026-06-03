package com.dfixtester.web;

import org.springframework.boot.system.ApplicationHome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/atdl")
public class AtdlController {

    private File getAtdlDirectory() {
        ApplicationHome home = new ApplicationHome(AtdlController.class);
        File dir = home.getDir();
        
        File atdlDir = dir != null ? new File(dir, "atdl") : new File("atdl");
        if (!atdlDir.exists()) {
            File fallbackDir = new File("atdl");
            if (fallbackDir.exists() && fallbackDir.isDirectory()) {
                return fallbackDir;
            }
        }
        return atdlDir;
    }

    @GetMapping("/files")
    public ResponseEntity<List<String>> getFiles() {
        List<String> fileList = new ArrayList<>();
        File atdlDir = getAtdlDirectory();
        if (atdlDir.exists() && atdlDir.isDirectory()) {
            File[] files = atdlDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".xml"));
            if (files != null) {
                for (File file : files) {
                    fileList.add(file.getName());
                }
            }
        }
        return ResponseEntity.ok(fileList);
    }

    @GetMapping("/file/{filename}")
    public ResponseEntity<String> getFileContent(@PathVariable String filename) throws IOException {
        File file = new File(getAtdlDirectory(), filename);
        if (!file.exists() || !file.isFile()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new String(Files.readAllBytes(file.toPath())));
    }
}
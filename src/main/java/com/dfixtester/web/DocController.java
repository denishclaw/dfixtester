package com.dfixtester.web;

import org.springframework.boot.system.ApplicationHome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/docs")
public class DocController {

    @GetMapping("/readme")
    public ResponseEntity<String> getReadme() throws IOException {
        ApplicationHome home = new ApplicationHome(DocController.class);
        File baseDir = home.getDir();

        File readme = new File(baseDir, "README.md");
        if (!readme.exists()) {
            readme = new File("README.md"); // Fallback for IDE execution
        }

        if (!readme.exists() || !readme.isFile()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new String(Files.readAllBytes(readme.toPath())));
    }
}
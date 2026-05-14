package com.movie.catalog_service.controller;

import com.movie.catalog_service.service.file.FileUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/files")
public class FileController {
    @Autowired
    FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadGenericFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("isVideo") boolean isVideo
            ) throws IOException {
        String fileUrl = fileUploadService.uploadFile(file, isVideo);
        return ResponseEntity.ok(fileUrl);
    }
}

package com.movie.catalog_service.service.file;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.movie.catalog_service.exception.APIException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class FileUploadService {
    @Autowired
    private Cloudinary cloudinary;

    private final List<String> ALLOWED_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");
    private final List<String> ALLOWED_VIDEO_EXTENSIONS = List.of(".mp4", ".avi", ".mkv", ".webm");

    public String uploadFile(MultipartFile file, boolean isVideo) throws IOException {
        if (file.isEmpty()) {
            throw new APIException("File is not empty");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null && !originalFileName.contains(".")) {
            throw new APIException("File name is not valid or not extension");
        }

        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();

        if (isVideo) {
            if (!ALLOWED_VIDEO_EXTENSIONS.contains(fileExtension)) {
                throw new APIException("You are uploading video, but file is have extension: " + fileExtension + " Supported: .mp4, .avi, .mkv, .webm");
            }
        } else {
            if (!ALLOWED_IMAGE_EXTENSIONS.contains(fileExtension)) {
                throw new APIException("You are uploading image, but file is have extension: " + fileExtension + " Supported: .jpg, .jpeg, .png, .webp");
            }
        }
        try {
            String resourceType = isVideo ? "video" : "auto";

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(),
                    ObjectUtils.asMap("resource_type", resourceType));

            return uploadResult.get("secure_url").toString();
        } catch (IOException e) {
            if (e.getMessage().contains("Unsupported")) {
                throw new APIException("Extension of file is unsupported by sever");
            }
            throw new APIException("Error when upload file: " + e.getMessage());
        }
    }
}


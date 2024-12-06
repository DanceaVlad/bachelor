package com.dancea.microservice.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NdviController {

    private final NdviService ndviService;

    /**
     * Endpoint to fetch NDVI data using a bounding box.
     * 
     * @param bbox A bounding box array with exactly 4 values: [minLon, minLat, maxLon, maxLat].
     * @return JSON response
     */
    @GetMapping("/ndvi/response")
    public String getResponseBody(@RequestParam double[] bbox) {
        return ndviService.getResponseBody(bbox);
    }

    /**
     * Endpoint to fetch NDVI data using a bounding box.
     * 
     * @param bbox A bounding box array with exactly 4 values: [minLon, minLat, maxLon, maxLat].
     * @return List of TIF files
     */
    @GetMapping("/ndvi/files")
    public ResponseEntity<?> getNdviFiles(@RequestParam double[] bbox) {
        try {
            List<File> files = ndviService.getNdviFiles(bbox);

            if (files.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (ZipOutputStream zipOut = new ZipOutputStream(byteArrayOutputStream)) {
                for (File file : files) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        ZipEntry zipEntry = new ZipEntry(file.getName());
                        zipOut.putNextEntry(zipEntry);

                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = fis.read(buffer)) > 0) {
                            zipOut.write(buffer, 0, length);
                        }
                        zipOut.closeEntry();
                    }
                }
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ndvi-files.zip")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(byteArrayOutputStream.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error while fetching NDVI files");
        }
    }

}
package com.dancea.microservice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

@Controller
public class NdviController {
    
    private static final String TILE_DIRECTORY = "microservice/src/main/resources/temporary/tiles";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NdviController.class);

    private final NdviService ndviService;

    public NdviController(NdviService ndviService) {
        this.ndviService = ndviService;
    }

    @GetMapping("/download-geotiffs")
    public ResponseEntity<String> downloadGeoTiffs() {
        try {
            ndviService.downloadGeoTiffs();
            return ResponseEntity.ok("GeoTIFF files downloaded successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error downloading GeoTIFF files.");
        }
    }

    @GetMapping("/merge-geotiffs")
    public ResponseEntity<String> mergeGeoTiffs() {
        try {
            ndviService.mergeGeoTiffs();
            return ResponseEntity.ok("GeoTIFF files merged successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error merging GeoTIFF files.");
        }
    }

    @GetMapping("/tiles/{z}/{x}/{y}.png")
    public ResponseEntity<Resource> getTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        try {
            // log the current directory
            Path tilePath = Paths.get(TILE_DIRECTORY, String.valueOf(z), String.valueOf(x), y + ".png");
            if (!Files.exists(tilePath)) {
                logger.warn("Tile not found: {}", tilePath);
                return ResponseEntity.notFound().build();
            }
            logger.info("Serving tile: {}", tilePath);
            Resource resource = new FileSystemResource(tilePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}

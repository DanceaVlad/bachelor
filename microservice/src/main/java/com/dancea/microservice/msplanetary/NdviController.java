package com.dancea.microservice.msplanetary;

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
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NdviController.class);
    private static final String PROVIDER = "/msplanetary";

    private final NdviService ndviService;

    public NdviController(NdviService ndviService) {
        this.ndviService = ndviService;
    }

    @GetMapping(PROVIDER + "/download-geotiffs")
    public ResponseEntity<String> downloadGeoTiffs() {
        try {
            ndviService.downloadGeoTiffs();
            return ResponseEntity.ok("GeoTIFF files downloaded successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error downloading GeoTIFF files.");
        }
    }

    @GetMapping(PROVIDER + "/merge-geotiffs")
    public ResponseEntity<String> mergeGeoTiffs() {
        try {
            ndviService.mergeGeoTiffs();
            return ResponseEntity.ok("GeoTIFF files merged successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error merging GeoTIFF files.");
        }
    }

    @GetMapping(PROVIDER + "/generate-tiles")
    public ResponseEntity<String> generateTiles() {
        try {
            ndviService.generateTiles();
            return ResponseEntity.ok("Tiles generated successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating tiles.");
        }
    }
    
    @GetMapping("PROVIDER + /tiles/{z}/{x}/{y}.png")
    public ResponseEntity<Resource> getTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        try {
            Path tilePath = Paths.get(Paths.get(".").toAbsolutePath().normalize().toString(), Utils.TILE_OUTPUT_DIR, String.valueOf(z), String.valueOf(x), y + ".png");
            if (!Files.exists(tilePath)) {
                logger.error("Tile not found: {}", tilePath);
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(tilePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


}

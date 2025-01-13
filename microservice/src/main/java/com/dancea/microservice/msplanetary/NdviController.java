package com.dancea.microservice.msplanetary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import com.dancea.microservice.Utils;

@Controller
@CrossOrigin(origins = "*")
public class NdviController {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NdviController.class);

    @GetMapping(Utils.MSPLANETARY_PROVIDER_URI_PATH + "/tiles/{z}/{x}/{y}.png")
    public ResponseEntity<Resource> getTile(
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y) {
        try {
            Path tilePath = Paths.get(Paths.get(".").toAbsolutePath().normalize().toString(),
                    Utils.MSPLANETARY_TILE_DIR_PATH, String.valueOf(z), String.valueOf(x), y + ".png");
            if (!Files.exists(tilePath)) {
                logger.error("Tile not found: {}", tilePath);
                return ResponseEntity.notFound()
                        .header("Access-Control-Allow-Origin", "*")
                        .build();
            }
            Resource resource = new FileSystemResource(tilePath);
            return ResponseEntity.ok()
                    .header("Access-Control-Allow-Origin", "*")
                    .contentType(MediaType.IMAGE_PNG)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

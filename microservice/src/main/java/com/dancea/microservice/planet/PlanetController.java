package com.dancea.microservice.planet;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;



@Controller
public class PlanetController {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(PlanetController.class);

    private final PlanetService planetService;

    public PlanetController(PlanetService planetService) {
        this.planetService = planetService;
    }

    @GetMapping(PlanetUtils.PLANET_PROVIDER_URI_PATH + "/download-geotiffs")
    public ResponseEntity<String> downloadGeoTiffs() {
        try {
            planetService.downloadGeoTiffs();
            logger.info(PlanetUtils.PLANET_PROVIDER_NAME + ": GeoTIFF files downloaded successfully.");
            return ResponseEntity.ok(PlanetUtils.PLANET_PROVIDER_NAME + ": GeoTIFF files downloaded successfully.");
        } catch (Exception e) {
            logger.error(PlanetUtils.PLANET_PROVIDER_NAME + ": Error downloading GeoTIFF files.\n", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PlanetUtils.PLANET_PROVIDER_NAME + ": Error downloading GeoTIFF files.");
        }
    }

    @GetMapping(PlanetUtils.PLANET_PROVIDER_URI_PATH + "/divide-geotiffs")
    public ResponseEntity<String> divideGeoTiffs() {
        try {
            planetService.divideGeoTiffs();
            logger.info(PlanetUtils.PLANET_PROVIDER_NAME + ": GeoTIFF files divided successfully.");
            return ResponseEntity.ok(PlanetUtils.PLANET_PROVIDER_NAME + ": GeoTIFF files divided successfully.");
        } catch (Exception e) {
            logger.error(PlanetUtils.PLANET_PROVIDER_NAME + ": Error dividing GeoTIFF files.\n", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PlanetUtils.PLANET_PROVIDER_NAME + ": Error dividing GeoTIFF files.");
        }
    }

    @GetMapping(PlanetUtils.PLANET_PROVIDER_URI_PATH + "/merge-geotiffs")
    public ResponseEntity<String> mergeGeoTiffs() {
        try {
            planetService.mergeGeoTiffs();
            logger.info(PlanetUtils.PLANET_PROVIDER_NAME + ": GeoTIFF files merged successfully.");
            return ResponseEntity.ok(PlanetUtils.PLANET_PROVIDER_NAME + ": GeoTIFF files merged successfully.");
        } catch (Exception e) {
            logger.error(PlanetUtils.PLANET_PROVIDER_NAME + ": Error merging GeoTIFF files.\n", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(PlanetUtils.PLANET_PROVIDER_NAME + ": Error merging GeoTIFF files.");
        }
    }
    
    
    
}

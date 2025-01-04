package com.dancea.microservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NdviService {

    private static final Logger logger = LoggerFactory.getLogger(NdviService.class);

    private static final String COLLECTION_ID = "modis-13A1-061";
    private static final String PLANETARY_COMPUTER_SEARCH_URL = "https://planetarycomputer.microsoft.com/api/stac/v1/search";
    private static final String TILE_OUTPUT_DIR = "microservice/src/main/resources/tiles/";
    private static final String TEMP_DIR = "microservice/src/main/resources/temporary/";

    private final RestTemplate restTemplate;


    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch all GeoTIFF files, convert to tiles, and save locally.
     */
    public void initializeData() {
        try {
            // Step 1: Fetch GeoTIFF links from the external source
            List<String> geoTiffLinks = fetchAllGeoTiffLinks();

            // Step 2: Download GeoTIFF files
            List<File> geoTiffFiles = downloadGeoTiffs(geoTiffLinks);

            // Step 3: Convert GeoTIFF files to tiles
            for (File tifFile : geoTiffFiles) {
                try {
                    convertTifToTiles(tifFile);
                } catch (Exception e) {
                    logger.error("Failed to convert TIF to tiles for file: {}", tifFile.getName(), e);
                }
            }

            logger.info("All GeoTIFF files have been processed and tiles are saved in: {}", TILE_OUTPUT_DIR);
        } catch (Exception e) {
            logger.error("Error during GeoTIFF processing", e);
        }
    }

    /**
     * Fetch all GeoTIFF links from the external source.
     */
    private List<String> fetchAllGeoTiffLinks() {
        List<String> geoTiffLinks = new ArrayList<>();

        try {
            // Define the request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("collections", new String[]{COLLECTION_ID});
            payload.put("datetime", "2023-06-01T00:00:00Z/2023-06-01T23:59:59Z");
            payload.put("limit", 3);
            payload.put("sortby", new Object[]{Map.of("field", "datetime", "direction", "asc")});
            payload.put("query", Map.of("platform", Map.of("eq", "terra")));

            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // Create the request
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            // Send POST request to fetch the GeoTIFF links
            String response = restTemplate.postForObject(PLANETARY_COMPUTER_SEARCH_URL, request, String.class);

            // Parse the response to extract GeoTIFF links
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode features = rootNode.get("features");

            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode assets = feature.get("assets");
                    if (assets != null && assets.has("500m_16_days_NDVI")) {
                        String href = assets.get("500m_16_days_NDVI").get("href").asText();
                        geoTiffLinks.add(href);
                    }
                }
            }
            logger.info("Fetched {} GeoTIFF links", geoTiffLinks.size());
            
            List<String> signedLinks = Utils.appendSASTokenToLinks(geoTiffLinks);
            logger.info("Successfully signed GeoTIFF links");

            for (String link : signedLinks) {
                logger.info(link);
            }

            return signedLinks;

        } catch (Exception e) {
            logger.error("Error fetching GeoTIFF links", e);
        }

        return geoTiffLinks;
    }

    /**
     * Download GeoTIFF files to a temporary directory.
     */
    private List<File> downloadGeoTiffs(List<String> geoTiffLinks) {
        List<File> downloadedFiles = new ArrayList<>();

        try {
            Files.createDirectories(Paths.get(TEMP_DIR));
        } catch (IOException e) {
            logger.error("Failed to create temporary directory: {}", TEMP_DIR, e);
            return downloadedFiles;
        }

        for (String link : geoTiffLinks) {
            try {
                String filePath = TEMP_DIR + extractFileName(link);
                File file = new File(filePath);

                if (!file.exists()) {
                    try (InputStream in = new URL(link).openStream()) {
                        Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        downloadedFiles.add(file);
                        logger.info("Downloaded GeoTIFF: {}", file.getName());
                    }
                } else {
                    downloadedFiles.add(file);
                }
            } catch (Exception e) {
                logger.error("Failed to download GeoTIFF from link: {}", link, e);
            }
        }

        return downloadedFiles;
    }

    /**
     * Convert GeoTIFF files to tiles using GDAL.
     */
    private void convertTifToTiles(File tifFile) throws IOException, InterruptedException {
        String basePath = tifFile.getParent(); // Parent directory
        String simplifiedPath = basePath + "/reprojected.tif";
        String reprojectedPath = basePath + "/reprojected_3857.tif";
        String bytePath = basePath + "/reprojected_3857_byte.vrt";
        String tileOutputDir = basePath + "/tiles";
    
        // Step 1: Rename the file to a simpler name
        String moveCommand = String.format("mv %s %s", tifFile.getAbsolutePath(), simplifiedPath);
        runCommand(moveCommand.split(" "), "Failed to rename GeoTIFF file");
    
        // Step 2: Reproject to EPSG:3857
        String[] warpCommand = {
            "gdalwarp",
            "-t_srs", "EPSG:3857",
            simplifiedPath,
            reprojectedPath
        };
        runCommand(warpCommand, "Failed to reproject GeoTIFF to EPSG:3857");
    
        // Step 3: Convert to 8-bit Byte format
        String[] translateCommand = {
            "gdal_translate",
            "-of", "VRT",
            "-ot", "Byte",
            "-scale",
            reprojectedPath,
            bytePath
        };
        runCommand(translateCommand, "Failed to convert GeoTIFF to 8-bit Byte format");
    
        // Step 4: Generate tiles with 3 processes
        String[] tileCommand = {
            "gdal2tiles.py",
            "--processes=3",
            "-z", "0-10",
            "-p", "mercator",
            bytePath,
            tileOutputDir
        };
        runCommand(tileCommand, "Failed to generate tiles from GeoTIFF");
    
        // Step 5: Clean up intermediate files
        cleanupIntermediateFiles(simplifiedPath, reprojectedPath, bytePath);
    }
    
    private void runCommand(String[] command, String errorMessage) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (process.waitFor() != 0) {
            throw new IOException(errorMessage);
        }
    }
    
    private void cleanupIntermediateFiles(String... files) {
        for (String file : files) {
            try {
                Files.deleteIfExists(Paths.get(file));
            } catch (IOException e) {
                System.err.println("Failed to delete intermediate file: " + file);
            }
        }
    }

    private String extractFileName(String link) {
        String noQuery = link.substring(0, link.indexOf('?'));
        return noQuery.substring(noQuery.lastIndexOf("/") + 1);
    }
}
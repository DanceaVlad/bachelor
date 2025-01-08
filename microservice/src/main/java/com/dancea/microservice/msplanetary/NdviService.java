package com.dancea.microservice.msplanetary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Service
public class NdviService {

    private static final Logger logger = LoggerFactory.getLogger(NdviService.class);

    private final RestTemplate restTemplate;

    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch all GeoTIFF files and save them locally.
     */
    public void downloadGeoTiffs() {

        // Step 1: Fetch GeoTIFF links from the external source
        List<String> geoTiffLinks = fetchAllGeoTiffLinks();

        // Step 2: Download GeoTIFF files
        downloadGeoTiffs(geoTiffLinks);
    }

    /**
     * Merge all GeoTIFF files into a single file.
     */
    public void mergeGeoTiffs() {

        // Step 1: Fetch GeoTIFF files
        List<String> geoTiffFiles = fetchGeoTiffNames();

        // Step 2: Build GDAL command
        String[] command = buildGdalCommand(geoTiffFiles);

        // Step 3: Run the command
        Utils.runCommand(command, "Successfully merged GeoTIFF files", "Failed to merge GeoTIFF files");
    }

    /**
     * Generate tiles from the merged GeoTIFF file with a limited number of zoom
     * levels.
     */
    public void generateTiles() {

        // Step 1: Calculate the maximum zoom level
        int maxZoom = calculateMaxZoom(294); // Limit to approximately 294 tiles

        // Step 2: Reproject to EPSG:3857
        String[] warpCommand = {
                "gdalwarp",
                "-t_srs", "EPSG:3857",
                Utils.MERGE_OUTPUT_DIR,
                Utils.REPROJECTED_OUTPUT_DIR
        };
        Utils.runCommand(warpCommand, "Successfully reprojected merged GeoTIFF to EPSG:3857",
                "Failed to reproject merged GeoTIFF to EPSG:3857");

        // Step 3: Convert to 8-bit Byte format
        String[] translateCommand = {
                "gdal_translate",
                "-of", "VRT",
                "-ot", "Byte",
                "-scale",
                Utils.REPROJECTED_OUTPUT_DIR,
                Utils.BYTE_OUTPUT_DIR
        };
        Utils.runCommand(translateCommand, "Successfully converted merged GeoTIFF to 8-Bit Byte format",
                "Failed to convert merged GeoTIFF to 8-bit Byte format");

        // Step 4: Generate tiles with calculated zoom levels
        String[] tileCommand = {
                "gdal2tiles.py",
                "--processes=3",
                "-z", "0-" + maxZoom,
                "-p", "mercator",
                Utils.BYTE_OUTPUT_DIR,
                Utils.TILE_OUTPUT_DIR
        };
        Utils.runCommand(tileCommand, "Successfully generated tiles from merged GeoTIFF",
                "Failed to generate tiles from merged GeoTIFF");

        // Step 5: Clean up intermediate files
        // cleanupIntermediateFiles(Utils.REPROJECTED_OUTPUT_DIR, Utils.BYTE_OUTPUT_DIR);

        logger.info("Tiles generated successfully from merged GeoTIFF and saved to: {}", Utils.TILE_OUTPUT_DIR);
    }

    /**
     * Calculate the maximum zoom level to limit the number of tiles.
     * 
     * @param maxTiles Maximum number of tiles
     * @return Maximum zoom level
     */
    private int calculateMaxZoom(int maxTiles) {
        int zoom = 0;
        while (Math.pow(2, 2.0 * zoom) <= maxTiles) {
            zoom++;
        }
        return zoom - 1; // Return the previous zoom level where the tile count is below the limit
    }

    private List<String> fetchAllGeoTiffLinks() {

        List<String> geoTiffLinks = new ArrayList<>();
        try {

            // Define the initial request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("collections", new String[] { Utils.COLLECTION_ID });
            payload.put("datetime", "2023-06-01T00:00:00Z/2023-06-01T23:59:59Z");
            payload.put("limit", 500);
            payload.put("sortby", new Object[] { Map.of("field", "datetime", "direction", "asc") });
            payload.put("query", Map.of("platform", Map.of("eq", "terra")));

            String nextPageToken = null;
            do {
                // Add the token to the payload for subsequent pages
                if (nextPageToken != null) {
                    payload.put(Utils.TOKEN_KEY, nextPageToken);
                }

                // Set headers
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");

                // Create the request
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

                // Send POST request to fetch the GeoTIFF links
                String response = restTemplate.postForObject(Utils.PLANETARY_COMPUTER_SEARCH_URL, request,
                        String.class);

                // Parse the response
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(response);
                JsonNode features = rootNode.get("features");
                JsonNode links = rootNode.get("links");

                // Extract GeoTIFF links from the features
                if (features != null && features.isArray()) {
                    for (JsonNode feature : features) {
                        JsonNode assets = feature.get("assets");
                        if (assets != null && assets.has("500m_16_days_NDVI")) {
                            String href = assets.get("500m_16_days_NDVI").get("href").asText();
                            geoTiffLinks.add(href);
                        }
                    }
                }

                // Find the next page token
                nextPageToken = null;
                if (links != null && links.isArray()) {
                    for (JsonNode link : links) {
                        if ("next".equals(link.get("rel").asText())) {
                            JsonNode body = link.get("body");
                            if (body != null && body.has(Utils.TOKEN_KEY)) {
                                nextPageToken = body.get(Utils.TOKEN_KEY).asText();
                            }
                        }
                    }
                }
            } while (nextPageToken != null); // Continue while there are more pages

            return Utils.appendSASTokenToLinks(geoTiffLinks);
        } catch (Exception e) {
            logger.error("Error fetching GeoTIFF links", e);
        }
        return geoTiffLinks;
    }

    private void downloadGeoTiffs(List<String> geoTiffLinks) {
        List<File> downloadedFiles = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Future<File>> futures = new ArrayList<>();

        try {
            Files.createDirectories(Paths.get(Utils.TIFF_OUTPUT_DIR));

            for (String link : geoTiffLinks) {
                Future<File> future = executor.submit(() -> {
                    try {
                        String filePath = Utils.TIFF_OUTPUT_DIR + Utils.extractFileName(link);
                        File file = new File(filePath);

                        if (!file.exists()) {
                            try (InputStream in = new URL(link).openStream()) {
                                Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                        return file;
                    } catch (Exception e) {
                        logger.error("Failed to download GeoTIFF from link: {}", link, e);
                        return null;
                    }
                });
                futures.add(future);
            }

            for (Future<File> future : futures) {
                File file = future.get();
                if (file != null) {
                    downloadedFiles.add(file);
                }
            }

            logger.info("{} GeoTIFF files downloaded successfully", downloadedFiles.size());

        } catch (IOException e) {
            logger.error("Failed to create temporary directory: {}", Utils.TIFF_OUTPUT_DIR, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread was interrupted during parallel GeoTIFF downloads", e);
        } catch (ExecutionException e) {
            logger.error("Error during parallel GeoTIFF downloads", e);
        } finally {
            executor.shutdown();
        }
    }

    public static final List<String> fetchGeoTiffNames() {
        List<String> geoTiffFiles = new ArrayList<>();
        try {
            Files.walk(Paths.get(Utils.TIFF_OUTPUT_DIR))
                    .filter(Files::isRegularFile)
                    .forEach(file -> geoTiffFiles.add(file.toAbsolutePath().toString()));

        } catch (IOException e) {
            logger.error("Error fetching GeoTIFF files", e);
        }
        return geoTiffFiles;
    }

    public final String[] buildGdalCommand(List<String> geoTiffFiles) {
        String[] command = new String[geoTiffFiles.size() + 3];

        command[0] = "gdal_merge.py";
        command[1] = "-o";
        command[2] = Utils.MERGE_OUTPUT_DIR;
        for (int i = 0; i < geoTiffFiles.size(); i++) {
            logger.info("{} added to the merge", geoTiffFiles.get(i));
            command[i + 3] = geoTiffFiles.get(i);
        }

        return command;
    }

    private void cleanupIntermediateFiles(String... files) {
        for (String file : files) {
            try {
                Files.deleteIfExists(Paths.get(file));
            } catch (IOException e) {
                logger.error("Failed to delete intermediate file: {}", file);
            }
        }
    }
}
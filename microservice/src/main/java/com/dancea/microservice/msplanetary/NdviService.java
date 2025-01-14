package com.dancea.microservice.msplanetary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

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

import com.dancea.microservice.Utils;

@Service
public class NdviService {

    private static final Logger logger = LoggerFactory.getLogger(NdviService.class);

    private final RestTemplate restTemplate;

    private String sasToken = "";
    private long lastTokenRefreshTime;

    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void prepareProvider() {

        // Step 1: Download GeoTIFF files
        download();

        // Step 2: Merge GeoTIFF files
        merge();

        // Step 3: Generate tiles
        generateTiles();

        // Step 4: Cleanup intermediate files
        cleanupIntermediateFiles();
    }

    // Step 1
    private void download() {
        // Step 1: Fetch GeoTIFF links from the external source
        List<String> geoTiffLinks = fetchGeoTiffLinks();

        // Step 2: Download GeoTIFF files
        downloadGeoTiffs(geoTiffLinks);
    }

    private List<String> fetchGeoTiffLinks() {

        List<String> geoTiffLinks = new ArrayList<>();
        try {

            // Define the initial request payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("collections", new String[] { Utils.MSPLANETARY_COLLECTION_ID });
            payload.put("datetime", "2023-06-01T00:00:00Z/2023-06-01T23:59:59Z");
            payload.put("limit", 500);
            payload.put("sortby", new Object[] { Map.of("field", "datetime", "direction", "asc") });
            payload.put("query", Map.of("platform", Map.of("eq", "terra")));

            String nextPageToken = null;
            do {
                // Add the token to the payload for subsequent pages
                if (nextPageToken != null) {
                    payload.put(Utils.MSPLANETARY_TOKEN_KEY, nextPageToken);
                }

                // Set headers
                HttpHeaders headers = new HttpHeaders();
                headers.set("Content-Type", "application/json");

                // Create the request
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

                // Send POST request to fetch the GeoTIFF links
                String response = restTemplate.postForObject(Utils.MSPLANETARY_SEARCH_URL, request,
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
                            if (body != null && body.has(Utils.MSPLANETARY_TOKEN_KEY)) {
                                nextPageToken = body.get(Utils.MSPLANETARY_TOKEN_KEY).asText();
                            }
                        }
                    }
                }
            } while (nextPageToken != null); // Continue while there are more pages

            return appendSASTokenToLinks(geoTiffLinks);
        } catch (Exception e) {
            logger.error("Error fetching GeoTIFF links", e);
        } finally {
            logger.info("[01] Fetched {} GeoTIFF links", geoTiffLinks.size());
        }
        return geoTiffLinks;
    }

    private void downloadGeoTiffs(List<String> geoTiffLinks) {
        List<File> downloadedFiles = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(50);
        List<Future<File>> futures = new ArrayList<>();

        try {
            Files.createDirectories(Paths.get(Utils.MSPLANETARY_TIFF_DIR_PATH));

            for (String link : geoTiffLinks) {
                Future<File> future = executor.submit(() -> {
                    try {
                        String filePath = Utils.MSPLANETARY_TIFF_DIR_PATH + extractFileName(link);
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

        } catch (IOException e) {
            logger.error("Failed to create temporary directory: {}", Utils.MSPLANETARY_TIFF_DIR_PATH, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread was interrupted during parallel GeoTIFF downloads", e);
        } catch (ExecutionException e) {
            logger.error("Error during parallel GeoTIFF downloads", e);
        } finally {
            logger.info("[02] Downloaded {} GeoTIFF files", downloadedFiles.size());
            executor.shutdown();
        }
    }

    // Step 2
    private List<String> fetchGeoTiffPaths() {
        List<String> geoTiffFiles = new ArrayList<>();
        try {
            Files.walk(Paths.get(Utils.MSPLANETARY_TIFF_DIR_PATH))
                    .filter(Files::isRegularFile)
                    .forEach(file -> geoTiffFiles.add(file.toAbsolutePath().toString()));

        } catch (IOException e) {
            logger.error("Error fetching GeoTIFF files", e);
        } finally {
            logger.info("[03] Fetched {} GeoTIFF paths", geoTiffFiles.size());
        }
        return geoTiffFiles;
    }

    private void merge() {

        // Step 0: Skip merging if file already exists
        if (Utils.isFilePresent(Utils.MSPLANETARY_MERGED_FILE_PATH)) {
            logger.info("[04] Merged GeoTIFF files");
            return;
        }

        // Step 1: Fetch GeoTIFF files
        List<String> geoTiffFiles = fetchGeoTiffPaths();

        // Step 2: Build GDAL command
        String[] command = buildGdalCommand(geoTiffFiles);

        // Step 3: Run the command
        Utils.runArrayCommand(command, "", "Failed to merge GeoTIFF files");

        logger.info("[04] Merged GeoTIFF files");
    }

    private String[] buildGdalCommand(List<String> geoTiffFiles) {
        String[] command = new String[geoTiffFiles.size() + 3];

        command[0] = "gdal_merge.py";
        command[1] = "-o";
        command[2] = Utils.MSPLANETARY_MERGED_FILE_PATH;
        for (int i = 0; i < geoTiffFiles.size(); i++) {
            command[i + 3] = geoTiffFiles.get(i);
        }

        return command;
    }

    // Step 3
    private void generateTiles() {

        // Step 0: Skip generating tiles if files already exist
        if (Utils.isFilePresent(Utils.MSPLANETARY_REPROJECTED_FILE_PATH)
                && Utils.isFilePresent(Utils.MSPLANETARY_BYTE_FILE_PATH)
                && Utils.isFilePresent(Utils.MSPLANETARY_TILE_DIR_PATH)) {
            logger.info("[05] Generated tiles");
            return;
        }


        // Step 1: Calculate the maximum zoom level
        int maxZoom = calculateMaxZoom(294); // Limit to approximately 294 tiles

        // Step 2: Reproject to EPSG:3857
        String[] warpCommand = {
                "gdalwarp",
                "-t_srs", "EPSG:3857",
                Utils.MSPLANETARY_MERGED_FILE_PATH,
                Utils.MSPLANETARY_REPROJECTED_FILE_PATH
        };
        Utils.runArrayCommand(warpCommand, "",
                "Failed to reproject merged GeoTIFF to EPSG:3857");

        // Step 3: Convert to 8-bit Byte format
        String[] translateCommand = {
                "gdal_translate",
                "-of", "VRT",
                "-ot", "Byte",
                "-scale",
                Utils.MSPLANETARY_REPROJECTED_FILE_PATH,
                Utils.MSPLANETARY_BYTE_FILE_PATH
        };
        Utils.runArrayCommand(translateCommand, "",
                "Failed to convert merged GeoTIFF to 8-bit Byte format");

        // Step 4: Generate tiles with calculated zoom levels
        String[] tileCommand = {
                "gdal2tiles.py",
                "--processes=3",
                "-z", "0-" + maxZoom,
                "-p", "mercator",
                Utils.MSPLANETARY_BYTE_FILE_PATH,
                Utils.MSPLANETARY_TILE_DIR_PATH
        };
        Utils.runArrayCommand(tileCommand, "",
                "Failed to generate tiles from merged GeoTIFF");

        logger.info("[05] Generated tiles at zoom levels 0 to {}", maxZoom);
    }

    // Step 4
    private void cleanupIntermediateFiles() {
        Utils.deleteDirectory(Utils.MSPLANETARY_TIFF_DIR_PATH);
        Utils.deleteFiles(new String[] { Utils.MSPLANETARY_MERGED_FILE_PATH, Utils.MSPLANETARY_REPROJECTED_FILE_PATH,
                Utils.MSPLANETARY_BYTE_FILE_PATH });
    }

    // Helper methods
    private String extractSASToken(String geoTiffUrl) {
        // Only extract a new SAS token if the current one is empty or has expired
        if (sasToken == null || sasToken.isEmpty() || System.currentTimeMillis() - lastTokenRefreshTime >= 3600000) {

            ObjectMapper objectMapper = new ObjectMapper();

            lastTokenRefreshTime = System.currentTimeMillis();
            String signedGeoTiffLink = "";
            String signRequestUrl = Utils.MSPLANETARY_SIGN_URL + "?href=" + geoTiffUrl;

            try {
                ResponseEntity<String> signResponse = restTemplate.getForEntity(signRequestUrl, String.class);
                JsonNode rootNode = objectMapper.readTree(signResponse.getBody());
                signedGeoTiffLink = rootNode.get("href").asText();

                // Extract SAS token from the signed URL
                sasToken = signedGeoTiffLink.split("\\?")[1];

            } catch (Exception e) {
                sasToken = null; // Reset SAS token to ensure reattempt later
            }
        }
        return sasToken;
    }

    private List<String> appendSASTokenToLinks(List<String> links) {
        List<String> signedLinks = new ArrayList<>();
        String currentToken = extractSASToken(links.get(0));
        for (String link : links) {
            if (currentToken != null && !currentToken.isEmpty()) {
                signedLinks.add(link + "?" + currentToken);
            }
        }

        return signedLinks;
    }

    private String extractFileName(String link) {
        String noQuery = link.substring(0, link.indexOf('?'));
        return noQuery.substring(noQuery.lastIndexOf("/") + 1);
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

}
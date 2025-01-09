package com.dancea.microservice.planet;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PlanetService {

    private static final Logger logger = LoggerFactory.getLogger(PlanetService.class);

    private final RestTemplate restTemplate;

    public PlanetService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch all GeoTIFF files and save them locally.
     */
    public void downloadGeoTiffs() {

        // Step 1: Fetch Assets links from the external source
        List<String> assetsLinks = fetchAssetsLinks();

        PlanetUtils.writeArrayToFile(PlanetUtils.PLANET_QUICKSEARCH_ASSETS_FILE_PATH, assetsLinks);

        // Step 2: Activate the assets if needed
        // activateAssets(assetsLinks);

        // Step 3: Fetch GeoTIFF links from assets
        List<String> geoTiffLinks = fetchGeoTiffLinks(assetsLinks);

        PlanetUtils.writeArrayToFile(PlanetUtils.PLANET_GEOTIFF_LINKS_FILE_PATH, geoTiffLinks);

        // Step 4: Download GeoTIFF files
        downloadGeoTiffs(geoTiffLinks);
    }

    /**
     * 
     * Divide all GeoTIFF files in the directory.
     */
    public void divideGeoTiffs() {
        // Step 1: Fetch all GeoTIFF files
        List<String> geoTiffFiles = PlanetUtils.fetchFilePathsInDirectory(PlanetUtils.PLANET_GEOTIFF_FILE_PATH, "tif");

        // Step 2: Build GDAL Commands
        String[] commands = gdalRetile(geoTiffFiles);

        // Step 3: Execute commands using a thread pool
        ExecutorService executor = Executors.newFixedThreadPool(5); // 5 threads for parallel execution

        List<Future<?>> futures = new ArrayList<>();
        for (String command : commands) {
            futures.add(executor.submit(() -> {
                try {
                    Process process = Runtime.getRuntime().exec(command);
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        logger.info("Command executed successfully: {}", command);
                    } else {
                        logger.error("Command failed with exit code {}: {}", exitCode, command);
                    }
                } catch (Exception e) {
                    logger.error("Failed to execute command: {}", e.getMessage());
                }
            }));
        }

        // Wait for all tasks to complete
        for (Future<?> future : futures) {
            try {
                future.get(); // Wait for each task to complete
            } catch (Exception e) {
                logger.error("Error waiting for command execution: {}", e.getMessage());
            }
        }

        executor.shutdown(); // Shutdown the thread pool
        logger.info("All GeoTIFF files divided successfully.");
    }

    /**
     * Merge all GeoTIFF files in the directory.
     */
    public void mergeGeoTiffs() {
        // Step 1: Fetch all GeoTIFF files
        List<String> geoTiffFiles = PlanetUtils.fetchFilePathsInDirectory(PlanetUtils.PLANET_GEOTIFF_SPLIT_FILE_PATH,
                "tif");

        // Step 2: Build GDAL Command
        String[] command = gdalMerge(geoTiffFiles);

        // Step 3: Execute the command
        PlanetUtils.runSingleGdalCommand(command, "Successfully merged GeoTIFF files",
                "Failed to merge GeoTIFF files");

    }

    private String[] gdalMerge(List<String> geoTiffFiles) {
        String[] command = new String[geoTiffFiles.size() + 3];

        command[0] = "gdal_merge.py";
        command[1] = "-o";
        command[2] = PlanetUtils.PLANET_GEOTIFF_MERGED_FILE_PATH;
        for (int i = 0; i < geoTiffFiles.size(); i++) {
            logger.info("{} added to the merge", geoTiffFiles.get(i));
            command[i + 3] = geoTiffFiles.get(i);
        }

        return command;
    }

    private String[] gdalRetile(List<String> geoTiffFiles) {
        // Commands for splitting the GeoTIFF filess
        List<String> commands = new ArrayList<>();

        for (String geoTiffFile : geoTiffFiles) {

            // Create the command for gdal_retile.py
            String command = String.format(
                    "gdal_retile.py -targetDir %s -ps 2048 2048 -co COMPRESS=DEFLATE -co TILED=YES -co BIGTIFF=YES %s",
                    PlanetUtils.PLANET_GEOTIFF_SPLIT_FILE_PATH, geoTiffFile);

            commands.add(command);
        }

        return commands.toArray(new String[0]);
    }

    private String[] buildCompressionGdalCommands(List<String> geoTiffFiles) {
        // Commands for splitting the GeoTIFF files
        List<String> commands = new ArrayList<>();

        for (String geoTiffFile : geoTiffFiles) {
            // Define the output directory for the split files
            String outputDir = geoTiffFile.replace("/geotiffs", "/geotiffs-split");

            // Create the command for gdal_retile.py
            String command = String.format(
                    "gdal_retile.py -targetDir %s -ps 512 512 -co COMPRESS=JPEG -co TILED=YES -co BIGTIFF=YES %s",
                    outputDir, geoTiffFile);

            commands.add(command);
        }

        return commands.toArray(new String[0]);
    }

    private List<String> fetchGeoTiffLinks(List<String> assetLinks) {

        logger.info("Fetching GeoTIFF links from assets...");

        List<String> geoTiffLinks = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        ExecutorService executor = Executors.newFixedThreadPool(3); // Create a thread pool with 10 threads
        List<Future<String>> futures = new ArrayList<>();

        for (String assetUrl : assetLinks) {
            // Submit tasks to the executor
            futures.add(executor.submit(() -> {
                try {
                    // Fetch asset details
                    HttpEntity<String> assetRequest = new HttpEntity<>(PlanetUtils.getHttpHeaders());
                    String response = restTemplate.exchange(assetUrl, HttpMethod.GET, assetRequest, String.class)
                            .getBody();

                    // Parse the response
                    JsonNode rootNode = mapper.readTree(response);
                    JsonNode asset = rootNode.get("ortho_analytic_4b_sr");
                    if (asset != null && asset.has("status") && "active".equals(asset.get("status").asText())) {
                        // Return the download link
                        return asset.get("location").asText();
                    } else {
                        logger.warn("Asset is not active or download link is missing for: {}", assetUrl);
                    }
                } catch (Exception e) {
                    logger.error("Failed to fetch asset details for {}: {}", assetUrl, e.getMessage());
                }
                return null; // Return null if the fetch failed or the asset is not active
            }));
        }

        // Collect results
        for (Future<String> future : futures) {
            try {
                String link = future.get(); // Wait for each task to complete
                if (link != null) {
                    geoTiffLinks.add(link); // Add valid links to the list
                }
            } catch (Exception e) {
                logger.error("Error processing asset: {}", e.getMessage());
            }
        }

        executor.shutdown(); // Gracefully shut down the thread pool

        logger.info("Fetched {} GeoTIFF links from assets.", geoTiffLinks.size());
        return geoTiffLinks;
    }

    private List<String> fetchAssetsLinks() {
        logger.info("Fetching assets links from the external source...");

        List<String> assetLinks = new ArrayList<>();
        String nextUrl = PlanetUtils.PLANET_API_URL;
        Map<String, Object> payload = createSearchPayload();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, PlanetUtils.getHttpHeaders());

        try {
            do {

                // Send POST request to fetch the GeoTIFF links
                String response = restTemplate.postForObject(nextUrl, request, String.class);

                // Parse the response
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response);
                PlanetUtils.writeJsonToFile(response, PlanetUtils.PLANET_QUICKSEARCH_FILE_PATH);
                JsonNode features = rootNode.get("features");

                if (features != null && features.isArray()) {
                    for (JsonNode feature : features) {
                        // Get the _links node inside the current feature
                        JsonNode links = feature.get("_links");

                        // Extract the 'assets' link if present
                        String assetsLink = links != null && links.has("assets") ? links.get("assets").asText() : null;

                        if (assetsLink != null)
                            assetLinks.add(assetsLink);
                        else
                            logger.warn("No assets found in the feature.");
                    }
                } else {
                    logger.warn("No features found in the response.");
                }

                // Handle Pagination
                JsonNode links = rootNode.get("_links");
                nextUrl = links != null && links.has("next") ? links.get("next").asText() : null;

            } while (nextUrl != null);

            logger.info("Fetched {} GeoTIFF links from the external source.", assetLinks.size());

        } catch (Exception e) {
            logger.error("Failed to fetch GeoTIFF links from the external source: {}", e.getMessage());
        }

        return assetLinks;
    }

    private void activateAssets(List<String> assetsLinks) {

        logger.info("Activating assets...");

        Map<String, String> downloadLinks = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        Map<String, Integer> assets = new HashMap<>();
        assets.put("inactive", 0);
        assets.put("activating", 0);
        assets.put("active", 0);

        for (String assetUrl : assetsLinks) {
            try {
                // Create the request
                HttpEntity<String> assetRequest = new HttpEntity<>(PlanetUtils.getHttpHeaders());

                // Send GET request to fetch the asset
                String response = restTemplate.exchange(assetUrl, HttpMethod.GET, assetRequest, String.class)
                        .getBody();

                // Parse the response
                JsonNode rootNode = mapper.readTree(response);
                JsonNode asset = rootNode.get("ortho_analytic_4b_sr");

                boolean isInactive = asset != null && asset.has("status")
                        && asset.get("status").asText().equals("inactive");

                if (isInactive) {

                    // Get the asset activation link
                    String assetActivationLink = asset.get("_links").get("activate").asText();

                    // Create the request
                    HttpEntity<String> activationRequest = new HttpEntity<>("{}", PlanetUtils.getHttpHeaders());

                    // Send POST request to activate the asset
                    restTemplate
                            .exchange(assetActivationLink, HttpMethod.POST, activationRequest, String.class)
                            .getBody();

                    assets.put("inactive", assets.get("inactive") + 1);
                }

                boolean isActivating = asset != null && asset.has("status")
                        && asset.get("status").asText().equals("activating");
                if (isActivating) {
                    assets.put("activating", assets.get("activating") + 1);
                }

                boolean isActive = asset != null && asset.has("status")
                        && asset.get("status").asText().equals("active");
                if (isActive) {
                    assets.put("active", assets.get("active") + 1);
                }

            } catch (Exception e) {
                logger.error("Failed to activate asset: {}", e.getMessage());
            }
        }
        logger.info("Inactive assets: {}\nActivating assets: {}\nActive assets: {}",
                assets.get("inactive"), assets.get("activating"), assets.get("active"));
    }

    private Map<String, Object> createSearchPayload() {
        Map<String, Object> payload = new HashMap<>();

        // Item types
        payload.put("item_types", new String[] { "PSScene" });

        // Filters
        Map<String, Object> filters = new HashMap<>();
        filters.put("type", "AndFilter");

        // Filter configuration
        List<Map<String, Object>> config = new ArrayList<>();

        // Date range filter
        Map<String, Object> dateRangeFilter = new HashMap<>();
        dateRangeFilter.put("type", "DateRangeFilter");
        dateRangeFilter.put("field_name", "acquired");
        dateRangeFilter.put("config", Map.of(
                "gte", "2023-01-01T00:00:00Z",
                "lte", "2023-12-31T23:59:59Z"));
        config.add(dateRangeFilter);

        // Add configuration to filters
        filters.put("config", config);

        // Add filters to payload
        payload.put("filter", filters);

        return payload;
    }

    private void downloadGeoTiffs(List<String> geoTiffLinks) {
        // List to hold all threads
        List<Thread> threads = new ArrayList<>();

        for (String geoTiffLink : geoTiffLinks) {
            threads.add(new Thread(() -> {
                try {
                    // Download the file
                    HttpHeaders headers = new HttpHeaders();
                    HttpEntity<String> downloadRequest = new HttpEntity<>(headers);

                    ResponseEntity<byte[]> downloadResponse = restTemplate.exchange(
                            geoTiffLink, HttpMethod.GET, downloadRequest, byte[].class);

                    // Extract the file name from the Content-Disposition header
                    String contentDisposition = downloadResponse.getHeaders().getFirst("Content-Disposition");
                    String fileName = "default_filename.tif"; // Fallback filename
                    if (contentDisposition != null && contentDisposition.contains("filename=")) {
                        fileName = contentDisposition.split("filename=")[1].replace("\"", "").trim();
                    }

                    // Create directory if not present
                    Path directoryPath = Paths.get(PlanetUtils.PLANET_GEOTIFF_FILE_PATH);
                    if (!Files.exists(directoryPath)) {
                        Files.createDirectories(directoryPath);
                    }

                    // Save the file locally
                    Path filePath = Paths.get(PlanetUtils.PLANET_GEOTIFF_FILE_PATH, fileName);
                    try (InputStream in = new ByteArrayInputStream(downloadResponse.getBody());
                            OutputStream out = Files.newOutputStream(filePath)) {
                        in.transferTo(out);
                    }
                    ;

                    logger.info("Downloaded and saved asset to: {}", filePath.toAbsolutePath());
                } catch (Exception e) {
                    logger.error("Failed to fetch asset details: {}", e.getMessage());
                }
            }));
        }

        // Start threads in batches of 5
        for (int i = 0; i < threads.size(); i++) {
            threads.get(i).start();
            if ((i + 1) % 5 == 0) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    logger.error("Thread sleep interrupted: {}", e.getMessage());
                }
            }
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                logger.error("Thread join interrupted: {}", e.getMessage());
            }
        }

        logger.info("All GeoTIFF files downloaded successfully.");
    }

}

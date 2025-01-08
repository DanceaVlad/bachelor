package com.dancea.microservice.planet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpMethod;
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

        // Step 2: Activate the assets if needed
        activateAssets(assetsLinks);

        // Step 3: Download GeoTIFF files
        downloadGeoTiffs(assetsLinks);
    }

    private void activateAssets(List<String> assetsLinks) {

        Map<String, String> downloadLinks = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        for (String assetUrl : assetsLinks) {
            executorService.submit(() -> {
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
                        String activateResponse = restTemplate
                                .exchange(assetActivationLink, HttpMethod.POST, activationRequest, String.class)
                                .getBody();

                        // Parse the response
                        JsonNode activateRootNode = mapper.readTree(activateResponse);
                        JsonNode location = activateRootNode.get("location");

                        if (location != null) {
                            downloadLinks.put(assetUrl, location.asText());
                        } else {
                            logger.error("Failed to activate asset: {}", activateResponse);
                        }
                    }

                    boolean isActivating = asset != null && asset.has("status")
                            && asset.get("status").asText().equals("activating");
                    if (isActivating) {
                        logger.info("Asset is activating. Please wait...");
                    }

                    boolean isActive = asset != null && asset.has("status")
                            && asset.get("status").asText().equals("active");
                    if (isActive) {
                        logger.info("Asset is already active.");
                    }

                } catch (Exception e) {
                    logger.error("Failed to activate asset: {}", e.getMessage());
                }
            });
        }
    }

    private List<String> fetchAssetsLinks() {
        List<String> geoTiffLinks = new ArrayList<>();
        String nextUrl = PlanetUtils.PLANET_API_URL;

        try {
            do {
                // Define Payload
                Map<String, Object> payload = createSearchPayload();

                // Create the request
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, PlanetUtils.getHttpHeaders());

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
                            geoTiffLinks.add(assetsLink);
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

            logger.info("Fetched {} GeoTIFF links from the external source.", geoTiffLinks.size());

        } catch (Exception e) {
            logger.error("Failed to fetch GeoTIFF links from the external source: {}", e.getMessage());
        }

        return geoTiffLinks;
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
        logger.info("Downloading {} GeoTIFF files...", geoTiffLinks.size());
    }
}

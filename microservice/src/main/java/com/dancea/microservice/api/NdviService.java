package com.dancea.microservice.api;

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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NdviService {

    private static final Logger logger = LoggerFactory.getLogger(NdviService.class);

    private static final String COLLECTION_ID = "modis-13A1-061";
    private static final String SIGN_API_URL = "https://planetarycomputer.microsoft.com/api/sas/v1/sign";
    private static final String SIGNED_GEOTIFF_LINKS_PATH = "microservice/src/main/resources/static/signedGeoTiffLinks.txt";
    private static final String GEOTIFF_LINKS_PATH = "microservice/src/main/resources/static/geoTiffLinks.txt";
    private static final String PREVIEW_LINKS_PATH = "microservice/src/main/resources/static/previewLinks.txt";

    private final RestTemplate restTemplate;

    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String fetchNdviData(double[] boundingBox) {
        if (boundingBox.length != 4) {
            throw new IllegalArgumentException("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
        }

        String response = requestCollection(boundingBox);

        List<String> signedGeoTiffLinks = extractSignedGeoTiffLinks(response);

        writeDataToFile(SIGNED_GEOTIFF_LINKS_PATH, signedGeoTiffLinks);
    
        

        // Return the response body
        return response;
    }

    private List<String> extractSignedGeoTiffLinks(String data) {

        // Extract GeoTIFF links from the response
        List<String> geoTiffLinks = extractGeoTiffLinks(data);

        // Extract SAS token from the Planetary Computer API based on the first GeoTIFF link
        String sasToken = extractSASToken(geoTiffLinks.get(0));

        // Append the SAS token to the GeoTIFF links
        List<String> signedGeoTiffLinks = new ArrayList<>();
        for (String geoTiffLink : geoTiffLinks) {
            signedGeoTiffLinks.add(geoTiffLink + "?" + sasToken);
        }

        return signedGeoTiffLinks;
    }

    private String requestCollection(double[] boundingBox) {
        // Create the request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("collections", new String[]{COLLECTION_ID});
        payload.put("bbox", boundingBox);
        payload.put("datetime", "2023-06-01T00:00:00Z/2023-06-01T23:59:59Z");
        payload.put("limit", 100);
        payload.put("sortby", new Object[]{Map.of("field", "datetime", "direction", "asc")});
        payload.put("query", Map.of("platform", Map.of("eq", "terra")));

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Create the request
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        // Send the request
        String url = "https://planetarycomputer.microsoft.com/api/stac/v1/search";
        return restTemplate.postForEntity(url, request, String.class).getBody();
    }

    private void writeDataToFile(String path, Object data) {
        try {
            File file = new File(path);
            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            FileWriter writer = new FileWriter(file);
            if(data instanceof List) {
                for (Object item : (List<?>) data) {
                    writer.write(item.toString() + "\n");
                }
            } else {
                writer.write(data.toString());
            }
            writer.close();
        } catch (IOException e) {
            logger.error("An error occurred while writing to the file: {}", path, e);
        }
    }

    private List<String> extractPreviewLinks(String data) {

        List<String> previewLinks = new ArrayList<>();

        // capture link whenever ""preview","href":"https:" is found
        while (data.contains("\"preview\",\"href\":\"https:")) {
            int index = data.indexOf("\"preview\",\"href\":\"https:");
            data = data.substring(index + 18);
            int endIndex = data.indexOf("\"");
            String link = data.substring(0, endIndex);
            previewLinks.add(link);
        }
        return previewLinks;
    }

    private List<String> extractGeoTiffLinks(String data) {
        List<String> geoTiffLinks = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode rootNode = objectMapper.readTree(data);
            JsonNode features = rootNode.get("features");

            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode assets = feature.get("assets");

                    if (assets != null) {
                        for (String assetName : List.of(
                                "500m_16_days_NDVI",
                                "500m_16_days_EVI",
                                "500m_16_days_VI_Quality",
                                "500m_16_days_MIR_reflectance")) {

                            JsonNode asset = assets.get(assetName);
                            if (asset != null && asset.has("href")) {
                                geoTiffLinks.add(asset.get("href").asText());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing GeoTIFF links from response", e);
        }

        return geoTiffLinks;
    }

    private String extractSASToken(String geoTiffUrl) {
        String signedGeoTiffLink = ""; 
        ObjectMapper objectMapper = new ObjectMapper();

        String signRequestUrl = SIGN_API_URL + "?href=" + geoTiffUrl;

        ResponseEntity<String> signResponse = restTemplate.getForEntity(signRequestUrl, String.class);
        try {
            JsonNode rootNode = objectMapper.readTree(signResponse.getBody());
            signedGeoTiffLink = rootNode.get("href").asText();
        } catch (Exception e) {
            logger.error("Error parsing SignedGeoTIFF link from response", e);
        }

        return signedGeoTiffLink.split("\\?")[1];
    }
}
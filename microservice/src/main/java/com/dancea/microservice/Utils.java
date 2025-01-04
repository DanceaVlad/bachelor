package com.dancea.microservice;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Utils {

    private static final String PLANETARY_COMPUTER_SIGN_URL = "https://planetarycomputer.microsoft.com/api/sas/v1/sign";
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    private static String sasToken = "";
    private static long lastTokenRefreshTime;

    public static final List<String> appendSASTokenToLinks(List<String> links) {
        List<String> signedLinks = new ArrayList<>();
        String currentToken = extractSASToken(links.get(0));
        for (String link : links) {
            if (currentToken != null && !currentToken.isEmpty()) {
                signedLinks.add(link + "?" + currentToken);
            }
        }

        return signedLinks;
    }

    private static final String extractSASToken(String geoTiffUrl) {
        // Only extract a new SAS token if the current one is empty or has expired
        if (sasToken == null || sasToken.isEmpty() || System.currentTimeMillis() - lastTokenRefreshTime >= 3600000) {
            lastTokenRefreshTime = System.currentTimeMillis();
            String signedGeoTiffLink = "";
            ObjectMapper objectMapper = new ObjectMapper();
    
            String signRequestUrl = PLANETARY_COMPUTER_SIGN_URL + "?href=" + geoTiffUrl;
    
            try {
                ResponseEntity<String> signResponse = REST_TEMPLATE.getForEntity(signRequestUrl, String.class);
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
}

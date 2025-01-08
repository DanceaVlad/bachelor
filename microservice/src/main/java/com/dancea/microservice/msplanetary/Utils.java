package com.dancea.microservice.msplanetary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {

    // Private constructor to hide the implicit public one
    private Utils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String COLLECTION_ID = "modis-13A1-061";
    public static final String PLANETARY_COMPUTER_SIGN_URL = "https://planetarycomputer.microsoft.com/api/sas/v1/sign";
    public static final String PLANETARY_COMPUTER_SEARCH_URL = "https://planetarycomputer.microsoft.com/api/stac/v1/search";
    public static final String TIFF_OUTPUT_DIR = "microservice/src/main/resources/01_tiffs/";
    public static final String MERGE_OUTPUT_DIR = "microservice/src/main/resources/02_merged.tif";
    public static final String REPROJECTED_OUTPUT_DIR = "microservice/src/main/resources/03_reprojected.tif";
    public static final String BYTE_OUTPUT_DIR = "microservice/src/main/resources/04_byte.vrt";
    public static final String TILE_OUTPUT_DIR = "microservice/src/main/resources/05_tiles/";
    public static final String TOKEN_KEY = "token";

    private static final Logger logger = LoggerFactory.getLogger(Utils.class);
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

    public static void runCommand(String[] command, String successMessage, String errorMessage) {
        try {
            Process process = new ProcessBuilder(command).inheritIO().start();
            if (process.waitFor() == 0) {
                logger.info("{}", successMessage);
            } else {
                logger.error("{}", errorMessage);
            }
        } catch (IOException e) {
            logger.error("Error running command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Error running command", e);
        }
    }

    // Run command and return output
    public static String runCommand(String[] command) {
        try {
            Process process = new ProcessBuilder(command).start();
            process.waitFor();
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException e) {
            logger.error("Error running command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Error running command", e);
        }
        return "";
    }

    public static String extractFileName(String link) {
        String noQuery = link.substring(0, link.indexOf('?'));
        return noQuery.substring(noQuery.lastIndexOf("/") + 1);
    } 
}

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
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpiRegistry;
import org.esa.snap.runtime.Engine;


@Service
public class NdviService {

    private static final Logger logger = LoggerFactory.getLogger(NdviService.class);

    private static final String COLLECTION_ID = "modis-13A1-061";
    private static final String SIGN_API_URL = "https://planetarycomputer.microsoft.com/api/sas/v1/sign";
    private static final String SIGNED_GEOTIFF_LINKS_PATH = "microservice/src/main/resources/static/signedGeoTiffLinks.txt";
    private static final String GEOTIFF_LINKS_PATH = "microservice/src/main/resources/static/geoTiffLinks.txt";
    private static final String PREVIEW_LINKS_PATH = "microservice/src/main/resources/static/previewLinks.txt";
    private static final String TEMP_DIR = "microservice/src/main/resources/temp/";
    private long lastTokenRefreshTime;
    private String sasToken = "";

    static {
        Engine.start();
    }

    private final RestTemplate restTemplate;

    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getResponseBody(double[] boundingBox) {
        if (boundingBox.length != 4) {
            logger.error("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
            throw new IllegalArgumentException("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
        }

        String response = requestCollection(boundingBox);
        return response;
    }

    public List<File> getNdviFiles(double[] boundingBox) {
        if (boundingBox.length != 4) {
            logger.error("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
            throw new IllegalArgumentException("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
        }

        // Step 1: Request data from the collection
        String response = requestCollection(boundingBox);

        // Step 2: Extract signed GeoTIFF links
        List<String> signedGeoTiffLinks = extractSignedGeoTiffLinks(response);

        // Step 3: Filter for NDVI-specific GeoTIFF links
        List<String> ndviGeoTiffLinks = extractNdviFromSignedGeoTiffLinks(signedGeoTiffLinks);

        // Step 4: Download the required GeoTIFF files
        List<File> neededGeoTiffs = downloadGeoTiffs(ndviGeoTiffLinks);

        // Step 5: Return the downloaded files
        return neededGeoTiffs;
    }

    private String extractFileName(String link) {
        String removedToken = link.substring('0', link.indexOf('?'));
        return removedToken.substring(removedToken.lastIndexOf("/") + 1);

    }

    private File processWithSnap(List<File> geoTiffFiles, double[] boundingBox) {
        File outputFile = new File(TEMP_DIR + "processed_output.tif");
    
        try {
            // Load products into SNAP
            List<Product> products = new ArrayList<>();
            for (File file : geoTiffFiles) {
                Product product = ProductIO.readProduct(file.getPath());
                if (product != null) {
                    products.add(product);
                    logger.info("Loaded product from file: {}", file.getPath());
                } else {
                    logger.error("Failed to read product from file: {}", file.getPath());
                }
            }
    
            if (products.isEmpty()) {
                logger.error("No valid products were loaded for processing.");
                return outputFile;
            }
    
            // Combine products using the Mosaic operator
            Map<String, Object> mosaicParameters = new HashMap<>();
            mosaicParameters.put("sourceBands", "NDVI"); // Process NDVI band
            mosaicParameters.put("pixelSize", "463.312716527"); // Match the original resolution of the file
    
            Product mosaicProduct = GPF.createProduct("Mosaic", mosaicParameters, products.toArray(new Product[0]));
            if (mosaicProduct == null) {
                logger.error("Mosaic operation failed.");
                return outputFile;
            }
    
            logger.info("Mosaic operation successful.");
    
            // Crop the mosaic using the Subset operator and bounding box
            Map<String, Object> subsetParameters = new HashMap<>();
            String geoRegion = String.format(
                "POLYGON((%f %f, %f %f, %f %f, %f %f, %f %f))",
                boundingBox[0], boundingBox[1], // minLon, minLat
                boundingBox[2], boundingBox[1], // maxLon, minLat
                boundingBox[2], boundingBox[3], // maxLon, maxLat
                boundingBox[0], boundingBox[3], // minLon, maxLat
                boundingBox[0], boundingBox[1]  // Close the polygon
            );
            subsetParameters.put("geoRegion", geoRegion);
    
            Product subsetProduct = GPF.createProduct("Subset", subsetParameters, mosaicProduct);
            if (subsetProduct == null) {
                logger.error("Subset operation failed.");
                return outputFile;
            }
    
            logger.info("Subset operation successful.");
    
            // Write the processed product to a GeoTIFF file
            ProductIO.writeProduct(subsetProduct, outputFile.getAbsolutePath(), "GeoTIFF");
            logger.info("Processed file saved at: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("An error occurred while processing the GeoTIFF files: {}", e.getMessage(), e);
        }
    
        return outputFile;
    }

    private List<String> extractNewGeoTiffLinks(List<String> ndviGeoTiffLinks) {
        List<String> newGeoTiffLinks = new ArrayList<>();
        
        try {
            // Ensure the temp directory exists
            Files.createDirectories(Paths.get(TEMP_DIR));
        } catch (IOException e) {
            logger.error("Failed to create temporary directory: {}", TEMP_DIR, e);
            return ndviGeoTiffLinks;
        }

        for (String link : ndviGeoTiffLinks) {
            try {
                String filePath = TEMP_DIR + File.separator + extractFileName(link);

                // check if file already exists
                File file = new File(filePath);
                if (!file.exists()) {
                    newGeoTiffLinks.add(link);
                }
            } catch (Exception e) {
                logger.error("Failed to download: {}", link, e);
            }
        }
        return newGeoTiffLinks;
    }

    private List<File> downloadGeoTiffs(List<String> geoTiffLinks) {
        List<File> downloadedFiles = new ArrayList<>();
        
        try {
            // Ensure the temp directory exists
            Files.createDirectories(Paths.get(TEMP_DIR));
        } catch (IOException e) {
            logger.error("Failed to create temporary directory: {}", TEMP_DIR, e);
            return downloadedFiles; // Return an empty list if directory creation fails
        }
        
        for (String link : geoTiffLinks) {
            try {
    
                String filePath = TEMP_DIR + File.separator + extractFileName(link);
    
                // Create necessary parent directories for the file
                File file = new File(filePath);
                if (!file.getParentFile().exists()) {
                    Files.createDirectories(file.getParentFile().toPath());
                }
    
                // Check if the file already exists
                if (file.exists()) {
                    downloadedFiles.add(file); // Add existing file to the list
                    continue;
                }
    
                // Download the file if it doesn't exist
                try (InputStream in = new URL(link).openStream()) {
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    downloadedFiles.add(file);
                }
            } catch (Exception e) {
                logger.error("Failed to download: {}", link, e);
            }
        }
        
        return downloadedFiles;
    }

    private List<String> extractNdviFromSignedGeoTiffLinks(List<String> originalLinks) {
        List<String> ndviLinks = new ArrayList<>();
        for (String link : originalLinks) {
            if(link.contains("NDVI")) {
                ndviLinks.add(link);
            }
        }
        return ndviLinks;
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
        // Only extract a new SAS token if the current one is empty or has expired
        if (sasToken == null || sasToken.isEmpty() || System.currentTimeMillis() - lastTokenRefreshTime >= 3600000) {
            lastTokenRefreshTime = System.currentTimeMillis();
            String signedGeoTiffLink = "";
            ObjectMapper objectMapper = new ObjectMapper();
    
            String signRequestUrl = SIGN_API_URL + "?href=" + geoTiffUrl;
    
            try {
                ResponseEntity<String> signResponse = restTemplate.getForEntity(signRequestUrl, String.class);
                JsonNode rootNode = objectMapper.readTree(signResponse.getBody());
                signedGeoTiffLink = rootNode.get("href").asText();
    
                // Extract SAS token from the signed URL
                sasToken = signedGeoTiffLink.split("\\?")[1];
            } catch (Exception e) {
                logger.error("Error parsing SignedGeoTIFF link from response", e);
                sasToken = null; // Reset SAS token to ensure reattempt later
            }
        }
        return sasToken;
    }
}
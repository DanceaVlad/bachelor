package com.dancea.microservice.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class NdviService {

    private static final Logger logger = LoggerFactory.getLogger(NdviService.class);

    private final RestTemplate restTemplate;

    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String fetchNdviData(double[] boundingBox) {
        if (boundingBox.length != 4) {
            throw new IllegalArgumentException("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
        }

        // Create the request payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("collections", new String[]{"modis-13A1-061"});
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
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Write the response to a file
        writeDataToFile(response.getBody());

        // Return the response body
        return response.getBody();
    }

    private void writeDataToFile(String data) {
        try {
            String responsePath = "microservice/src/main/resources/static/response.txt";
            File responseFile = new File(responsePath);
            File responseParentDir = responseFile.getParentFile();
            if (!responseParentDir.exists()) {
                responseParentDir.mkdirs();
            }
            FileWriter responseWriter = new FileWriter(responseFile);
            responseWriter.write(data);
            responseWriter.close();

            String previewsPath = "microservice/src/main/resources/static/previews.txt";
            File previewsFile = new File(previewsPath);
            File previewsParentDir = previewsFile.getParentFile();
            if (!previewsParentDir.exists()) {
                previewsParentDir.mkdirs();
            }
            FileWriter previewsWriter = new FileWriter(previewsFile);
            // capture link whenever ""preview","href":"https:" is found
            while (data.contains("\"preview\",\"href\":\"https:")) {
                int index = data.indexOf("\"preview\",\"href\":\"https:");
                data = data.substring(index + 18);
                int endIndex = data.indexOf("\"");
                String link = data.substring(0, endIndex);
                previewsWriter.write(link + "\n");
            }
            previewsWriter.close();
            logger.info("Successfully wrote to the file {}", System.getProperty("user.dir"));
        } catch (IOException e) {
            logger.error("An error occurred while writing to the file.", e);
        }
    }
}
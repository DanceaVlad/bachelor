package com.dancea.microservice.api;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class NdviService {

    private final RestTemplate restTemplate;

    public NdviService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String fetchNdviData(double[] boundingBox) {
        if (boundingBox.length != 4) {
            throw new IllegalArgumentException("Bounding box must contain exactly 4 values: [minLon, minLat, maxLon, maxLat]");
        }

        // Create the request payload
        String payload = String.format(
            "{ \"collections\": [\"modis-13A1-061\"], \"geometry\": { \"type\": \"Polygon\", \"coordinates\": [[[ %f, %f ], [ %f, %f ], [ %f, %f ], [ %f, %f ], [ %f, %f ]]] }, \"datetime\": \"2023-01-01T00:00:00Z/2023-12-31T23:59:59Z\", \"limit\": 1 }",
            boundingBox[0], boundingBox[1],  // Bottom-left
            boundingBox[2], boundingBox[1],  // Bottom-right
            boundingBox[2], boundingBox[3],  // Top-right
            boundingBox[0], boundingBox[3],  // Top-left
            boundingBox[0], boundingBox[1]   // Close polygon
        );

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        // Create the request
        HttpEntity<String> request = new HttpEntity<>(payload, headers);

        // Send the request
        String url = "https://planetarycomputer.microsoft.com/api/stac/v1/search";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Return the response body
        for (double d : boundingBox) {
            System.out.println(d);
        }
        return response.getBody();
    }
}
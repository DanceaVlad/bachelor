package com.dancea.microservice.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class NdviController {

    private final NdviService ndviService;

    /**
     * Endpoint to fetch NDVI data using a bounding box.
     * 
     * @param bbox A bounding box array with exactly 4 values: [minLon, minLat, maxLon, maxLat].
     * @return JSON response containing the NDVI data.
     */
    @GetMapping("/ndvi")
    public String getNdviData(@RequestParam double[] bbox) {
        return ndviService.fetchNdviData(bbox);
    }
}
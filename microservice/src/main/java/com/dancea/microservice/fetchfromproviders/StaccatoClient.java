package com.dancea.microservice.fetchfromproviders;

import java.util.logging.Logger;

import org.springframework.web.client.RestTemplate;

import com.dancea.microservice.fetchfromproviders.models.StacData;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StaccatoClient {

    private final Logger logger = Logger.getLogger(StaccatoClient.class.getName());

    private final RestTemplate restTemplate;

    public void indexData(StacData data) {
        String url = "http://staccato:8080/collections/" + data.getSource() + "/items";
        // restTemplate.postForEntity(url, data.getData(), String.class);
        logger.info(String.format("Indexed data to Staccato: %s", url));
    }
}

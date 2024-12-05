package com.dancea.microservice.fetchfromproviders.models.msplanetary;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.dancea.microservice.fetchfromproviders.interfaces.DataProvider;
import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class MsplanetaryApiProvider implements DataProvider {

    private final Logger logger = Logger.getLogger(MsplanetaryApiProvider.class.getName());

    private final MsplanetaryTransformer transformer = new MsplanetaryTransformer();

    @Override
    public String getName() {
        return "msplanetary";
    }

    @Override
    public StacData provideData(Object queryParams) {
        RawData rawData = fetchData(queryParams);

        StacData stacData = transformer.transform(rawData);

        return stacData;
    }

    @Override
    public RawData fetchData(Object queryParams) {
        String apiUrl = "https://planetarycomputer.microsoft.com/api/stac/v1/";
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<String> request = new HttpEntity<>("{}", new HttpHeaders());

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(apiUrl, HttpMethod.GET, request,
                new ParameterizedTypeReference<>() {
                });
        Map<String, Object> responseBody = response.getBody();

        ObjectMapper objectMapper = new ObjectMapper();
        List<JsonNode> features = objectMapper.convertValue(responseBody.get("features"),
                new TypeReference<List<JsonNode>>() {
                });

        RawData rawData = new RawData();
        rawData.setData(features);
        return rawData;
    }
}

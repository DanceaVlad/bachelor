package com.dancea.microservice.fetchfromproviders;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class StaccatoConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public StaccatoClient staccatoClient(RestTemplate restTemplate) {
        return new StaccatoClient(restTemplate);
    }
}
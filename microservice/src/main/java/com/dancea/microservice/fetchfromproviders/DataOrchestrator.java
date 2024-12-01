package com.dancea.microservice.fetchfromproviders;

import org.springframework.stereotype.Component;

import com.dancea.microservice.fetchfromproviders.interfaces.DataProvider;
import com.dancea.microservice.fetchfromproviders.models.StacData;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Component
public class DataOrchestrator {

    private static final Logger logger = Logger.getLogger(DataOrchestrator.class.getName());

    private final List<DataProvider> providers = new ArrayList<>();
    private final StaccatoClient staccatoClient;

    public DataOrchestrator(StaccatoClient staccatoClient, List<DataProvider> providers) {
        this.staccatoClient = staccatoClient;
        this.providers.addAll(providers);
    }

    public void registerProvider(DataProvider provider) {
        providers.add(provider);
    }

    public void executeAll(Object queryParams) {
        for (DataProvider provider : providers) {
            try {
                StacData data = provider.provideData(queryParams);
                staccatoClient.indexData(data);
            } catch (Exception e) {
                logger.warning(
                        String.format("Failed to fetch data from provider %s: %s", provider.getName(), e.getMessage()));
                logger.warning(e.toString());
            }
        }
    }
}
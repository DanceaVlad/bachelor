package com.dancea.microservice.fetchfromproviders.models.planet;

import java.util.List;
import java.util.logging.Logger;

import com.dancea.microservice.fetchfromproviders.interfaces.DataProvider;
import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

public class PlanetApiProvider implements DataProvider {

    private final Logger logger = Logger.getLogger(PlanetApiProvider.class.getName());

    private final PlanetTransformer transformer = new PlanetTransformer();

    @Override
    public String getName() {
        return "planet";
    }

    @Override
    public StacData provideData(Object queryParams) {
        RawData rawData = fetchData(queryParams);
        logger.info(String.format("Fetched data from planet: %s", rawData));

        StacData stacData = transformer.transform(rawData);
        logger.info(String.format("Transformed data from planet: %s", stacData));

        return stacData;
    }

    @Override
    public RawData fetchData(Object queryParams) {
        return null;
    }
}

package com.dancea.microservice.fetchfromproviders.models.msplanetary;

import java.util.logging.Logger;

import com.dancea.microservice.fetchfromproviders.interfaces.DataProvider;
import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

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
        logger.info(String.format("Fetched data from msplanetary: %s", rawData));

        StacData stacData = transformer.transform(rawData);
        logger.info(String.format("Transformed data from msplanetary: %s", stacData));

        return stacData;
    }

    @Override
    public RawData fetchData(Object queryParams) {
        return null;
    }
}

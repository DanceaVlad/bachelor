package com.dancea.microservice.fetchfromproviders.models.planet;

import com.dancea.microservice.fetchfromproviders.interfaces.DataTransformer;
import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

public class PlanetTransformer implements DataTransformer {

    @Override
    public StacData transform(RawData rawData) {

        StacData stacData = new StacData("planet");
        stacData.setData(rawData.getData()); // TODO: Implement the transformation logic
        return stacData;
    }

}

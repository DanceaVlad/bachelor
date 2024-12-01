package com.dancea.microservice.fetchfromproviders.models.msplanetary;

import com.dancea.microservice.fetchfromproviders.interfaces.DataTransformer;
import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

public class MsplanetaryTransformer implements DataTransformer {

    @Override
    public StacData transform(RawData rawData) {
        StacData stacData = new StacData("msplanetary");
        stacData.setData(rawData.getData()); // Add implementation if needed
        return stacData;
    }
}

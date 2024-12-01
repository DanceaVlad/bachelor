package com.dancea.microservice.fetchfromproviders.models.msplanetary;

import com.dancea.microservice.fetchfromproviders.interfaces.DataTransformer;
import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

public class MsplanetaryTransformer implements DataTransformer {

    @Override
    public StacData transform(RawData rawData) {
        StacData stacData = new StacData("msplanetary");
        stacData.setData(rawData.getData()); // TODO: Implement the transformation logic
        return stacData;
    }
}

package com.dancea.microservice.fetchfromproviders.interfaces;

import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

public interface DataTransformer {
    StacData transform(RawData rawData);
}

package com.dancea.microservice.fetchfromproviders.interfaces;

import com.dancea.microservice.fetchfromproviders.models.RawData;
import com.dancea.microservice.fetchfromproviders.models.StacData;

public interface DataProvider {

    String getName();

    StacData provideData(Object queryParams);

    RawData fetchData(Object queryParams);
}

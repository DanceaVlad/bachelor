package com.dancea.microservice.fetchfromproviders.models;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@Getter
@Setter
public class StacData {

    private String source;
    private List<JsonNode> data;

    public StacData(String source) {
        this.source = source;
    }
}

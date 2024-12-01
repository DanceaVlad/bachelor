package com.dancea.microservice.fetchfromproviders.models;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@NoArgsConstructor
@Getter
@Setter
@ToString
public class RawData {
    List<JsonNode> data;
}

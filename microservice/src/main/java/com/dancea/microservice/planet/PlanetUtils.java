package com.dancea.microservice.planet;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PlanetUtils {
    public static final String PLANET_PROVIDER_NAME = "planet.com";
    public static final String PLANET_PROVIDER_URI_PATH = "/planet";
    public static final String PLANET_PROVIDER_API_KEY = "PLAK380f55a7c89f4c4aa9753286349bf874";
    public static final String PLANET_API_URL = "https://api.planet.com/data/v1/quick-search";
    public static final String PLANET_FILE_PATH = "microservice/src/main/resources/planet/";
    public static final String PLANET_GEOTIFF_FILE_PATH = PLANET_FILE_PATH + "geotiff/";
    public static final String PLANET_QUICKSEARCH_FILE_PATH = PLANET_FILE_PATH + "quicksearch.json";
    public static final String PLANET_QUICKSEARCH_ASSETS_FILE_PATH = PLANET_FILE_PATH + "quicksearch-assets.txt";
    public static final String PLANET_GEOTIFF_LINKS_FILE_PATH = PLANET_FILE_PATH + "geotiff-links.txt";
    public static final String PLANET_ACTIVATE_FILE_PATH = PLANET_FILE_PATH + "activate/";

    public static final HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "api-key " + PLANET_PROVIDER_API_KEY);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    public static final void writeJsonToFile(String content, String filePath) {

        // create directory if it doesn't exist
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        // create file if it doesn't exist
        // overwrite the file if it already exists
        // pretty print the JSON response
        try (FileWriter fileWriter = new FileWriter(file)) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(content);
            JsonNode features = rootNode.get("features");
            fileWriter.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(features));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static final void writeArrayToFile(String filePath, List<String> content) {

        // create directory if it doesn't exist
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        // create file if it doesn't exist
        // overwrite the file if it already exists
        try (FileWriter fileWriter = new FileWriter(file)) {
            for (String line : content) {
                fileWriter.write(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static final void writeToFile(String content, String filePath) {

        // create directory if it doesn't exist
        File file = new File(filePath);
        file.getParentFile().mkdirs();

        // create file if it doesn't exist
        // overwrite the file if it already exists
        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

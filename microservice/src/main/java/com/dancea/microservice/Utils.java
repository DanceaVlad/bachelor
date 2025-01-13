package com.dancea.microservice;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public class Utils {

    // Private constructor to hide the implicit public one
    private Utils() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Utils.class);

    // NASA API
    public static final String NASA_PROVIDER_NAME = "Nasa";
    public static final String NASA_PROVIDER_URI_PATH = "/nasa";

    public static final String NASA_BASE_DIR_PATH = "microservice/src/main/resources/nasa/";
    public static final String NASA_HDF_DIR_PATH = NASA_BASE_DIR_PATH + "01_hdf/";
    public static final String NASA_TIFF_DIR_PATH = NASA_BASE_DIR_PATH + "02_tiff/";
    public static final String NASA_MERGED_FILE_PATH = NASA_BASE_DIR_PATH + "03_merged.tif";
    public static final String NASA_REPROJECTED_FILE_PATH = NASA_BASE_DIR_PATH + "04_reprojected.tif";
    public static final String NASA_BYTE_FILE_PATH = NASA_BASE_DIR_PATH + "05_byte.vrt";
    public static final String NASA_TILE_DIR_PATH = NASA_BASE_DIR_PATH + "06_tiles/";

    // MSPlanetary API
    public static final String MSPLANETARY_PROVIDER_NAME = "MSPlanetary";
    public static final String MSPLANETARY_PROVIDER_URI_PATH = "/msplanetary";
    public static final String MSPLANETARY_COLLECTION_ID = "modis-13A1-061";
    public static final String MSPLANETARY_SIGN_URL = "https://planetarycomputer.microsoft.com/api/sas/v1/sign";
    public static final String MSPLANETARY_SEARCH_URL = "https://planetarycomputer.microsoft.com/api/stac/v1/search";

    public static final String MSPLANETARY_BASE_DIR_PATH = "microservice/src/main/resources/msplanetary/";
    public static final String MSPLANETARY_TIFF_DIR_PATH = MSPLANETARY_BASE_DIR_PATH + "01_tiff/";
    public static final String MSPLANETARY_MERGED_FILE_PATH = MSPLANETARY_BASE_DIR_PATH + "02_merged.tif";
    public static final String MSPLANETARY_REPROJECTED_FILE_PATH = MSPLANETARY_BASE_DIR_PATH + "03_reprojected.tif";
    public static final String MSPLANETARY_BYTE_FILE_PATH = MSPLANETARY_BASE_DIR_PATH + "04_byte.vrt";
    public static final String MSPLANETARY_TILE_DIR_PATH = MSPLANETARY_BASE_DIR_PATH + "tiles/";

    public static final String MSPLANETARY_TOKEN_KEY = "token";

    // Common Methods
    public static void runArrayCommand(String[] command, String successMessage, String errorMessage) {
        try {
            Process process = new ProcessBuilder(command).inheritIO().start();
            if (process.waitFor() == 0) {
                if (!successMessage.isEmpty())
                    logger.info("{}", successMessage);
            } else {
                logger.error("{}", errorMessage);
            }
        } catch (IOException e) {
            logger.error("{}\nCause:\n{}", errorMessage, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("{}\nCause:\n{}", errorMessage, e);
        }
    }

    public static boolean isFilePresent(String filePath) {
        File file = new File(filePath);
        return file.exists();
    }

    public static void deleteFiles(String[] filePaths) {
        for (String filePath : filePaths) {
            File file = new File(filePath);
            if (file.exists()) {
                if (file.delete()) {
                    logger.info("Deleted file: {}", filePath);
                } else {
                    logger.error("Failed to delete file: {}", filePath);
                }
            }
        }
    }

    public static void deleteDirectory(String directoryPath) {

        Path directory = Paths.get(directoryPath);

        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file); // Delete file
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir); // Delete directory after its contents are deleted
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.error("Failed to delete directory: {}\nCause:\n{}", directoryPath, e);
        }
    }
}

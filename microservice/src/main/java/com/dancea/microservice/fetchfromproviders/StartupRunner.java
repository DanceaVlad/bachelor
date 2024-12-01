package com.dancea.microservice.fetchfromproviders;

import java.util.logging.Logger;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dancea.microservice.fetchfromproviders.models.msplanetary.MsplanetaryApiProvider;

@Configuration
public class StartupRunner {
    Logger logger = Logger.getLogger(StartupRunner.class.getName());

    @Bean
    CommandLineRunner runOrchestrator(DataOrchestrator orchestrator) {
        return args -> {
            Object queryParams = null;
            orchestrator.registerProvider(new MsplanetaryApiProvider());
            orchestrator.executeAll(queryParams);
            logger.info("Data fetching completed");
        };
    }
}

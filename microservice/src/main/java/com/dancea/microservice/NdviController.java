package com.dancea.microservice;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NdviController {
    
    private final NdviService ndviService;

    public NdviController(NdviService ndviService) {
        this.ndviService = ndviService;
    }

    @GetMapping("/initialize-data")
    public void initializeData() {
        ndviService.initializeData();
    }
}

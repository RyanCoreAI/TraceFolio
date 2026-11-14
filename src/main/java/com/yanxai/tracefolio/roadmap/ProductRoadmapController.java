package com.yanxai.tracefolio.roadmap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
public class ProductRoadmapController {

    private final ProductRoadmapService roadmapService;

    public ProductRoadmapController(ProductRoadmapService roadmapService) {
        this.roadmapService = roadmapService;
    }

    @GetMapping("/roadmap")
    public ProductRoadmapResponse roadmap() {
        return roadmapService.roadmap();
    }
}

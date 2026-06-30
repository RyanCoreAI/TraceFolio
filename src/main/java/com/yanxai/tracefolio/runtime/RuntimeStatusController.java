package com.yanxai.tracefolio.runtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeStatusController {

    private final RuntimeStatusService runtimeStatusService;

    public RuntimeStatusController(RuntimeStatusService runtimeStatusService) {
        this.runtimeStatusService = runtimeStatusService;
    }

    @GetMapping("/status")
    public RuntimeStatus status() {
        return runtimeStatusService.getStatus();
    }
}

package com.yanxai.tracefolio.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class EvidenceOsAuditController {

    private final EvidenceOsAuditService auditService;

    public EvidenceOsAuditController(EvidenceOsAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/evidence-os")
    public EvidenceOsAuditResult audit() {
        return auditService.audit();
    }
}

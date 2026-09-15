package pt.rucodel.productionplanning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.rucodel.productionplanning.dto.AuditEventResponse;
import pt.rucodel.productionplanning.dto.PageResponse;
import pt.rucodel.productionplanning.service.AuditService;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return auditService.list(page, size);
    }
}

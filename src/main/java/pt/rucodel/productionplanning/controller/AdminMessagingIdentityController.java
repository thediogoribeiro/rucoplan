package pt.rucodel.productionplanning.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import pt.rucodel.productionplanning.dto.IdentityLinkRequest;
import pt.rucodel.productionplanning.dto.MessagingIdentityResponse;
import pt.rucodel.productionplanning.security.CurrentUserService;
import pt.rucodel.productionplanning.service.AdminMessagingIdentityService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/messaging-identities")
public class AdminMessagingIdentityController {
    private final AdminMessagingIdentityService identities;
    private final CurrentUserService currentUserService;

    public AdminMessagingIdentityController(AdminMessagingIdentityService identities, CurrentUserService currentUserService) {
        this.identities = identities;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<MessagingIdentityResponse> list() {
        return identities.listIdentities();
    }

    @GetMapping("/{identityId}")
    public MessagingIdentityResponse get(@PathVariable UUID identityId) {
        return identities.getIdentity(identityId);
    }

    @PostMapping("/{identityId}/link")
    public MessagingIdentityResponse link(@PathVariable UUID identityId, @Valid @RequestBody IdentityLinkRequest request) {
        return identities.link(identityId, request, currentUserService.requireUser().actorLabel());
    }

    @PostMapping("/{identityId}/block")
    public MessagingIdentityResponse block(@PathVariable UUID identityId) {
        return identities.block(identityId, currentUserService.requireUser().actorLabel());
    }

    @PostMapping("/{identityId}/reactivate")
    public MessagingIdentityResponse reactivate(@PathVariable UUID identityId) {
        return identities.reactivate(identityId, currentUserService.requireUser().actorLabel());
    }
}

package com.routealarm.ReachClock.sharing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SharingController {

    private final SharingService sharingService;

    @PostMapping("/api/v1/trips/{tripId}/shares")
    public ResponseEntity<ShareResponse> createShare(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId,
            @RequestBody CreateShareRequest request) {
        ShareResponse response = sharingService.createShare(userId, tripId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/v1/trips/{tripId}/shares")
    public List<ShareResponse> listShares(
            @PathVariable UUID tripId,
            @RequestAttribute("userId") UUID userId) {
        return sharingService.listShares(userId, tripId);
    }

    @DeleteMapping("/api/v1/shares/{shareId}")
    public ResponseEntity<Void> revokeShare(
            @PathVariable UUID shareId,
            @RequestAttribute("userId") UUID userId) {
        sharingService.revokeShare(userId, shareId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/shares/incoming")
    public List<IncomingShareResponse> getIncomingShares(
            @RequestAttribute("userId") UUID userId) {
        return sharingService.getIncomingShares(userId);
    }
}

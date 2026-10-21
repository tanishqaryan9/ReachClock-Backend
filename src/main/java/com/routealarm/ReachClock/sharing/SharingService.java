package com.routealarm.ReachClock.sharing;

import com.routealarm.ReachClock.alarm.Alarm;
import com.routealarm.ReachClock.alarm.AlarmRepository;
import com.routealarm.ReachClock.auth.Device;
import com.routealarm.ReachClock.auth.DeviceRepository;
import com.routealarm.ReachClock.eta.EtaResult;
import com.routealarm.ReachClock.location.LocationUpdate;
import com.routealarm.ReachClock.trip.Trip;
import com.routealarm.ReachClock.trip.TripRepository;
import com.routealarm.ReachClock.trip.TripStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharingService {

    private static final double DEFAULT_RADIUS_METERS = 200.0;

    private final TripShareRepository tripShareRepository;
    private final TripRepository tripRepository;
    private final AlarmRepository alarmRepository;
    private final DeviceRepository deviceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;
    private final ExpoPushService expoPushService;

    // ─── 1. Create share ───────────────────────────────────────────────────────

    @Transactional
    public ShareResponse createShare(UUID ownerId, UUID tripId, CreateShareRequest request) {
        Trip trip = tripRepository.findByIdAndUserId(tripId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found or not owned by you"));
        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw new IllegalArgumentException("Trip is not active");
        }

        Device recipient = deviceRepository.findByShareCode(request.recipientCode())
                .orElseThrow(() -> new IllegalArgumentException("No device with that share code"));

        if (recipient.getId().equals(ownerId)) {
            throw new IllegalArgumentException("Cannot share a trip with yourself");
        }

        tripShareRepository.findByOwnerIdAndRecipientIdAndTripIdAndStatus(ownerId, recipient.getId(), tripId, "ACTIVE")
                .ifPresent(s -> { throw new IllegalArgumentException("Already sharing this trip with that recipient"); });

        TripShare share = new TripShare();
        share.setTripId(tripId);
        share.setOwnerId(ownerId);
        share.setRecipientId(recipient.getId());
        share.setEtaMinutes(request.etaMinutes());
        share.setNotifyOnArrival(request.notifyOnArrival());
        share.setNotifyOnLeave(request.notifyOnLeave());
        share.setStatus("ACTIVE");

        share = tripShareRepository.save(share);
        return toShareResponse(share, recipient);
    }

    // ─── 2. List shares for a trip ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ShareResponse> listShares(UUID ownerId, UUID tripId) {
        // Verify ownership
        tripRepository.findByIdAndUserId(tripId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found or not owned by you"));

        return tripShareRepository.findAllByTripIdAndStatus(tripId, "ACTIVE").stream()
                .map(share -> {
                    Device recipient = deviceRepository.findById(share.getRecipientId()).orElse(null);
                    return toShareResponse(share, recipient);
                })
                .toList();
    }

    // ─── 3. Revoke share ───────────────────────────────────────────────────────

    @Transactional
    public void revokeShare(UUID requesterId, UUID shareId) {
        TripShare share = tripShareRepository.findById(shareId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));

        boolean isOwner = share.getOwnerId().equals(requesterId);
        boolean isRecipient = share.getRecipientId().equals(requesterId);

        if (!isOwner && !isRecipient) {
            throw new IllegalArgumentException("You are not the owner or recipient of this share");
        }

        share.setStatus("REVOKED");
        share.setEndedAt(Instant.now());
        tripShareRepository.save(share);

        // Notify recipient that share ended
        sendShareUpdate(share, "REVOKED");
    }

    // ─── 4. Incoming shares for a recipient ───────────────────────────────────

    @Transactional(readOnly = true)
    public List<IncomingShareResponse> getIncomingShares(UUID recipientId) {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);

        List<TripShare> shares = tripShareRepository.findAllByRecipientId(recipientId);

        return shares.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus())
                        || ("ENDED".equals(s.getStatus()) && s.getEndedAt() != null && s.getEndedAt().isAfter(cutoff)))
                .map(share -> {
                    Device owner = deviceRepository.findById(share.getOwnerId()).orElse(null);
                    Trip trip = tripRepository.findById(share.getTripId()).orElse(null);
                    String ownerName = owner != null ? owner.getDisplayName() : null;
                    String destinationName = null;
                    double destLat = 0;
                    double destLng = 0;
                    if (trip != null) {
                        destLat = trip.getDestinationLatitude();
                        destLng = trip.getDestinationLongitude();
                        // Try to get destination name from the trip's alarm
                        try {
                            com.routealarm.ReachClock.alarm.Alarm a = alarmRepository
                                    .findByIdAndUserId(trip.getAlarmId(), trip.getUserId())
                                    .orElse(null);
                            if (a != null) destinationName = a.getDestinationName();
                        } catch (Exception ignored) {}
                    }
                    Long updatedAtMs = share.getLastUpdatedAt() != null
                            ? share.getLastUpdatedAt().toEpochMilli() : null;
                    return new IncomingShareResponse(
                            share.getId(),
                            ownerName,
                            destinationName,
                            destLat,
                            destLng,
                            share.getStatus(),
                            share.getLastOwnerLatitude(),
                            share.getLastOwnerLongitude(),
                            share.getLastOwnerRemainingSeconds(),
                            share.getLastOwnerDistanceMeters(),
                            updatedAtMs
                    );
                })
                .toList();
    }

    // ─── 5. Process share events (called from LocationService) ─────────────────

    @Transactional
    public void processShareEvents(Trip trip, Alarm alarm, LocationUpdate loc, EtaResult eta) {
        List<TripShare> shares = tripShareRepository.findAllByTripIdAndStatus(trip.getId(), "ACTIVE");
        if (shares.isEmpty()) return;

        Device owner = deviceRepository.findById(trip.getUserId()).orElse(null);
        String ownerName = owner != null ? owner.getDisplayName() : null;
        String destinationName = alarm != null ? alarm.getDestinationName() : null;
        double radiusMeters = (alarm != null && alarm.getRadiusMeters() != null)
                ? alarm.getRadiusMeters() : DEFAULT_RADIUS_METERS;

        Instant now = Instant.now();

        for (TripShare share : shares) {
            // a. Update position snapshot
            share.setLastOwnerLatitude(loc.latitude());
            share.setLastOwnerLongitude(loc.longitude());
            share.setLastOwnerRemainingSeconds(eta.remainingSeconds());
            share.setLastOwnerDistanceMeters(eta.distanceMeters());
            share.setLastUpdatedAt(now);

            // b. Send live update to recipient
            ShareUpdatePayload update = new ShareUpdatePayload(
                    share.getId(),
                    loc.latitude(),
                    loc.longitude(),
                    eta.remainingSeconds(),
                    eta.distanceMeters(),
                    now.toEpochMilli(),
                    "ACTIVE",
                    eta.trafficCondition(),
                    eta.delayMinutes()
            );
            sendToUser(share.getRecipientId().toString(), "/queue/share-updates", update);

            // c. Check ETA events
            for (Integer m : share.getEtaMinutes()) {
                String eventKey = "ETA:" + m;
                if (eta.remainingSeconds() <= (long) m * 60 && !share.hasFiredEvent(eventKey)) {
                    share.addFiredEvent(eventKey);
                    fireShareEvent(share, eventKey, ownerName, destinationName, m, eta.remainingSeconds(), owner);
                }
            }

            // d. Check ARRIVED
            if (share.isNotifyOnArrival()
                    && eta.distanceMeters() <= radiusMeters
                    && !share.hasFiredEvent("ARRIVED")) {
                share.addFiredEvent("ARRIVED");
                fireShareEvent(share, "ARRIVED", ownerName, destinationName, null, eta.remainingSeconds(), owner);
            }

            // e. Check LEFT_PLACE (DEPARTURE alarm type: owner was inside and now left)
            if (share.isNotifyOnLeave()
                    && trip.isInsideSeen()
                    && eta.distanceMeters() > radiusMeters
                    && !share.hasFiredEvent("LEFT_PLACE")) {
                share.addFiredEvent("LEFT_PLACE");
                fireShareEvent(share, "LEFT_PLACE", ownerName, destinationName, null, eta.remainingSeconds(), owner);
            }

            tripShareRepository.save(share);
        }
    }

    // ─── 6. End all shares for a trip ─────────────────────────────────────────

    @Transactional
    public void endSharesForTrip(UUID tripId, String finalStatus) {
        List<TripShare> shares = tripShareRepository.findAllByTripIdAndStatus(tripId, "ACTIVE");
        Instant now = Instant.now();
        for (TripShare share : shares) {
            share.setStatus("ENDED".equals(finalStatus) ? "ENDED" : "ENDED");
            share.setEndedAt(now);
            tripShareRepository.save(share);
            sendShareUpdate(share, "ENDED");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void fireShareEvent(TripShare share, String event, String ownerName,
                                String destinationName, Integer minutes,
                                long remainingSeconds, Device owner) {
        ShareEventPayload payload = new ShareEventPayload(
                event,
                share.getId(),
                ownerName,
                destinationName,
                minutes,
                remainingSeconds,
                Instant.now().toEpochMilli()
        );
        String recipientId = share.getRecipientId().toString();

        // Send via WebSocket
        sendToUser(recipientId, "/queue/share-events", payload);

        // Send push if recipient has no active WebSocket session
        if (simpUserRegistry.getUser(recipientId) == null) {
            Device recipient = deviceRepository.findById(share.getRecipientId()).orElse(null);
            if (recipient != null && recipient.getPushToken() != null) {
                String title = buildPushTitle(event, ownerName, minutes);
                String body = buildPushBody(event, destinationName, minutes, remainingSeconds);
                expoPushService.send(recipient.getPushToken(), title, body, payload);
            }
        }
    }

    private void sendShareUpdate(TripShare share, String status) {
        ShareUpdatePayload payload = new ShareUpdatePayload(
                share.getId(),
                share.getLastOwnerLatitude() != null ? share.getLastOwnerLatitude() : 0,
                share.getLastOwnerLongitude() != null ? share.getLastOwnerLongitude() : 0,
                share.getLastOwnerRemainingSeconds() != null ? share.getLastOwnerRemainingSeconds() : 0,
                share.getLastOwnerDistanceMeters() != null ? share.getLastOwnerDistanceMeters() : 0,
                Instant.now().toEpochMilli(),
                status
        );
        sendToUser(share.getRecipientId().toString(), "/queue/share-updates", payload);
    }

    private void sendToUser(String userId, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId, destination, payload);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message to user {}: {}", userId, e.getMessage());
        }
    }

    private ShareResponse toShareResponse(TripShare share, Device recipient) {
        String recipientName = recipient != null ? recipient.getDisplayName() : null;
        String recipientCode = recipient != null ? recipient.getShareCode() : null;
        return new ShareResponse(
                share.getId(),
                share.getTripId(),
                recipientName,
                recipientCode,
                share.getEtaMinutes(),
                share.isNotifyOnArrival(),
                share.isNotifyOnLeave(),
                share.getStatus()
        );
    }

    private String buildPushTitle(String event, String ownerName, Integer minutes) {
        String name = ownerName != null ? ownerName : "Someone";
        return switch (event) {
            case "ARRIVED" -> name + " has arrived!";
            case "LEFT_PLACE" -> name + " has left";
            default -> name + " is " + minutes + " min away";
        };
    }

    private String buildPushBody(String event, String destinationName, Integer minutes, long remainingSeconds) {
        String dest = destinationName != null ? destinationName : "the destination";
        return switch (event) {
            case "ARRIVED" -> "They have reached " + dest;
            case "LEFT_PLACE" -> "They have left " + dest;
            default -> "ETA: approximately " + minutes + " minute(s) to " + dest;
        };
    }
}

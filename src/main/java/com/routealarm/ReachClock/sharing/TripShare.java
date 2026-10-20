package com.routealarm.ReachClock.sharing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "trip_shares", indexes = {
        @Index(name = "idx_trip_shares_trip_id", columnList = "tripId"),
        @Index(name = "idx_trip_shares_owner_id", columnList = "ownerId"),
        @Index(name = "idx_trip_shares_recipient_id", columnList = "recipientId")
})
@Getter
@Setter
public class TripShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tripId;

    @Column(nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, updatable = false)
    private UUID recipientId;

    /** Comma-separated list of ETA threshold minutes, e.g. "5,10,15" */
    @Column
    private String etaMinutesRaw;

    @Column(nullable = false)
    private boolean notifyOnArrival;

    @Column(nullable = false)
    private boolean notifyOnLeave;

    /** Comma-separated fired events, e.g. "ETA:15,ARRIVED" */
    @Column
    private String firedEventsRaw;

    /** ACTIVE | ENDED | REVOKED */
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant endedAt;

    @Column
    private Double lastOwnerLatitude;

    @Column
    private Double lastOwnerLongitude;

    @Column
    private Long lastOwnerRemainingSeconds;

    @Column
    private Double lastOwnerDistanceMeters;

    @Column
    private Instant lastUpdatedAt;

    // ---- convenience accessors for etaMinutes ----

    public List<Integer> getEtaMinutes() {
        if (etaMinutesRaw == null || etaMinutesRaw.isBlank()) return new ArrayList<>();
        return Arrays.stream(etaMinutesRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    public void setEtaMinutes(List<Integer> minutes) {
        if (minutes == null || minutes.isEmpty()) {
            this.etaMinutesRaw = null;
        } else {
            this.etaMinutesRaw = minutes.stream().map(String::valueOf).collect(Collectors.joining(","));
        }
    }

    // ---- convenience accessors for firedEvents ----

    public List<String> getFiredEvents() {
        if (firedEventsRaw == null || firedEventsRaw.isBlank()) return new ArrayList<>();
        return Arrays.stream(firedEventsRaw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public void addFiredEvent(String event) {
        List<String> events = getFiredEvents();
        if (!events.contains(event)) {
            events.add(event);
        }
        this.firedEventsRaw = String.join(",", events);
    }

    public boolean hasFiredEvent(String event) {
        return getFiredEvents().contains(event);
    }
}

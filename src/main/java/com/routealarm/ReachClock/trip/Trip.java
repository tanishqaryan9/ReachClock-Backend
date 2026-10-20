package com.routealarm.ReachClock.trip;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trips", indexes = @Index(name = "idx_trips_user_id", columnList = "userId"))
@Getter
@Setter
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID alarmId;

    private double destinationLatitude;
    private double destinationLongitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.ACTIVE;

    /** DEPARTURE alarms: true once the user has been seen inside the radius. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean insideSeen = false;

    private Instant startedAt = Instant.now();
    private Instant endedAt;
}

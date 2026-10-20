// Alarm.java
package com.routealarm.ReachClock.alarm;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alarms", indexes = @Index(name = "idx_alarms_user_id", columnList = "userId"))
@Getter
@Setter
public class Alarm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmStatus status = AlarmStatus.ACTIVE;

    private Integer thresholdMinutes;
    private Integer radiusMeters;

    private String destinationName;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
package com.routealarm.ReachClock.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
@Getter
@Setter
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(unique = true, nullable = false, updatable = false)
    private String shareCode;

    @Column
    private String displayName;

    @Column
    private String pushToken;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}

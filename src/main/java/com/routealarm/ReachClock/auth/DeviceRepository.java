package com.routealarm.ReachClock.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {
    Optional<Device> findByTokenHash(String tokenHash);
    Optional<Device> findByShareCode(String shareCode);
    boolean existsByShareCode(String shareCode);
}

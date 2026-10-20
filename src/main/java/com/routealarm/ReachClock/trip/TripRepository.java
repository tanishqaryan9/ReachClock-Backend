package com.routealarm.ReachClock.trip;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    Optional<Trip> findByIdAndUserId(UUID tripId, UUID userId);

    boolean existsByAlarmIdAndStatus(UUID alarmId, TripStatus status);

    List<Trip> findAllByUserIdAndStatus(UUID userId, TripStatus status);
}

package com.routealarm.ReachClock.alarm;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlarmRepository extends JpaRepository<Alarm, UUID> {

    List<Alarm> findAllByUserId(UUID userId);

    Optional<Alarm> findByIdAndUserId(UUID id, UUID userId);
}

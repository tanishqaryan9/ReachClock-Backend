package com.routealarm.ReachClock.sharing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripShareRepository extends JpaRepository<TripShare, UUID> {

    List<TripShare> findAllByOwnerIdAndStatus(UUID ownerId, String status);

    List<TripShare> findAllByRecipientIdAndStatus(UUID recipientId, String status);

    List<TripShare> findAllByRecipientId(UUID recipientId);

    Optional<TripShare> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<TripShare> findByOwnerIdAndRecipientIdAndTripIdAndStatus(UUID ownerId, UUID recipientId, UUID tripId, String status);

    List<TripShare> findAllByTripId(UUID tripId);

    List<TripShare> findAllByRecipientIdAndStatusIn(UUID recipientId, List<String> statuses);

    List<TripShare> findAllByTripIdAndStatus(UUID tripId, String status);

    Optional<TripShare> findByIdAndRecipientId(UUID id, UUID recipientId);
}

package com.routealarm.ReachClock.location;

import com.routealarm.ReachClock.alarm.Alarm;
import com.routealarm.ReachClock.alarm.AlarmRepository;
import com.routealarm.ReachClock.alarm.AlarmStatus;
import com.routealarm.ReachClock.eta.EtaAlarmService;
import com.routealarm.ReachClock.eta.EtaResult;
import com.routealarm.ReachClock.eta.EtaService;
import com.routealarm.ReachClock.eta.RouteService;
import com.routealarm.ReachClock.sharing.SharingService;
import com.routealarm.ReachClock.trip.Trip;
import com.routealarm.ReachClock.trip.TripRepository;
import com.routealarm.ReachClock.trip.TripStatus;
import com.routealarm.ReachClock.websocket.AlarmEvent;
import com.routealarm.ReachClock.websocket.AlarmEventPublisher;
import com.routealarm.ReachClock.websocket.TripUpdateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationValidator validator;
    private final TripRepository tripRepository;
    private final AlarmRepository alarmRepository;
    private final EtaService etaService;
    private final EtaAlarmService etaAlarmService;
    private final RouteService routeService;
    private final AlarmEventPublisher publisher;
    private final SharingService sharingService;

    /** Striped locks: updates for one trip are processed one at a time, so an alarm can never fire twice. */
    private final Object[] locks = createLocks();

    private static Object[] createLocks() {
        Object[] l = new Object[64];
        for (int i = 0; i < l.length; i++) l[i] = new Object();
        return l;
    }

    private Object lockFor(UUID tripId) {
        return locks[Math.floorMod(tripId.hashCode(), locks.length)];
    }

    public void process(UUID userId, LocationUpdate update) {
        if (!validator.isValid(update)) return;

        synchronized (lockFor(update.tripId())) {
            Trip trip = tripRepository.findByIdAndUserId(update.tripId(), userId).orElse(null);
            if (trip == null || trip.getStatus() != TripStatus.ACTIVE) return;

            Alarm alarm = alarmRepository.findByIdAndUserId(trip.getAlarmId(), userId).orElse(null);
            if (alarm == null || alarm.getStatus() != AlarmStatus.ACTIVE) {
                // alarm was deleted / cancelled while travelling
                finish(trip, TripStatus.CANCELLED);
                return;
            }

            EtaResult eta = etaService.updateEta(trip, update);
            String user = userId.toString();

            publisher.sendTripUpdate(user, new TripUpdateEvent(
                    trip.getId(), update.latitude(), update.longitude(),
                    eta.remainingSeconds(), eta.distanceMeters(), eta.calculatedAt().toEpochMilli()));

            if (etaAlarmService.shouldTrigger(trip, alarm, update, eta)) {
                alarm.setStatus(AlarmStatus.TRIGGERED);
                alarmRepository.save(alarm);
                finish(trip, TripStatus.ARRIVED);

                publisher.sendAlarmEvent(user, new AlarmEvent(
                        "ALARM_TRIGGERED", alarm.getId(), trip.getId(), alarm.getType(),
                        alarm.getDestinationName(), eta.remainingSeconds(), eta.distanceMeters(),
                        Instant.now().toEpochMilli()));
            }
        }
    }

    private void finish(Trip trip, TripStatus status) {
        trip.setStatus(status);
        trip.setEndedAt(Instant.now());
        tripRepository.save(trip);
        routeService.forget(trip.getId());
    }
}

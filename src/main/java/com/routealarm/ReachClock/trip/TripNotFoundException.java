package com.routealarm.ReachClock.trip;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException() { super("Trip not found"); }
}

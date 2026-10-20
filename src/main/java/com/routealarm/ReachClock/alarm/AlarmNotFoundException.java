package com.routealarm.ReachClock.alarm;

public class AlarmNotFoundException extends RuntimeException {
    public AlarmNotFoundException() { super("Alarm not found"); }
}

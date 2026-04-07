package com.brixo.slidehub.ui.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserActivityTracker {

    private final AtomicLong lastActivityEpochMs = new AtomicLong(0);

    public void markActivityNow() {
        lastActivityEpochMs.set(System.currentTimeMillis());
    }

    public boolean hasRecentActivity(long windowMs) {
        long last = lastActivityEpochMs.get();
        if (last <= 0 || windowMs <= 0) {
            return false;
        }
        return System.currentTimeMillis() - last <= windowMs;
    }
}

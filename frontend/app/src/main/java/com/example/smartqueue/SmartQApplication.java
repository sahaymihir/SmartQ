package com.example.smartqueue;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

/**
 * Enables Material You dynamic color for all view-based activities when the
 * device supports it, keeping XML screens aligned with the Compose theme.
 */
public class SmartQApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}

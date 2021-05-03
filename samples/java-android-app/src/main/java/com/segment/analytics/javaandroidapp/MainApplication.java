package com.segment.analytics.javaandroidapp;

import android.app.Application;

import com.segment.analytics.Analytics;
import com.segment.analytics.AndroidAnalyticsKt;

import kotlin.Unit;

public class MainApplication extends Application {
    private Analytics mainAnalytics;

    @Override
    public void onCreate() {
        super.onCreate();
        mainAnalytics = AndroidAnalyticsKt.Analytics(BuildConfig.SEGMENT_WRITE_KEY, getApplicationContext(), configuration -> {
            configuration.setFlushAt(1);
            return Unit.INSTANCE;
        });
    }
}

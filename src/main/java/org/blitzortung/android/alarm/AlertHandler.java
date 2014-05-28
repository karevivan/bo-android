package org.blitzortung.android.alarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Vibrator;
import android.util.Log;
import org.blitzortung.android.alarm.factory.AlarmObjectFactory;
import org.blitzortung.android.alarm.handler.AlarmStatusHandler;
import org.blitzortung.android.alarm.listener.AlertListener;
import org.blitzortung.android.alarm.object.AlarmSector;
import org.blitzortung.android.alarm.object.AlarmStatus;
import org.blitzortung.android.app.Main;
import org.blitzortung.android.app.R;
import org.blitzortung.android.app.controller.LocationHandler;
import org.blitzortung.android.app.controller.LocationListener;
import org.blitzortung.android.app.controller.NotificationHandler;
import org.blitzortung.android.app.view.PreferenceKey;
import org.blitzortung.android.data.beans.Stroke;
import org.blitzortung.android.util.MeasurementSystem;

import java.util.Collection;

public class AlertHandler implements OnSharedPreferenceChangeListener, LocationListener {

    private final Vibrator vibrator;
    private final NotificationHandler notificationHandler;
    private Context context;
    private Collection<? extends Stroke> lastStrokes;
    private int vibrationSignalDuration;
    private Uri alarmSoundNotificationSignal;

    private final AlarmParameters alarmParameters;

    private Location location;

    private boolean alarmEnabled;

    private boolean alarmValid;

    private AlertListener alertListener;

    private final AlarmStatus alarmStatus;

    private final AlarmStatusHandler alarmStatusHandler;

    private final LocationHandler locationHandler;

    private float notificationDistanceLimit;

    private long notificationLastTimestamp;

    private float signalingDistanceLimit;

    private long signalingLastTimestamp;

    public AlertHandler(LocationHandler locationHandler, SharedPreferences preferences, Context context, Vibrator vibrator, NotificationHandler notificationHandler, AlarmObjectFactory alarmObjectFactory, AlarmParameters alarmParameters) {
        this.locationHandler = locationHandler;
        this.context = context;
        this.vibrator = vibrator;
        this.notificationHandler = notificationHandler;
        this.alarmStatus = alarmObjectFactory.createAlarmStatus(alarmParameters);
        this.alarmStatusHandler = alarmObjectFactory.createAlarmStatusHandler(alarmParameters);
        this.alarmParameters = alarmParameters;


        preferences.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(preferences, PreferenceKey.ALARM_ENABLED);
        onSharedPreferenceChanged(preferences, PreferenceKey.MEASUREMENT_UNIT);
        onSharedPreferenceChanged(preferences, PreferenceKey.NOTIFICATION_DISTANCE_LIMIT);
        onSharedPreferenceChanged(preferences, PreferenceKey.SIGNALING_DISTANCE_LIMIT);
        onSharedPreferenceChanged(preferences, PreferenceKey.ALARM_VIBRATION_SIGNAL);
        onSharedPreferenceChanged(preferences, PreferenceKey.ALARM_SOUND_SIGNAL);

        alarmValid = false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String keyString) {
        onSharedPreferenceChanged(sharedPreferences, PreferenceKey.fromString(keyString));
    }

    private void onSharedPreferenceChanged(SharedPreferences sharedPreferences, PreferenceKey key) {
        switch (key) {
            case ALARM_ENABLED:
                alarmEnabled = sharedPreferences.getBoolean(key.toString(), false);

                if (alarmEnabled) {
                    locationHandler.requestUpdates(this);
                } else {
                    locationHandler.removeUpdates(this);
                    location = null;
                    broadcastClear();
                }
                break;

            case MEASUREMENT_UNIT:
                String measurementSystemName = sharedPreferences.getString(key.toString(), MeasurementSystem.METRIC.toString());

                alarmParameters.setMeasurementSystem(MeasurementSystem.valueOf(measurementSystemName));
                break;

            case NOTIFICATION_DISTANCE_LIMIT:
                notificationDistanceLimit = Float.parseFloat(sharedPreferences.getString(key.toString(), "50"));
                break;

            case SIGNALING_DISTANCE_LIMIT:
                signalingDistanceLimit = Float.parseFloat(sharedPreferences.getString(key.toString(), "25"));
                break;

            case ALARM_VIBRATION_SIGNAL:
                vibrationSignalDuration = sharedPreferences.getInt(key.toString(), 3) * 10;
                break;

            case ALARM_SOUND_SIGNAL:
                final String signalUri = sharedPreferences.getString(key.toString(), "");
                alarmSoundNotificationSignal = !signalUri.isEmpty() ? Uri.parse(signalUri) : null;
                break;
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        this.location = location;

        checkStrokes(lastStrokes);
    }

    public boolean isAlarmEnabled() {
        return alarmEnabled;
    }

    public void checkStrokes(Collection<? extends Stroke> strokes) {
        boolean currentAlarmIsValid = isAlarmEnabled() && location != null && strokes != null;
        lastStrokes = strokes;

        if (currentAlarmIsValid) {
            alarmValid = true;
            alarmStatusHandler.checkStrokes(alarmStatus, strokes, location);
            processResult(getAlarmResult());
        } else {
            invalidateAlarm();
        }
    }

    public AlarmResult getAlarmResult() {
        return alarmValid ? alarmStatusHandler.getCurrentActivity(alarmStatus) : null;
    }

    public String getTextMessage(float notificationDistanceLimit) {
        return alarmStatusHandler.getTextMessage(alarmStatus, notificationDistanceLimit);
    }

    public void setAlertListener(AlertListener alertListener) {
        this.alertListener = alertListener;
    }

    public Collection<AlarmSector> getAlarmSectors() {
        return alarmStatus.getSectors();
    }

    public AlarmParameters getAlarmParameters() {
        return alarmParameters;
    }

    public AlarmStatus getAlarmStatus() {
        return alarmValid ? alarmStatus : null;
    }

    public float getMaxDistance() {
        final float[] ranges = alarmParameters.getRangeSteps();
        return ranges[ranges.length - 1];
    }

    private void invalidateAlarm() {
        boolean previousAlarmValidState = alarmValid;
        alarmValid = false;

        if (previousAlarmValidState) {
            alarmStatus.clearResults();
            broadcastClear();
        }
    }

    private void broadcastClear() {
        if (alertListener != null) {
            alertListener.onAlertCancel();
        }
    }

    private void broadcastResult(AlarmResult alarmResult) {
        if (alertListener != null) {
            alertListener.onAlert(alarmStatus, alarmResult);
        }
    }

    private void processResult(AlarmResult alarmResult) {
        if (alarmResult != null) {
            Log.v(Main.LOG_TAG, "AlertHandler.processResult()");

            alarmParameters.updateSectorLabels(context);

            if (alarmResult.getClosestStrokeDistance() <= signalingDistanceLimit) {
                long signalingLatestTimestamp = alarmStatusHandler.getLatestTimstampWithin(signalingDistanceLimit, alarmStatus);
                if (signalingLatestTimestamp > signalingLastTimestamp) {
                    Log.v(Main.LOG_TAG, "AlertHandler.processResult() perform alarm");
                    vibrateIfEnabled();
                    playSoundIfEnabled();
                    signalingLastTimestamp = signalingLatestTimestamp;
                } else {
                    Log.d(Main.LOG_TAG, String.format("old signaling event: %d vs %d", signalingLatestTimestamp, signalingLastTimestamp));
                }
            }

            if (alarmResult.getClosestStrokeDistance() <= notificationDistanceLimit) {
                long notificationLatestTimestamp = alarmStatusHandler.getLatestTimstampWithin(notificationDistanceLimit, alarmStatus);
                if (notificationLatestTimestamp > notificationLastTimestamp) {
                    Log.v(Main.LOG_TAG, "AlertHandler.processResult() perform notification");
                    notificationHandler.sendNotification(context.getResources().getString(R.string.activity) + ": " + getTextMessage(notificationDistanceLimit));
                    notificationLastTimestamp = notificationLatestTimestamp;
                } else {
                    Log.d(Main.LOG_TAG, String.format("AlertHandler.processResult() previous signaling event: %d vs %d", notificationLatestTimestamp, signalingLastTimestamp));
                }
            } else {
                notificationHandler.clearNotification();
            }
        } else {
            notificationHandler.clearNotification();
        }

        Log.v(Main.LOG_TAG, String.format("AlertHandler.processResult() broadcast result %s", alarmResult));

        broadcastResult(alarmResult);
    }

    private void vibrateIfEnabled() {
        vibrator.vibrate(vibrationSignalDuration);
    }

    private void playSoundIfEnabled() {
        if (alarmSoundNotificationSignal != null ) {
            Ringtone r = RingtoneManager.getRingtone(context, alarmSoundNotificationSignal);
            if (r != null) {
                if (!r.isPlaying()) {
                    r.setStreamType(AudioManager.STREAM_NOTIFICATION);
                    r.play();
                }
                Log.v(Main.LOG_TAG, "playing " + r.getTitle(context));
            }
        }
    }

    public Location getCurrentLocation() {
        return location;
    }

    public void cancelAlert() {
        broadcastClear();
    }
}
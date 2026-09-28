package com.example.femora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/**
 * ShakeDetectionService  —  Femora Women Safety
 * ──────────────────────────────────────────────
 * Foreground service that monitors the accelerometer for distress shakes.
 *
 * NEW in this version
 * ───────────────────
 *  • Three configurable sensitivity levels (Low / Medium / High) persisted in
 *    SharedPreferences ("femora_prefs", key "shake_sensitivity").
 *  • Stationary-bias filter: ignores shakes while gravity variance is very low
 *    (phone sitting flat on a table avoids accidental triggers).
 *  • SOS countdown with cancellation window (CANCEL_WINDOW_MS). A short
 *    vibration burst fires immediately; if the user doesn't cancel within the
 *    window the real SOS broadcast goes out.
 *  • Shake-count progress broadcast so the UI can animate a "1…2…3!" indicator.
 *  • Notification updates dynamically to reflect the active sensitivity level.
 */
public class ShakeDetectionService extends Service implements SensorEventListener {

    private static final String TAG = "ShakeDetectionService";

    // ── SharedPreferences ─────────────────────────────────────────────────────
    public static final String PREFS_NAME          = "femora_prefs";
    public static final String PREF_SHAKE_SENSITIVITY = "shake_sensitivity";

    /** Values stored in SharedPreferences for sensitivity. */
    public static final String SENSITIVITY_LOW    = "low";
    public static final String SENSITIVITY_MEDIUM = "medium";   // default
    public static final String SENSITIVITY_HIGH   = "high";

    // ── Per-sensitivity thresholds (m/s²) ─────────────────────────────────────
    /** Low  — requires a very forceful, deliberate shake. */
    private static final float THRESHOLD_LOW    = 24f;
    /** Medium — default, comfortable deliberate shake. */
    private static final float THRESHOLD_MEDIUM = 18f;
    /** High — reacts to mild shakes, useful for limited mobility. */
    private static final float THRESHOLD_HIGH   = 13f;

    /** Number of shake impulses required to trigger the SOS countdown. */
    private static final int  SHAKE_COUNT_TRIGGER = 3;
    /** Window in which all impulses must occur (ms). */
    private static final long SHAKE_WINDOW_MS     = 1_500L;
    /** Minimum gap between two consecutive impulse detections (ms). */
    private static final long SHAKE_INTERVAL_MS   = 250L;

    // ── Stationary-bias filter ────────────────────────────────────────────────
    /**
     * Rolling-average gravity magnitude; updated on every sensor event.
     * If the phone's net force stays within ±STATIONARY_EPSILON of 9.81 for
     * too long we know it is sitting still — that prevents desk-knock triggers.
     */
    private static final float GRAVITY_SI         = 9.81f;
    private static final float STATIONARY_EPSILON = 1.5f;   // m/s²
    private float gravityAvg = GRAVITY_SI;
    private static final float GRAVITY_ALPHA = 0.1f;         // low-pass filter weight

    // ── SOS countdown ─────────────────────────────────────────────────────────
    /**
     * Time between the shake-count reaching SHAKE_COUNT_TRIGGER and the actual
     * SOS broadcast. During this window the user can cancel via the notification
     * or the in-app toast.
     */
    private static final long CANCEL_WINDOW_MS = 5_000L;
    private boolean sosCountdownActive = false;

    // ── Notification ──────────────────────────────────────────────────────────
    public static final String CHANNEL_ID = "shake_detection_channel";
    public static final int    NOTIF_ID   = 1001;

    // ── Broadcasts ────────────────────────────────────────────────────────────
    /** Fired when the full SOS countdown completes (or immediately if window=0). */
    public static final String ACTION_SOS_TRIGGERED  = "com.example.femora.ACTION_SOS_TRIGGERED";
    /** Fired as soon as the shake count hits the trigger — starts the countdown UI. */
    public static final String ACTION_SOS_COUNTDOWN  = "com.example.femora.ACTION_SOS_COUNTDOWN";
    /** Carries remaining ms in the countdown. */
    public static final String EXTRA_COUNTDOWN_MS    = "countdown_ms";
    /** Fired on each shake impulse so the UI can animate a counter bubble. */
    public static final String ACTION_SHAKE_PROGRESS = "com.example.femora.ACTION_SHAKE_PROGRESS";
    /** Current shake count (1, 2, …). */
    public static final String EXTRA_SHAKE_COUNT     = "shake_count";

    public static final String EXTRA_TRIGGER_SOURCE  = "trigger_source";
    public static final String SOURCE_SHAKE           = "shake";

    // ── Cancel action (sent from notification "Cancel" button) ────────────────
    public static final String ACTION_CANCEL_SOS = "com.example.femora.ACTION_CANCEL_SOS";

    // ── Runtime state ─────────────────────────────────────────────────────────
    private SensorManager sensorManager;
    private Sensor        accelerometer;
    private Vibrator      vibrator;
    private Handler       handler;

    private int  shakeCount     = 0;
    private long firstShakeTime = 0L;
    private long lastShakeTime  = 0L;

    private float activeThreshold = THRESHOLD_MEDIUM;

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        handler       = new Handler(Looper.getMainLooper());
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator      = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        loadSensitivitySetting();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            Log.d(TAG, "Accelerometer registered — threshold=" + activeThreshold + " m/s²");
        } else {
            Log.w(TAG, "No accelerometer — stopping service");
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL_SOS.equals(intent.getAction())) {
            cancelCountdown();
        }
        // Re-read sensitivity in case it was changed while service was running
        loadSensitivitySetting();
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        Log.d(TAG, "Shake detection stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─────────────────────────────────────────────────────────────────────────
    // Sensitivity
    // ─────────────────────────────────────────────────────────────────────────

    private void loadSensitivitySetting() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String level = prefs.getString(PREF_SHAKE_SENSITIVITY, SENSITIVITY_MEDIUM);
        switch (level) {
            case SENSITIVITY_LOW:  activeThreshold = THRESHOLD_LOW;  break;
            case SENSITIVITY_HIGH: activeThreshold = THRESHOLD_HIGH; break;
            default:               activeThreshold = THRESHOLD_MEDIUM;
        }
    }

    /** Called from ProfileActivity after the user picks a new level. */
    public static void saveSensitivity(Context ctx, String level) {
        ctx.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putString(PREF_SHAKE_SENSITIVITY, level).apply();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SensorEventListener
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        if (sosCountdownActive) return; // Don't stack triggers during countdown

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double netForce = Math.sqrt(x * x + y * y + z * z);

        // ── Stationary-bias low-pass update ───────────────────────────────────
        gravityAvg = GRAVITY_ALPHA * (float) netForce + (1 - GRAVITY_ALPHA) * gravityAvg;

        // Reject if the phone appears to be resting (force hasn't deviated enough)
        if (Math.abs(netForce - gravityAvg) < STATIONARY_EPSILON && netForce < activeThreshold) return;

        if (netForce < activeThreshold) return;

        long now = System.currentTimeMillis();
        if (now - lastShakeTime < SHAKE_INTERVAL_MS) return;
        lastShakeTime = now;

        if (shakeCount == 0 || now - firstShakeTime > SHAKE_WINDOW_MS) {
            shakeCount     = 1;
            firstShakeTime = now;
        } else {
            shakeCount++;
        }

        Log.d(TAG, "Shake #" + shakeCount + " (force=" + String.format("%.1f", netForce) + " m/s²)");

        // Broadcast live progress to UI
        broadcastShakeProgress(shakeCount);

        // Short haptic tick on each shake
        vibrateOnce(80);

        if (shakeCount >= SHAKE_COUNT_TRIGGER) {
            shakeCount = 0;
            beginSOSCountdown();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // ─────────────────────────────────────────────────────────────────────────
    // SOS countdown
    // ─────────────────────────────────────────────────────────────────────────

    private void beginSOSCountdown() {
        sosCountdownActive = true;
        Log.i(TAG, "SOS countdown started — " + CANCEL_WINDOW_MS + " ms to cancel");

        // Strong haptic burst to alert the user
        vibratePattern(new long[]{0, 300, 100, 300});

        // Broadcast → activity shows "SOS in 5 s — Tap to cancel"
        Intent cdIntent = new Intent(ACTION_SOS_COUNTDOWN);
        cdIntent.putExtra(EXTRA_COUNTDOWN_MS, CANCEL_WINDOW_MS);
        cdIntent.setPackage(getPackageName());
        sendBroadcast(cdIntent);

        // Update notification with Cancel button
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildCountdownNotification());

        handler.postDelayed(this::fireSOS, CANCEL_WINDOW_MS);
    }

    private void cancelCountdown() {
        if (!sosCountdownActive) return;
        sosCountdownActive = false;
        handler.removeCallbacksAndMessages(null);
        Log.i(TAG, "SOS countdown cancelled by user");
        updateNotification();

        // One short vibration confirms cancellation
        vibrateOnce(150);
    }

    private void fireSOS() {
        if (!sosCountdownActive) return; // User cancelled
        sosCountdownActive = false;
        Log.i(TAG, "🚨 SOS triggered by shake detection!");

        // Final long vibration to confirm dispatch
        vibratePattern(new long[]{0, 400, 150, 400, 150, 600});

        Intent sosIntent = new Intent(ACTION_SOS_TRIGGERED);
        sosIntent.putExtra(EXTRA_TRIGGER_SOURCE, SOURCE_SHAKE);
        sosIntent.setPackage(getPackageName());
        sendBroadcast(sosIntent);

        updateNotification();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Progress broadcast
    // ─────────────────────────────────────────────────────────────────────────

    private void broadcastShakeProgress(int count) {
        Intent intent = new Intent(ACTION_SHAKE_PROGRESS);
        intent.putExtra(EXTRA_SHAKE_COUNT, count);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Haptic helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void vibrateOnce(long ms) {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void vibratePattern(long[] pattern) {
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        } else {
            vibrator.vibrate(pattern, -1);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Shake Detection", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps shake-to-SOS active in the background");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private String sensitivityLabel() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String level = prefs.getString(PREF_SHAKE_SENSITIVITY, SENSITIVITY_MEDIUM);
        switch (level) {
            case SENSITIVITY_LOW:  return "Low sensitivity";
            case SENSITIVITY_HIGH: return "High sensitivity";
            default:               return "Medium sensitivity";
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, ProfileActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Shake Detection Active")
                .setContentText("Shake 3× to trigger SOS  •  " + sensitivityLabel())
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /** Notification shown during the 5-second cancellation window. */
    private Notification buildCountdownNotification() {
        Intent cancelIntent = new Intent(this, ShakeDetectionService.class);
        cancelIntent.setAction(ACTION_CANCEL_SOS);
        PendingIntent cancelPi = PendingIntent.getService(this, 1, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⚠️ SOS Sending in 5 seconds…")
                .setContentText("Tap CANCEL if this was accidental")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "CANCEL", cancelPi)
                .setOngoing(true)
                .setSilent(false)   // audible for countdown
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
    }
}
package com.example.femora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * VoiceActivationService  —  Femora Women Safety
 * ───────────────────────────────────────────────
 * Foreground service that continuously listens for distress keywords and fires
 * the SOS broadcast when one is detected.
 *
 * NEW in this version
 * ───────────────────
 *  • Greatly expanded multilingual keyword table (English, Hindi, Punjabi,
 *    Urdu, Tamil, Bengali) stored as a typed enum-style constant list.
 *  • Confidence gating: partial results with a very low confidence score are
 *    skipped to reduce false positives in noisy environments.
 *  • SOS cooldown lock (SOS_COOLDOWN_MS): after one trigger the service stops
 *    listening for a configurable period so double-fires cannot happen.
 *  • "Listening pulse" broadcast (ACTION_VOICE_PULSE) so the UI can animate a
 *    microphone ring while the service is active.
 *  • Microphone state broadcast (ACTION_MIC_STATE) with EXTRA_MIC_ACTIVE=true/false
 *    so ProfileActivity can update the status chip in real-time.
 *  • Minimum confidence threshold constant; results below it are discarded.
 */
public class VoiceActivationService extends Service {

    private static final String TAG = "VoiceActivationService";

    // ── Keyword table ─────────────────────────────────────────────────────────
    /**
     * Each entry is a lower-case phrase. Partial-result scanning stops at the
     * first match in any recognition session (one SOS per session).
     *
     * Languages covered: English, Hindi, Punjabi, Urdu, Tamil, Bengali, Marathi.
     */
    private static final List<String> TRIGGER_KEYWORDS = Arrays.asList(
            // ── English ──────────────────────────────────────────────────────
            "help",
            "help me",
            "danger",
            "emergency",
            "save me",
            "sos",
            "call police",
            "call 112",
            "let me go",
            "stop",
            "leave me alone",
            // ── Hindi ─────────────────────────────────────────────────────────
            "madad",          // help
            "madad karo",     // help me / do help
            "bachao",         // save me
            "bachao mujhe",   // save me please
            "koi hai",        // is anyone there
            "chodo mujhe",    // leave me alone
            "police bulao",   // call police
            "khatara",        // danger
            "musibat",        // trouble
            // ── Punjabi ──────────────────────────────────────────────────────
            "madat",          // help
            "madat karo",     // help me
            "bachao",         // save me (same spelling, different phoneme in Punjabi)
            "khatra",         // danger
            "chhado",         // leave me
            // ── Urdu ─────────────────────────────────────────────────────────
            "imdad",          // help
            "mujhe bachao",   // save me
            "khatrah",        // danger
            // ── Tamil ────────────────────────────────────────────────────────
            "udhavi",         // help
            "apadam",         // danger / distress
            "kaapadu",        // save me
            // ── Bengali ──────────────────────────────────────────────────────
            "sahajjo",        // help
            "bachao",         // save me
            "bipod",          // danger
            // ── Marathi ──────────────────────────────────────────────────────
            "maddat kara",    // help me
            "wachva"          // save me
    );

    // ── Confidence gating ─────────────────────────────────────────────────────
    /**
     * SpeechRecognizer can return a confidence float[] via CONFIDENCE_SCORES.
     * We discard results whose best score is below this threshold when available.
     * Range 0.0–1.0; 0.0 means accept all (no gating).
     */
    private static final float MIN_CONFIDENCE = 0.25f;

    // ── Timings ───────────────────────────────────────────────────────────────
    /** Gap before re-creating the recognizer after each session (ms). */
    private static final long RESTART_DELAY_MS = 1_000L;
    /** How long to pause listening after an SOS fires (ms). */
    private static final long SOS_COOLDOWN_MS  = 8_000L;

    // ── Notification ──────────────────────────────────────────────────────────
    public static final String CHANNEL_ID = "voice_activation_channel";
    public static final int    NOTIF_ID   = 1002;

    // ── Broadcasts ────────────────────────────────────────────────────────────
    /** Sent when microphone listening state changes. */
    public static final String ACTION_MIC_STATE  = "com.example.femora.ACTION_MIC_STATE";
    public static final String EXTRA_MIC_ACTIVE  = "mic_active";
    /** Heartbeat sent every ~2 s while listening, for the pulse UI ring. */
    public static final String ACTION_VOICE_PULSE = "com.example.femora.ACTION_VOICE_PULSE";

    public static final String SOURCE_VOICE = "voice";

    // ── Runtime state ─────────────────────────────────────────────────────────
    private SpeechRecognizer speechRecognizer;
    private Intent           recognizerIntent;
    private Handler          mainHandler;
    private Vibrator         vibrator;

    private boolean isListening    = false;
    private boolean serviceRunning = false;
    private boolean inCooldown     = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Service lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        vibrator    = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition unavailable — stopping");
            stopSelf();
            return;
        }

        serviceRunning = true;
        setupRecognizerIntent();
        mainHandler.post(this::createAndStartRecognizer);

        // Pulse heartbeat every 2 s
        schedulePulse();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceRunning = false;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
        });
        broadcastMicState(false);
        Log.d(TAG, "Voice activation stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─────────────────────────────────────────────────────────────────────────
    // Pulse heartbeat
    // ─────────────────────────────────────────────────────────────────────────

    private void schedulePulse() {
        mainHandler.postDelayed(() -> {
            if (!serviceRunning) return;
            if (isListening) {
                Intent pulse = new Intent(ACTION_VOICE_PULSE);
                pulse.setPackage(getPackageName());
                sendBroadcast(pulse);
            }
            schedulePulse();
        }, 2_000L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Recognizer setup
    // ─────────────────────────────────────────────────────────────────────────

    private void setupRecognizerIntent() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        // Keep session open longer; keyword may come after some ambient noise
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3_000L);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_000L);
    }

    /** Must be called on the main thread. */
    private void createAndStartRecognizer() {
        if (!serviceRunning || inCooldown) return;

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            @Override
            public void onReadyForSpeech(Bundle params) {
                isListening = true;
                broadcastMicState(true);
                Log.d(TAG, "Listening for keywords…");
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (inCooldown) return;
                List<String> partial = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                // Partial results don't have confidence scores; check all
                if (partial != null) checkForKeywords(partial, null);
            }

            @Override
            public void onResults(Bundle results) {
                isListening = false;
                broadcastMicState(false);
                if (!inCooldown) {
                    List<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION);
                    float[] confidences = results.getFloatArray(
                            SpeechRecognizer.CONFIDENCE_SCORES);
                    if (matches != null) checkForKeywords(matches, confidences);
                }
                scheduleRestart();
            }

            @Override
            public void onError(int error) {
                isListening = false;
                broadcastMicState(false);
                Log.d(TAG, "Recognizer error " + error + " — restarting");
                scheduleRestart();
            }

            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { isListening = false; broadcastMicState(false); }
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        startListening();
    }

    private void startListening() {
        if (!serviceRunning || speechRecognizer == null || isListening || inCooldown) return;
        try {
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            Log.e(TAG, "startListening failed: " + e.getMessage());
            scheduleRestart();
        }
    }

    private void scheduleRestart() {
        mainHandler.postDelayed(() -> {
            if (!serviceRunning || inCooldown) return;
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
                speechRecognizer = null;
            }
            createAndStartRecognizer();
        }, RESTART_DELAY_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Keyword detection with confidence gating
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param results     Recognition result strings (may be multiple hypotheses).
     * @param confidences Per-result confidence scores, or null for partials.
     */
    private void checkForKeywords(List<String> results, float[] confidences) {
        for (int i = 0; i < results.size(); i++) {
            // Confidence gate — skip low-confidence full results
            if (confidences != null && i < confidences.length) {
                if (confidences[i] < MIN_CONFIDENCE) {
                    Log.d(TAG, "Skipped low-confidence result (score=" + confidences[i] + ")");
                    continue;
                }
            }

            String lower = results.get(i).toLowerCase(Locale.getDefault());
            for (String keyword : TRIGGER_KEYWORDS) {
                if (lower.contains(keyword)) {
                    Log.i(TAG, "🚨 Keyword detected: \"" + keyword + "\" in \"" + results.get(i) + "\"");
                    triggerSOS(keyword);
                    return;
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SOS trigger with cooldown
    // ─────────────────────────────────────────────────────────────────────────

    private void triggerSOS(String detectedKeyword) {
        inCooldown = true;

        // Stop recognizer immediately
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        isListening = false;
        broadcastMicState(false);

        // Three quick haptic pulses → SOS confirmed
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                        new long[]{0, 300, 100, 300, 100, 300}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 300, 100, 300, 100, 300}, -1);
            }
        }

        // Broadcast SOS
        Intent sosIntent = new Intent(ShakeDetectionService.ACTION_SOS_TRIGGERED);
        sosIntent.putExtra(ShakeDetectionService.EXTRA_TRIGGER_SOURCE, SOURCE_VOICE);
        sosIntent.putExtra("detected_keyword", detectedKeyword);
        sosIntent.setPackage(getPackageName());
        sendBroadcast(sosIntent);

        // Resume listening after cooldown
        mainHandler.postDelayed(() -> {
            inCooldown = false;
            scheduleRestart();
        }, SOS_COOLDOWN_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State broadcasts
    // ─────────────────────────────────────────────────────────────────────────

    private void broadcastMicState(boolean active) {
        Intent intent = new Intent(ACTION_MIC_STATE);
        intent.putExtra(EXTRA_MIC_ACTIVE, active);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "Voice Activation", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Listens for distress keywords to trigger SOS");
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, ProfileActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Voice Activation Active")
                .setContentText("Say \u201cHelp\u201d, \u201cBachao\u201d or \u201cMadad\u201d to trigger SOS")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
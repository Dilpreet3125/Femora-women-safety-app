package com.example.femora;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * VoiceHelper — Femora Women Safety App
 * ───────────────────────────────────────────────────────────────────────────
 * Merged from two versions:
 *
 *  Version A (uploaded file)  — multilingual keyword list, confidence
 *                               threshold filtering, state callbacks,
 *                               auto-restart, thread-safe destroy.
 *
 *  Version B (pasted code)    — detailed error logging, getErrorText(),
 *                               busy-recognizer cancel-and-retry, simpler
 *                               constructor signature.
 *
 * This merged class keeps EVERYTHING from both versions with zero conflicts.
 * ───────────────────────────────────────────────────────────────────────────
 */
public class VoiceHelper {

    private static final String TAG = "VoiceHelper";

    // ── Multilingual keyword list (from Version A — comprehensive) ────────────
    private static final List<String> TRIGGER_KEYWORDS = Arrays.asList(
            // English
            "help", "help me", "danger", "emergency", "save me", "sos",
            "call police", "call 112", "let me go", "stop", "leave me alone",
            "in danger",
            // Hindi
            "madad", "madad karo", "bachao", "bachao mujhe",
            "koi hai", "chodo mujhe", "police bulao", "khatara", "musibat",
            // Punjabi
            "madat", "madat karo", "khatra", "chhado",
            // Urdu
            "imdad", "mujhe bachao", "khatrah",
            // Tamil
            "udhavi", "apadam", "kaapadu",
            // Bengali
            "sahajjo", "bipod",
            // Marathi
            "maddat kara", "wachva"
    );

    /**
     * Minimum confidence score to accept a final result (0.0 = accept all).
     * Partial results always bypass this filter for faster response.
     */
    private static final float MIN_CONFIDENCE = 0.25f;

    // ─────────────────────────────────────────────────────────────────────────
    // Callback interfaces
    // ─────────────────────────────────────────────────────────────────────────

    /** Called when any trigger keyword is detected. */
    public interface OnKeywordDetectedListener {
        void onKeywordDetected(String keyword);
    }

    /**
     * Optional UI state callbacks — use these to animate a mic icon
     * on your safety settings / profile screen.
     */
    public interface OnStateChangeListener {
        void onListeningStarted();
        void onListeningStopped();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fields
    // ─────────────────────────────────────────────────────────────────────────
    private final Context                   context;
    private final Handler                   mainHandler;
    private final OnKeywordDetectedListener keywordListener;

    private OnStateChangeListener stateListener;
    private SpeechRecognizer      speechRecognizer;
    private Intent                recognizerIntent;

    private boolean autoRestart = true;   // default ON — keeps listening continuously
    private boolean destroyed   = false;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Standard constructor — matches both Version A and Version B signatures.
     *
     * @param context  Any context — application context is stored internally.
     * @param listener Called when a trigger keyword is detected.
     */
    public VoiceHelper(Context context, OnKeywordDetectedListener listener) {
        this.context         = context.getApplicationContext();
        this.keywordListener = listener;
        this.mainHandler     = new Handler(Looper.getMainLooper());
        // Init on main thread (SpeechRecognizer requires it)
        mainHandler.post(this::initSpeechRecognizer);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Configuration (call before startListening)
    // ─────────────────────────────────────────────────────────────────────────

    /** Attach optional UI animation callbacks. */
    public void setStateChangeListener(OnStateChangeListener listener) {
        this.stateListener = listener;
    }

    /**
     * Control continuous listening mode.
     * Default is true — set false if you only want a single session.
     */
    public void setAutoRestart(boolean autoRestart) {
        this.autoRestart = autoRestart;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialisation (runs on main thread)
    // ─────────────────────────────────────────────────────────────────────────

    private void initSpeechRecognizer() {
        if (destroyed) return;

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition NOT available on this device.");
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                context.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {

            // ── Session started ───────────────────────────────────────────
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "onReadyForSpeech — mic is live");
                if (stateListener != null) stateListener.onListeningStarted();
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
                if (stateListener != null) stateListener.onListeningStopped();
            }

            // ── Results ───────────────────────────────────────────────────
            @Override
            public void onPartialResults(Bundle partialResults) {
                // Partial results — no confidence filtering (speed matters)
                List<String> matches = partialResults.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    Log.d(TAG, "Partial: " + matches);
                    checkKeywords(matches, null);
                }
            }

            @Override
            public void onResults(Bundle results) {
                Log.d(TAG, "onResults (final)");
                if (stateListener != null) stateListener.onListeningStopped();

                List<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                float[] confidences = results.getFloatArray(
                        SpeechRecognizer.CONFIDENCE_SCORES);

                if (matches != null) {
                    Log.d(TAG, "Final results: " + matches);
                    checkKeywords(matches, confidences);
                }

                // Auto-restart for continuous background listening
                if (autoRestart && !destroyed) {
                    startListening();
                }
            }

            // ── Errors — from Version B: detailed logging + smart recovery ─
            @Override
            public void onError(int error) {
                String msg = getErrorText(error);
                Log.e(TAG, "onError: " + msg + " (code " + error + ")");

                if (stateListener != null) stateListener.onListeningStopped();

                // If recognizer is busy — cancel first, then retry
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    if (speechRecognizer != null) speechRecognizer.cancel();
                }

                // Restart on all errors except client-side errors
                if (error != SpeechRecognizer.ERROR_CLIENT && autoRestart && !destroyed) {
                    mainHandler.postDelayed(VoiceHelper.this::startListening, 500L);
                }
            }

            // ── Unused callbacks ──────────────────────────────────────────
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Keyword matching — multilingual, with optional confidence filter
    // ─────────────────────────────────────────────────────────────────────────

    private void checkKeywords(List<String> results, float[] confidences) {
        for (int i = 0; i < results.size(); i++) {

            // Apply confidence filter only to final results (confidences != null)
            if (confidences != null && i < confidences.length
                    && confidences[i] < MIN_CONFIDENCE) {
                continue;
            }

            String lower = results.get(i).toLowerCase(Locale.getDefault());
            Log.d(TAG, "Checking: \"" + lower + "\"");

            for (String keyword : TRIGGER_KEYWORDS) {
                if (lower.contains(keyword)) {
                    Log.i(TAG, "🚨 Emergency keyword DETECTED: \"" + lower
                            + "\" matched keyword: \"" + keyword + "\"");
                    if (keywordListener != null) {
                        keywordListener.onKeywordDetected(results.get(i));
                    }
                    return; // One detection per result set is enough
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start a listening session. Thread-safe — can be called from any thread.
     * If the recognizer is busy it cancels first then restarts.
     */
    public void startListening() {
        mainHandler.post(() -> {
            if (destroyed || speechRecognizer == null) return;
            try {
                speechRecognizer.startListening(recognizerIntent);
                Log.d(TAG, "startListening() called");
            } catch (Exception e) {
                Log.e(TAG, "startListening failed: " + e.getMessage());
                // Cancel stuck session and retry once
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Stop the current session without releasing resources.
     * Call this when the user toggles voice SOS off temporarily.
     */
    public void stopListening() {
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                Log.d(TAG, "stopListening() called");
            }
            if (stateListener != null) stateListener.onListeningStopped();
        });
    }

    /**
     * Release ALL resources. Call from onDestroy() or onPause().
     * Thread-safe — safe to call from any thread.
     * After destroy() this instance cannot be reused.
     */
    public void destroy() {
        destroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.stopListening();
                speechRecognizer.destroy();
                speechRecognizer = null;
                Log.d(TAG, "SpeechRecognizer destroyed.");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error text helper — from Version B
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a SpeechRecognizer error code to a human-readable string.
     * Useful for debugging and crash reporting.
     */
    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions (add RECORD_AUDIO to Manifest)";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "RecognitionService busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input (timeout)";
            default:
                return "Unknown error (code " + errorCode + ")";
        }
    }
}
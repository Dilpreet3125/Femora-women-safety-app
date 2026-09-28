package com.example.femora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class PowerButtonReceiver extends BroadcastReceiver {
    private static int count = 0;
    private static final long RESET_TIME = 3000; // 3 seconds window
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Runnable resetRunnable = () -> {
        count = 0;
        Log.d("PowerButtonReceiver", "Counter reset to 0");
    };

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_SCREEN_OFF.equals(action)) {
            count++;
            Log.d("PowerButtonReceiver", "Button press detected. Count: " + count);

            // Remove existing reset timer and start a new one
            handler.removeCallbacks(resetRunnable);
            handler.postDelayed(resetRunnable, RESET_TIME);

            if (count >= 3) {
                count = 0;
                handler.removeCallbacks(resetRunnable);
                Log.d("PowerButtonReceiver", "3 presses threshold reached! Launching SOS...");
                
                // Launch MainActivity with SOS trigger flag
                Intent sosIntent = new Intent(context, MainActivity.class);
                sosIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | 
                                 Intent.FLAG_ACTIVITY_CLEAR_TOP | 
                                 Intent.FLAG_ACTIVITY_SINGLE_TOP);
                sosIntent.putExtra("trigger_sos", true);
                context.startActivity(sosIntent);
            }
        }
    }
}

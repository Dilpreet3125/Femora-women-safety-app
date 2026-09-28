package com.example.femora;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class BatteryReceiver extends BroadcastReceiver {
    private static boolean alerted = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BATTERY_CHANGED.equals(action) || Intent.ACTION_BATTERY_LOW.equals(action)) {
            
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct;
            
            if (level != -1 && scale != -1) {
                batteryPct = (level / (float) scale) * 100;
            } else {
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }

            Log.d("BatteryReceiver", "Battery Level: " + batteryPct + "%");

            // Trigger alert when battery drops to 10% or below
            if (batteryPct <= 10 && batteryPct > 0 && !alerted) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    sendLowBatteryAlert(context, (int) batteryPct);
                    alerted = true;
                } else {
                    Log.e("BatteryReceiver", "SMS Permission not granted for low battery alert");
                }
            } else if (batteryPct > 15) {
                alerted = false; // Reset when battery is charged enough
            }
        }
    }

    private void sendLowBatteryAlert(Context context, int percentage) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        // Exact message as requested
        String message = "Low Battery Alert: My phone battery is less than 10% (" + percentage + "%) and my phone may power off soon. - Sent via Femora";

        if (mAuth.getCurrentUser() == null) {
            sendOfflineAlert(context, message);
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
        DatabaseReference contactsRef = FirebaseDatabase.getInstance(dbUrl)
                .getReference("users")
                .child(userId)
                .child("emergencyContacts");

        contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChildren()) {
                    int sentCount = 0;
                    for (DataSnapshot contact : snapshot.getChildren()) {
                        String phone = contact.child("number").getValue(String.class);
                        if (phone != null && !phone.isEmpty()) {
                            if (sendSMS(context, phone, message)) {
                                sentCount++;
                            }
                        }
                    }
                    Log.d("BatteryReceiver", "Battery alerts sent to " + sentCount + " contacts");
                } else {
                    sendOfflineAlert(context, message);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                sendOfflineAlert(context, message);
            }
        });
    }

    private void sendOfflineAlert(Context context, String message) {
        SharedPreferences pref = context.getSharedPreferences("FemoraContacts", Context.MODE_PRIVATE);
        String cachedData = pref.getString("cached_contacts", "");

        if (!cachedData.isEmpty()) {
            int sentCount = 0;
            String[] contacts = cachedData.split(";");
            for (String contact : contacts) {
                if (contact.contains(":")) {
                    String phone = contact.split(":")[1];
                    if (sendSMS(context, phone, message)) {
                        sentCount++;
                    }
                }
            }
            Log.d("BatteryReceiver", "Battery alerts sent to " + sentCount + " contacts (Offline)");
        }
    }

    private boolean sendSMS(Context context, String phone, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager != null) {
                smsManager.sendTextMessage(phone.trim(), null, message, null, null);
                return true;
            }
        } catch (Exception e) {
            Log.e("BatteryReceiver", "SMS failed: " + e.getMessage());
        }
        return false;
    }
}

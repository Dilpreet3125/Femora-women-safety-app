package com.example.femora;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;

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
        if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level * 100 / (float) scale;

            Log.d("BatteryReceiver", "Battery Level: " + batteryPct);

            if (batteryPct <= 10 && batteryPct > 0 && !alerted) {
                sendLowBatteryAlert(context);
                alerted = true;
            } else if (batteryPct > 15) {
                alerted = false; // Reset when battery is charged
            }
        }
    }

    private void sendLowBatteryAlert(Context context) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
        DatabaseReference contactsRef = FirebaseDatabase.getInstance(dbUrl)
                .getReference("emergency_contacts")
                .child(userId);

        contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String message = "Low Battery Alert: My phone is at 10% battery. I may be unreachable soon. - Sent via Femora";
                    for (DataSnapshot contact : snapshot.getChildren()) {
                        String phone = contact.child("phone").getValue(String.class);
                        if (phone != null && !phone.isEmpty()) {
                            try {
                                SmsManager smsManager;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    smsManager = context.getSystemService(SmsManager.class);
                                } else {
                                    smsManager = SmsManager.getDefault();
                                }
                                smsManager.sendTextMessage(phone, null, message, null, null);
                                Log.d("BatteryReceiver", "SMS sent to: " + phone);
                            } catch (Exception e) {
                                Log.e("BatteryReceiver", "SMS failed: " + e.getMessage());
                            }
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}

package com.example.femora;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SosService extends Service {
    private static final String CHANNEL_ID = "SosServiceChannel";
    private static final int NOTIFICATION_ID = 123;
    private static final String ACTION_STOP_SERVICE = "STOP_SOS_SERVICE";
    
    private BatteryReceiver batteryReceiver;
    private FusedLocationProviderClient fusedLocationClient;
    private VoiceHelper voiceHelper;
    private BroadcastReceiver screenStateReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        createNotificationChannel();
        registerBatteryReceiver();
        registerScreenStateReceiver();
        initVoiceSOS();
    }

    private void registerScreenStateReceiver() {
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (voiceHelper != null) {
                    // Re-ensure voice recognition is active when screen state changes
                    voiceHelper.startListening();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenStateReceiver, filter);
    }

    private void initVoiceSOS() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (voiceHelper != null) voiceHelper.destroy();
            voiceHelper = new VoiceHelper(this, keyword -> {
                Log.d("SosService", "Voice Trigger Detected: " + keyword);
                vibrate(1000);
                new Handler(Looper.getMainLooper()).post(() -> 
                    Toast.makeText(this, "🚨 VOICE SOS TRIGGERED: " + keyword, Toast.LENGTH_LONG).show());
                triggerSos();
            });
            voiceHelper.startListening();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Re-check permissions and initialize if granted later
        if (voiceHelper == null) {
            initVoiceSOS();
        }

        Intent stopIntent = new Intent(this, SosService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Femora Security")
                .setContentText("Femora background service is running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off", stopPendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    private void registerBatteryReceiver() {
        batteryReceiver = new BatteryReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        registerReceiver(batteryReceiver, filter);
    }

    private void vibrate(int duration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(duration);
            }
        }
    }

    private void triggerSos() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
            String message;
            if (location != null) {
                message = "🚨 SOS EMERGENCY!!! I am in danger.\n"
                        + "My live location: https://www.google.com/maps/search/?api=1&query="
                        + location.getLatitude() + "," + location.getLongitude()
                        + "\n- Sent via Femora";
            } else {
                String lastLat = pref.getString("last_lat", null);
                String lastLng = pref.getString("last_lng", null);
                if (lastLat != null && lastLng != null) {
                    message = "🚨 SOS EMERGENCY!!! I am in danger.\n"
                            + "My last known location: https://www.google.com/maps/search/?api=1&query="
                            + lastLat + "," + lastLng
                            + "\n- Sent via Femora (Cached)";
                } else {
                    message = "🚨 SOS EMERGENCY!!! I need help immediately. (Location unavailable) - Sent via Femora";
                }
            }
            fetchContactsAndSendSMS(message);
        });
    }

    private void fetchContactsAndSendSMS(String message) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            sendSOSOffline(message);
            return;
        }
        String userId = mAuth.getCurrentUser().getUid();
        DatabaseReference contactsRef = FirebaseDatabase.getInstance("https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(userId).child("emergencyContacts");

        contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.hasChildren()) {
                    for (DataSnapshot contact : snapshot.getChildren()) {
                        String phone = contact.child("number").getValue(String.class);
                        if (phone != null && !phone.isEmpty()) sendSingleSMS(phone, message);
                    }
                } else {
                    sendSOSOffline(message);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                sendSOSOffline(message);
            }
        });
    }

    private void sendSOSOffline(String message) {
        SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
        String cachedData = pref.getString("cached_contacts", "");
        if (!cachedData.isEmpty()) {
            for (String contact : cachedData.split(";")) {
                if (contact.contains(":")) sendSingleSMS(contact.split(":")[1], message);
            }
        }
    }

    private void sendSingleSMS(String phone, String message) {
        try {
            SmsManager smsManager = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? 
                    getSystemService(SmsManager.class) : SmsManager.getDefault();
            smsManager.sendTextMessage(phone.trim(), null, message, null, null);
        } catch (Exception e) {
            Log.e("SosService", "SMS failed: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SOS Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (batteryReceiver != null) unregisterReceiver(batteryReceiver);
        if (screenStateReceiver != null) unregisterReceiver(screenStateReceiver);
        if (voiceHelper != null) voiceHelper.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}

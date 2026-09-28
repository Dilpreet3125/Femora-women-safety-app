package com.example.femora;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Femora_Main";

    // ── Constants ─
    private static final String DB_URL =
            "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String WEB_CLIENT_ID =
            "704911599115-c2pg5oot9n3e7l6qob4p825lb4tftt6b.apps.googleusercontent.com";
    private static final int PERMISSION_REQUEST_SOS   = 1;
    private static final int PERMISSION_REQUEST_VOICE = 2;

    // ── Views
    private TextView tvWelcome, tvUserName;
    private CardView profileButton, trustedContactsCard, nearbyHelpCard,
    liveLocationCard, voiceSosCard, lowBatteryCard, offlineSmsCard;
    private View  btnSOSContainer;
    private ImageView ivMainProfile;
    private BottomNavigationView bottomNavigationView;

    // ── Firebase / Auth / Location
    private FirebaseAuth   mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private FusedLocationProviderClient fusedLocationClient;

    // ── SOS & Voice
    private VoiceHelper    voiceHelper;
    private CountDownTimer sosTimer;

    // ── Battery Receiver (Version A)
    private BatteryReceiver batteryReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Firebase & Location init
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // 2. Security: user must be logged in and email-verified (from Version B)
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || !user.isEmailVerified()) {
            signOutUser();
            return;
        }

        // 3. Google Sign-In client — using hardcoded ID (avoids getString crash)
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // 4. UI Bindings
        initViews();

        // 5. Dashboard data
        setGreeting();
        setUserName();
        loadProfileImage();
        cacheContactsLocally();

        // 6. Click listeners
        setupClickListeners();

        // 7. Voice SOS init
        initVoiceSOS();

        // 8. Battery monitoring (from Version A)
        setupBatteryReceiver();

        // 9. Start background SOS service (from Version B)
        startSosService();
    }


    private void initViews() {
        tvWelcome           = findViewById(R.id.tvWelcome);
        tvUserName          = findViewById(R.id.tvUserName);
        profileButton       = findViewById(R.id.profile_button);
        ivMainProfile       = findViewById(R.id.ivMainProfile);
        trustedContactsCard = findViewById(R.id.trustedContactsCard);
        liveLocationCard    = findViewById(R.id.liveLocationCard);
        nearbyHelpCard      = findViewById(R.id.nearbyHelpCard);
        voiceSosCard        = findViewById(R.id.voiceSosCard);
        lowBatteryCard      = findViewById(R.id.lowBatteryCard);
        offlineSmsCard      = findViewById(R.id.offlineSmsCard);
        btnSOSContainer     = findViewById(R.id.btnSOSContainer);
        bottomNavigationView = findViewById(R.id.bottom_navigation);
    }


    private void setupClickListeners() {

        // ── SOS button
        if (btnSOSContainer != null)
            btnSOSContainer.setOnClickListener(v -> startSosTimer());

        // ── Profile button + image
        View.OnClickListener goToProfile = v ->
                startActivity(new Intent(this, ProfileActivity.class));

        if (profileButton != null)
            profileButton.setOnClickListener(goToProfile);

        if (ivMainProfile != null) {
            ivMainProfile.setOnClickListener(goToProfile);
            // Long-press to sign out quickly (from Version A)
            ivMainProfile.setOnLongClickListener(v -> {
                signOutUser();
                return true;
            });
        }

        // ── Safety feature cards
        if (trustedContactsCard != null)
            trustedContactsCard.setOnClickListener(v ->
                    startActivity(new Intent(this, EmergencyContactActivity.class)));

        if (liveLocationCard != null)
            liveLocationCard.setOnClickListener(v ->
                    startActivity(new Intent(this, LiveLocationActivity.class)));

        if (nearbyHelpCard != null)
            nearbyHelpCard.setOnClickListener(v ->
                    startActivity(new Intent(this, NearbyHelpActivity.class)));

        // ── Voice SOS card — shows status toast or reinitialises
        if (voiceSosCard != null) {
            voiceSosCard.setOnClickListener(v -> {
                if (voiceHelper != null) {
                    Toast.makeText(this,
                            "Voice SOS is active. Say 'Help' or 'Danger' to trigger.",
                            Toast.LENGTH_LONG).show();
                } else {
                    initVoiceSOS();
                    Toast.makeText(this, "Voice SOS Activated", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // ── Low battery info
        if (lowBatteryCard != null) {
            lowBatteryCard.setOnClickListener(v ->
                    Toast.makeText(this,
                            "Automated alerts are active. Will trigger at 10% battery.",
                            Toast.LENGTH_LONG).show());
        }

        // ── Offline SMS card
        if (offlineSmsCard != null)
            offlineSmsCard.setOnClickListener(v -> startSosTimer());

        // ── Bottom Navigation
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_tips) {
                    startActivity(new Intent(this, SafetyTipsActivity.class));
                    return true;
                } else if (id == R.id.nav_profile) {
                    startActivity(new Intent(this, ProfileActivity.class));
                    return true;
                } else if (id == R.id.nav_map) {
                    startActivity(new Intent(this, MapActivity.class));
                    return true;
                }
                return true;
            });
        }
    }

    public void signOutUser() {
        if (mAuth != null) mAuth.signOut();
        if (mGoogleSignInClient != null) {
            mGoogleSignInClient.signOut().addOnCompleteListener(this,
                    task -> navigateToLogin());
        } else {
            navigateToLogin();
        }
    }

    private void navigateToLogin() {
        getSharedPreferences("UserData",       MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("FemoraContacts", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }


    private void setGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if      (hour < 12) greeting = "Good Morning,";
        else if (hour < 16) greeting = "Good Afternoon,";
        else if (hour < 21) greeting = "Good Evening,";
        else                greeting = "Good Night,";
        if (tvWelcome != null) tvWelcome.setText(greeting);
    }

    private void setUserName() {
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
        String name = pref.getString("name", "User");
        if (tvUserName != null) tvUserName.setText(name);

        if (mAuth.getCurrentUser() != null) {
            FirebaseDatabase.getInstance(DB_URL)
                    .getReference("users")
                    .child(mAuth.getCurrentUser().getUid())
                    .child("name")
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String n = snapshot.getValue(String.class);
                                if (n != null && tvUserName != null) {
                                    tvUserName.setText(n);
                                    pref.edit().putString("name", n).apply();
                                }
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Name fetch cancelled: " + error.getMessage());
                        }
                    });
        }
    }

    // 🔴 FIXED METHOD: Handles restoring the gray tint and padding when the image is removed
    private void loadProfileImage() {
        if (ivMainProfile == null) return;
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);

        // 1. Show cached image immediately
        String cached = pref.getString("profile_image", "");
        if (!cached.isEmpty()) {
            try {
                byte[] decoded = Base64.decode(cached, Base64.DEFAULT);
                Bitmap bitmap  = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (bitmap != null) {
                    ivMainProfile.setImageBitmap(bitmap);
                    // Remove tint and padding for real photo
                    ivMainProfile.setPadding(0, 0, 0, 0);
                    ivMainProfile.clearColorFilter();
                }
            } catch (Exception e) {
                Log.e(TAG, "Cached image decode failed: " + e.getMessage());
            }
        } else {
            // Restore default gray icon if empty/deleted
            ivMainProfile.setImageResource(R.drawable.ic_profile);
            ivMainProfile.setPadding(10, 10, 10, 10);
            ivMainProfile.setColorFilter(android.graphics.Color.parseColor("#BDBDBD"));
        }

        // 2. Sync latest from Firebase
        if (mAuth.getCurrentUser() != null) {
            FirebaseDatabase.getInstance(DB_URL)
                    .getReference("users")
                    .child(mAuth.getCurrentUser().getUid())
                    .child("profileImage")
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                String encoded = snapshot.getValue(String.class);
                                if (encoded != null && !encoded.isEmpty()) {
                                    try {
                                        byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                                        Bitmap bitmap  = BitmapFactory.decodeByteArray(
                                                decoded, 0, decoded.length);
                                        if (bitmap != null) {
                                            ivMainProfile.setImageBitmap(bitmap);
                                            // Remove tint and padding for real photo
                                            ivMainProfile.setPadding(0, 0, 0, 0);
                                            ivMainProfile.clearColorFilter();
                                            pref.edit().putString("profile_image", encoded).apply();
                                        }
                                    } catch (Exception e) {
                                        Log.e(TAG, "Firebase image decode failed: " + e.getMessage());
                                    }
                                } else {
                                    // User removed the image from the cloud
                                    ivMainProfile.setImageResource(R.drawable.ic_profile);
                                    ivMainProfile.setPadding(10, 10, 10, 10);
                                    ivMainProfile.setColorFilter(android.graphics.Color.parseColor("#BDBDBD"));
                                }
                            } else {
                                // Node doesn't exist (user deleted image)
                                ivMainProfile.setImageResource(R.drawable.ic_profile);
                                ivMainProfile.setPadding(10, 10, 10, 10);
                                ivMainProfile.setColorFilter(android.graphics.Color.parseColor("#BDBDBD"));
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e(TAG, "Profile image fetch cancelled: " + error.getMessage());
                        }
                    });
        }
    }

    private void cacheContactsLocally() {
        if (mAuth.getCurrentUser() == null) return;
        FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(mAuth.getCurrentUser().getUid())
                .child("emergencyContacts")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            StringBuilder sb = new StringBuilder();
                            for (DataSnapshot contact : snapshot.getChildren()) {
                                String name  = contact.child("name").getValue(String.class);
                                String phone = contact.child("number").getValue(String.class);
                                if (phone != null && !phone.isEmpty())
                                    sb.append(name).append(":").append(phone).append(";");
                            }
                            getSharedPreferences("FemoraContacts", MODE_PRIVATE)
                                    .edit().putString("cached_contacts", sb.toString()).apply();
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Contact cache failed: " + error.getMessage());
                    }
                });
    }


    private void setupBatteryReceiver() {
        batteryReceiver = new BatteryReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(batteryReceiver, filter);
        }
    }


    private void startSosService() {
        Intent serviceIntent = new Intent(this, SosService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }


    private void initVoiceSOS() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            if (voiceHelper == null) {
                voiceHelper = new VoiceHelper(this, keyword -> {
                    Log.i(TAG, "Voice keyword triggered SOS: " + keyword);
                    runOnUiThread(this::startSosTimer);
                });
                voiceHelper.setAutoRestart(true);
                voiceHelper.startListening();
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST_VOICE);
        }
    }


    private void startSosTimer() {
        if (sosTimer != null) sosTimer.cancel();

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        View dialogView   = getLayoutInflater().inflate(R.layout.dialog_sos, null);
        TextView tvCountdown   = dialogView.findViewById(R.id.tvSosCountdown);
        View     sosCircleFrame = dialogView.findViewById(R.id.sos_circle_frame);

        Dialog dialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.setContentView(dialogView);
        dialog.setCancelable(true);

        dialog.setOnCancelListener(d -> {
            if (sosTimer  != null) sosTimer.cancel();
            if (vibrator  != null) vibrator.cancel();
            Toast.makeText(this, "SOS Cancelled", Toast.LENGTH_SHORT).show();
        });

        // Tap anywhere on dialog to cancel
        dialogView.setOnClickListener(v -> dialog.cancel());
        dialog.show();

        // Urgent repeating vibration pattern
        if (vibrator != null) {
            long[] pattern = {0, 300, 200, 300, 200};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }

        // 3-second animated countdown
        sosTimer = new CountDownTimer(3500, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int sec = (int)(millisUntilFinished / 1000);
                if (sec >= 1 && sec <= 3) {
                    if (tvCountdown != null) {
                        tvCountdown.setText(String.valueOf(sec));
                        tvCountdown.setScaleX(0.7f);
                        tvCountdown.setScaleY(0.7f);
                        tvCountdown.animate()
                                .scaleX(1.3f).scaleY(1.3f).setDuration(300)
                                .withEndAction(() -> tvCountdown.animate()
                                        .scaleX(1.0f).scaleY(1.0f).setDuration(200).start())
                                .start();
                    }
                    if (sosCircleFrame != null) {
                        sosCircleFrame.animate()
                                .scaleX(1.1f).scaleY(1.1f).setDuration(300)
                                .withEndAction(() -> sosCircleFrame.animate()
                                        .scaleX(1.0f).scaleY(1.0f).setDuration(200).start())
                                .start();
                    }
                }
            }

            @Override
            public void onFinish() {
                if (vibrator != null) vibrator.cancel();
                if (!isFinishing() && dialog.isShowing()) dialog.dismiss();
                sendSOS();
            }
        }.start();
    }

    private void sendSOS() {
        if (mAuth.getCurrentUser() == null) return;

        boolean hasLocation =
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        boolean hasSms = ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;

        if (!hasLocation || !hasSms) {
            ArrayList<String> perms = new ArrayList<>();
            if (!hasLocation) perms.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (!hasSms)      perms.add(Manifest.permission.SEND_SMS);
            ActivityCompat.requestPermissions(this,
                    perms.toArray(new String[0]), PERMISSION_REQUEST_SOS);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
            String message;

            if (location != null) {
                // Cache for offline fallback
                pref.edit()
                        .putString("last_lat", String.valueOf(location.getLatitude()))
                        .putString("last_lng", String.valueOf(location.getLongitude()))
                        .apply();
                message = "🚨 I am in danger!!! Please help me.\n"
                        + "My live location: https://www.google.com/maps/search/?api=1&query="
                        + location.getLatitude() + "," + location.getLongitude()
                        + "\nCoordinates: " + location.getLatitude()
                        + ", " + location.getLongitude()
                        + "\n- Sent via Femora";
            } else {
                String lastLat = pref.getString("last_lat", null);
                String lastLng = pref.getString("last_lng", null);
                if (lastLat != null && lastLng != null) {
                    message = "🚨 I am in danger!!! Please help me.\n"
                            + "My last known location: https://www.google.com/maps/search/?api=1&query="
                            + lastLat + "," + lastLng
                            + "\n- Sent via Femora (Cached Location)";
                } else {
                    message = "🚨 I am in danger!!! Please help me immediately. "
                            + "(Location unavailable) - Sent via Femora";
                }
            }
            fetchContactsAndSendSMS(message);
        });
    }

    private void fetchContactsAndSendSMS(String message) {
        if (mAuth.getCurrentUser() == null) return;
        Toast.makeText(this, "Sending SOS Alerts...", Toast.LENGTH_SHORT).show();

        FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(mAuth.getCurrentUser().getUid())
                .child("emergencyContacts")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.hasChildren()) {
                            sendSMSToSnapshot(snapshot, message);
                        } else {
                            sendSOSOffline(message); // Firebase empty → try cache
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Firebase SOS fetch failed: " + error.getMessage());
                        sendSOSOffline(message); // No internet → use cache
                    }
                });
    }

    private void sendSMSToSnapshot(DataSnapshot snapshot, String message) {
        int sentCount = 0;
        for (DataSnapshot contact : snapshot.getChildren()) {
            String phone = contact.child("number").getValue(String.class);
            if (sendSingleSMS(phone, message)) sentCount++;
        }
        showSOSResult(sentCount);
    }

    private void sendSOSOffline(String message) {
        Log.d(TAG, "Attempting offline SOS using cached contacts...");
        String cachedData = getSharedPreferences("FemoraContacts", MODE_PRIVATE)
                .getString("cached_contacts", "");

        if (!cachedData.isEmpty()) {
            int sentCount = 0;
            for (String contact : cachedData.split(";")) {
                if (contact.contains(":")) {
                    String phone = contact.split(":")[1].trim();
                    if (sendSingleSMS(phone, message)) sentCount++;
                }
            }
            showSOSResult(sentCount);
        } else {
            Toast.makeText(this,
                    "No trusted contacts found. Please add contacts while online.",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, EmergencyContactActivity.class));
        }
    }

    private boolean sendSingleSMS(String phone, String message) {
        if (phone == null || phone.trim().isEmpty()) return false;
        try {
            android.telephony.SmsManager smsManager =
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            ? getSystemService(android.telephony.SmsManager.class)
                            : android.telephony.SmsManager.getDefault();
            if (smsManager != null) {
                ArrayList<String> parts = smsManager.divideMessage(message);
                smsManager.sendMultipartTextMessage(phone.trim(), null, parts, null, null);
                Log.d(TAG, "SMS sent to: " + phone);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "SMS failed for " + phone + ": " + e.getMessage());
        }
        return false;
    }

    private void showSOSResult(int sentCount) {
        String msg = sentCount > 0
                ? "🚨 SOS Alerts sent to " + sentCount + " contacts!"
                : "Failed to send SMS. Check SIM or permissions.";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_SOS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startSosTimer();
            } else {
                Toast.makeText(this,
                        "Location & SMS permissions required for SOS.",
                        Toast.LENGTH_LONG).show();
            }

        } else if (requestCode == PERMISSION_REQUEST_VOICE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initVoiceSOS();
                startSosService();
            } else {
                Toast.makeText(this,
                        "Microphone permission denied. Voice SOS disabled.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // Refresh name and profile photo when returning to this screen
        setUserName();
        loadProfileImage();
        if (voiceHelper != null) voiceHelper.startListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceHelper     != null) voiceHelper.destroy();
        if (sosTimer        != null) sosTimer.cancel();
        // Unregister battery receiver safely (Version A)
        try { unregisterReceiver(batteryReceiver); } catch (Exception ignored) {}
    }
}
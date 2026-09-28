package com.example.femora;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    TextView tvWelcome, tvUserName;
    CardView profileButton, trustedContactsCard, nearbyHelpCard, liveLocationCard, emergencyReportCard, lowBatteryCard, offlineSmsCard;
    View btnSOSContainer;
    BottomNavigationView bottomNavigationView;
    FirebaseAuth mAuth;
    FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserName = findViewById(R.id.tvUserName);
        profileButton = findViewById(R.id.profile_button);
        trustedContactsCard = findViewById(R.id.trustedContactsCard);
        liveLocationCard = findViewById(R.id.liveLocationCard);
        emergencyReportCard = findViewById(R.id.emergencyReportCard);
        lowBatteryCard = findViewById(R.id.lowBatteryCard);
        offlineSmsCard = findViewById(R.id.offlineSmsCard);
        nearbyHelpCard = findViewById(R.id.nearbyHelpCard);
        btnSOSContainer = findViewById(R.id.btnSOSContainer);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Set dynamic greeting based on time
        setGreeting();

        // Set dynamic user name from SharedPreferences
        setUserName();

        // SOS Button click
        btnSOSContainer.setOnClickListener(v -> sendSOS());

        // Profile button click
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        // Trusted Contacts click
        trustedContactsCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, EmergencyContactActivity.class);
            startActivity(intent);
        });

        // Offline SMS click
        offlineSmsCard.setOnClickListener(v -> {
            sendSOS();
        });

        // Live Location click
        liveLocationCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LiveLocationActivity.class);
            startActivity(intent);
        });

        // Emergency Report click
        emergencyReportCard.setOnClickListener(v -> {
            // To be implemented
        });

        // Low Battery click
        lowBatteryCard.setOnClickListener(v -> {
            // This is handled automatically by BatteryReceiver, but we can show a status
            Toast.makeText(MainActivity.this,
                    "Automated alerts are active. They will trigger at 10% battery.",
                    Toast.LENGTH_LONG).show();
        });

        // Nearby Help click
        nearbyHelpCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NearbyHelpActivity.class);
            startActivity(intent);
        });

        // Bottom Navigation handling
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_tips) {
                startActivity(new Intent(MainActivity.this, SafetyTipsActivity.class));
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                return true;
            } else if (itemId == R.id.nav_map) {
                // Clicking the Map icon in bottom nav also opens Live Location
                startActivity(new Intent(MainActivity.this, LiveLocationActivity.class));
                return true;
            }
            return true;
        });

        // Load contacts into SharedPreferences for offline access
        cacheContactsLocally();
    }

    private void cacheContactsLocally() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();
        String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
        DatabaseReference contactsRef = FirebaseDatabase.getInstance(dbUrl)
                .getReference("users")
                .child(userId)
                .child("emergencyContacts");

        contactsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    StringBuilder contactsData = new StringBuilder();
                    for (DataSnapshot contact : snapshot.getChildren()) {
                        String name = contact.child("name").getValue(String.class);
                        String phone = contact.child("number").getValue(String.class);
                        if (phone != null) {
                            contactsData.append(name).append(":").append(phone).append(";");
                        }
                    }
                    SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
                    pref.edit().putString("cached_contacts", contactsData.toString()).apply();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendSOS() {
        if (mAuth.getCurrentUser() == null) return;

        // Check for both Location and SMS permissions
        boolean hasLocation = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasSms = ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;

        if (!hasLocation || !hasSms) {
            ArrayList<String> permissions = new ArrayList<>();
            if (!hasLocation) permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            if (!hasSms) permissions.add(Manifest.permission.SEND_SMS);

            ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), 1);
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    String message;
                    if (location != null) {
                        message = "I am in danger!!! Please help me. My live location: https://www.google.com/maps/search/?api=1&query="
                                + location.getLatitude() + "," + location.getLongitude()
                                + "\nCoordinates: " + location.getLatitude() + ", " + location.getLongitude()
                                + "\n- Sent via Femora";

                        // Cache last known location for emergency backup
                        SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
                        pref.edit().putString("last_lat", String.valueOf(location.getLatitude()))
                                .putString("last_lng", String.valueOf(location.getLongitude())).apply();
                    } else {
                        // Try to use cached location if GPS is currently failing (e.g. indoors)
                        SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
                        String lastLat = pref.getString("last_lat", null);
                        String lastLng = pref.getString("last_lng", null);

                        if (lastLat != null && lastLng != null) {
                            message = "I am in danger!!! Please help me. My last known location: https://www.google.com/maps/search/?api=1&query="
                                    + lastLat + "," + lastLng
                                    + "\n- Sent via Femora (Offline Location Cache)";
                        } else {
                            message = "I am in danger!!! Please help me. I need help immediately. (Location currently unavailable) - Sent via Femora";
                        }
                    }
                    fetchContactsAndSendSMS(message);
                });
    }

    private void fetchContactsAndSendSMS(String message) {
        if (mAuth.getCurrentUser() == null) return;

        Toast.makeText(this, "Sending SOS Alerts...", Toast.LENGTH_SHORT).show();

        // 1. Try fetching from Firebase (if online)
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
                    sendSMSToSnapshot(snapshot, message);
                } else {
                    // 2. If Firebase fails or is empty, try Offline Cache
                    sendSOSOffline(message);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // 3. On error (like no internet), try Offline Cache
                sendSOSOffline(message);
            }
        });
    }

    private void sendSMSToSnapshot(DataSnapshot snapshot, String message) {
        int sentCount = 0;
        for (DataSnapshot contact : snapshot.getChildren()) {
            String phone = contact.child("number").getValue(String.class);
            if (sendSingleSMS(phone, message)) {
                sentCount++;
            }
        }
        showSOSResult(sentCount);
    }

    private void sendSOSOffline(String message) {
        Log.d("SOS_DEBUG", "Attempting offline SOS sending...");
        SharedPreferences pref = getSharedPreferences("FemoraContacts", MODE_PRIVATE);
        String cachedData = pref.getString("cached_contacts", "");

        if (!cachedData.isEmpty()) {
            int sentCount = 0;
            String[] contacts = cachedData.split(";");
            for (String contact : contacts) {
                if (contact.contains(":")) {
                    String phone = contact.split(":")[1];
                    if (sendSingleSMS(phone, message)) {
                        sentCount++;
                    }
                }
            }
            showSOSResult(sentCount);
        } else {
            Toast.makeText(this, "No trusted contacts found. Please add them first while online.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, EmergencyContactActivity.class));
        }
    }

    private boolean sendSingleSMS(String phone, String message) {
        if (phone != null && !phone.trim().isEmpty()) {
            try {
                SmsManager smsManager;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    smsManager = getSystemService(SmsManager.class);
                } else {
                    smsManager = SmsManager.getDefault();
                }

                if (smsManager != null) {
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    smsManager.sendMultipartTextMessage(phone.trim(), null, parts, null, null);
                    Log.d("SOS_DEBUG", "SMS Sent to " + phone);
                    return true;
                }
            } catch (Exception e) {
                Log.e("SOS_DEBUG", "SMS failed for " + phone + ": " + e.getMessage());
            }
        }
        return false;
    }

    private void showSOSResult(int sentCount) {
        if (sentCount > 0) {
            Toast.makeText(this, "SOS Alerts sent to " + sentCount + " contacts!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Failed to send SMS. Check your SIM or permissions.", Toast.LENGTH_LONG).show();
        }
    }

    private void openMap(String query) {
        android.net.Uri gmmIntentUri = android.net.Uri.parse("geo:0,0?q=" + query);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Fallback for if Google Maps is not installed
            startActivity(new Intent(Intent.ACTION_VIEW, gmmIntentUri));
        }
    }

    private void setGreeting() {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        String greeting;
        if (hour < 12) {
            greeting = "Good Morning,";
        } else if (hour < 16) {
            greeting = "Good Afternoon,";
        } else if (hour < 21) {
            greeting = "Good Evening,";
        } else {
            greeting = "Good Night,";
        }

        tvWelcome.setText(greeting);
    }

    private void setUserName() {
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
        String name = pref.getString("name", "User");
        tvUserName.setText(name);

        // Also fetch from Firebase to ensure it's up to date or if Prefs are empty
        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
            DatabaseReference db = FirebaseDatabase.getInstance(dbUrl).getReference("users").child(userId);

            db.child("name").addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String firebaseName = snapshot.getValue(String.class);
                        tvUserName.setText(firebaseName);

                        // Update SharedPreferences
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("name", firebaseName);
                        editor.apply();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {}
            });
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        setUserName();
    }
}

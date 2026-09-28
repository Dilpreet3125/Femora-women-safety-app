package com.example.femora;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class PersonalInfoActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText infoName, infoEmail, infoPhone, infoAge, infoAddress;
    private ImageButton btnBack, btnEditSave;
    private CardView btnSaveChanges;
    private TextView tvEditModeStatus;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean isEditMode = false;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    private static final String DB_URL =
            "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_info);

        initViews();
        setupFirebase();
        loadUserData();
        setupClickListeners();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INIT VIEWS
    // ─────────────────────────────────────────────────────────────────────────
    private void initViews() {
        infoName         = findViewById(R.id.infoName);
        infoEmail        = findViewById(R.id.infoEmail);
        infoPhone        = findViewById(R.id.infoPhone);
        infoAge          = findViewById(R.id.infoAge);
        infoAddress      = findViewById(R.id.infoAddress);
        btnBack          = findViewById(R.id.btnBack);
        btnEditSave      = findViewById(R.id.btnEditSave);
        btnSaveChanges   = findViewById(R.id.btnSaveChanges);
        tvEditModeStatus = findViewById(R.id.tvEditModeStatus);

        // Email is always locked (linked to Firebase Auth)
        infoEmail.setEnabled(false);
        infoEmail.setAlpha(0.6f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SETUP FIREBASE
    // ─────────────────────────────────────────────────────────────────────────
    private void setupFirebase() {
        mAuth = FirebaseAuth.getInstance();
        String userId = mAuth.getCurrentUser() != null
                ? mAuth.getCurrentUser().getUid() : null;

        if (userId != null) {
            dbRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("users")
                    .child(userId);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD USER DATA
    // ─────────────────────────────────────────────────────────────────────────
    private void loadUserData() {
        if (dbRef != null) {
            // Load from Firebase (live)
            dbRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String name    = snapshot.child("name").getValue(String.class);
                        String email   = snapshot.child("email").getValue(String.class);
                        String phone   = snapshot.child("phone").getValue(String.class);
                        String age     = snapshot.child("age").getValue(String.class);
                        String address = snapshot.child("address").getValue(String.class);

                        infoName.setText(name    != null ? name    : "");
                        infoEmail.setText(email  != null ? email   : "");
                        infoPhone.setText(phone  != null ? phone   : "");
                        infoAge.setText(age      != null ? age     : "");
                        infoAddress.setText(address != null ? address : "");
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(PersonalInfoActivity.this,
                            "Failed to load data.", Toast.LENGTH_SHORT).show();
                    loadFromSharedPreferences(); // Fallback
                }
            });
        } else {
            // No logged-in user → use SharedPreferences cache
            loadFromSharedPreferences();
        }
    }

    private void loadFromSharedPreferences() {
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
        infoName.setText(pref.getString("name", ""));
        infoEmail.setText(pref.getString("email", ""));
        infoPhone.setText(pref.getString("phone", ""));
        infoAge.setText(pref.getString("age", ""));
        infoAddress.setText(pref.getString("address", ""));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLICK LISTENERS
    // ─────────────────────────────────────────────────────────────────────────
    private void setupClickListeners() {

        btnBack.setOnClickListener(v -> finish());

        // Edit / Cancel toggle
        btnEditSave.setOnClickListener(v -> {
            if (isEditMode) {
                // Cancel editing — revert to view mode
                exitEditMode();
                loadUserData(); // Reload original data
            } else {
                enterEditMode();
            }
        });

        // Save button (shown only in edit mode)
        btnSaveChanges.setOnClickListener(v -> saveUserData());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENTER EDIT MODE
    // ─────────────────────────────────────────────────────────────────────────
    private void enterEditMode() {
        isEditMode = true;

        // Enable editable fields (email stays locked)
        infoName.setEnabled(true);
        infoPhone.setEnabled(true);
        infoAge.setEnabled(true);
        infoAddress.setEnabled(true);

        // Focus on name
        infoName.requestFocus();
        infoName.setSelection(infoName.getText().length());

        // Update UI state
        btnEditSave.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnSaveChanges.setVisibility(View.VISIBLE);
        tvEditModeStatus.setText("● Edit Mode");

        Toast.makeText(this, "You can now edit your information.", Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXIT EDIT MODE
    // ─────────────────────────────────────────────────────────────────────────
    private void exitEditMode() {
        isEditMode = false;

        // Disable all fields
        infoName.setEnabled(false);
        infoPhone.setEnabled(false);
        infoAge.setEnabled(false);
        infoAddress.setEnabled(false);

        // Update UI state
        btnEditSave.setImageResource(android.R.drawable.ic_menu_edit);
        btnSaveChanges.setVisibility(View.GONE);
        tvEditModeStatus.setText("● View Mode");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE USER DATA
    // ─────────────────────────────────────────────────────────────────────────
    private void saveUserData() {
        String name    = infoName.getText().toString().trim();
        String phone   = infoPhone.getText().toString().trim();
        String age     = infoAge.getText().toString().trim();
        String address = infoAddress.getText().toString().trim();

        // Validation
        if (name.isEmpty()) {
            infoName.setError("Name cannot be empty.");
            infoName.requestFocus();
            return;
        }
        if (!phone.isEmpty() && phone.length() < 10) {
            infoPhone.setError("Enter a valid phone number.");
            infoPhone.requestFocus();
            return;
        }
        if (!age.isEmpty()) {
            int ageVal = Integer.parseInt(age);
            if (ageVal < 1 || ageVal > 120) {
                infoAge.setError("Enter a valid age.");
                infoAge.requestFocus();
                return;
            }
        }

        // Save to Firebase
        if (dbRef != null) {
            dbRef.child("name").setValue(name);
            dbRef.child("phone").setValue(phone);
            dbRef.child("age").setValue(age);
            dbRef.child("address").setValue(address)
                    .addOnSuccessListener(unused -> {
                        // Also cache locally
                        getSharedPreferences("UserData", MODE_PRIVATE).edit()
                                .putString("name", name)
                                .putString("phone", phone)
                                .putString("age", age)
                                .putString("address", address)
                                .apply();

                        Toast.makeText(this,
                                "✅ Profile updated successfully!", Toast.LENGTH_SHORT).show();
                        exitEditMode();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed to save. Check your connection.", Toast.LENGTH_SHORT).show());
        } else {
            // Offline: save locally only
            getSharedPreferences("UserData", MODE_PRIVATE).edit()
                    .putString("name", name)
                    .putString("phone", phone)
                    .putString("age", age)
                    .putString("address", address)
                    .apply();
            Toast.makeText(this, "Saved locally (offline).", Toast.LENGTH_SHORT).show();
            exitEditMode();
        }
    }
}
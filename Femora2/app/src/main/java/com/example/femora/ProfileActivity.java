package com.example.femora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ProfileActivity extends AppCompatActivity {

    TextView tvName, tvEmail;
    ImageView btnBack;
    LinearLayout btnLogout, optionPersonalInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName = findViewById(R.id.tvProfileName);
        tvEmail = findViewById(R.id.tvProfileEmail);
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout);
        optionPersonalInfo = findViewById(R.id.optionPersonalInfo);

        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);

        // LOAD DATA
        String name = pref.getString("name", "Samridhi");
        String email = pref.getString("email", "samridhi@gmail.com");

        tvName.setText(name);
        tvEmail.setText(email);

        // BACK BUTTON
        btnBack.setOnClickListener(v -> finish());

        // LOGOUT BUTTON
        btnLogout.setOnClickListener(v -> {
            // 1. Sign out from Firebase
            FirebaseAuth.getInstance().signOut();

            // 2. Clear SharedPreferences
            SharedPreferences logoutPref = getSharedPreferences("UserData", MODE_PRIVATE);
            SharedPreferences.Editor editor = logoutPref.edit();
            editor.clear();
            editor.apply();

            // 3. Redirect to Login Activity
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // PERSONAL INFO CLICK
        optionPersonalInfo.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, PersonalInfoActivity.class);
            startActivity(intent);
        });
    }
}
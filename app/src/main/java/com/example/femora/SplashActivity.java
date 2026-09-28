package com.example.femora;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import com.google.firebase.auth.FirebaseAuth;

import androidx.appcompat.app.AppCompatActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(getMainLooper()).postDelayed(() -> {
            FirebaseAuth mAuth = FirebaseAuth.getInstance();

            if (mAuth.getCurrentUser() != null) {
                // User is signed in via Firebase
                startActivity(new Intent(SplashActivity.this, MainActivity.class));
            } else {
                // No user signed in
                SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
                boolean isFirstTime = pref.getBoolean("isFirstTime", true);

                if (isFirstTime) {
                    startActivity(new Intent(SplashActivity.this, OnboardingActivity1.class));
                } else {
                    startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                }
            }
            finish();
        }, 2500);
    }
}
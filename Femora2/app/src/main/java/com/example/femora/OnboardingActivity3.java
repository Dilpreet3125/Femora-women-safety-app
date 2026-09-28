package com.example.femora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity3 extends AppCompatActivity {

    Button btnStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding3);

        btnStart = findViewById(R.id.btnStart);

        btnStart.setOnClickListener(v -> {
            // Mark onboarding as completed
            SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
            pref.edit().putBoolean("isFirstTime", false).apply();

            // Change destination from MainActivity to LoginActivity
            startActivity(new Intent(OnboardingActivity3.this, LoginActivity.class));
            finish();
        });
    }
}
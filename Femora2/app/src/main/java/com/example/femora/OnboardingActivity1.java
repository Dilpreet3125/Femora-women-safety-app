package com.example.femora;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity1 extends AppCompatActivity {

    Button btnNext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding1); // your layout name

        btnNext = findViewById(R.id.btnNext);

        btnNext.setOnClickListener(v -> {
            startActivity(new Intent(OnboardingActivity1.this, OnboardingActivity2.class));
        });
    }
}
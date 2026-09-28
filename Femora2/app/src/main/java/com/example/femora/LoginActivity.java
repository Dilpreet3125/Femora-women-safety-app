package com.example.femora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    EditText email, password;
    Button btnLogin;
    TextView txtSignup;
    ImageView eyeIcon;

    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Check if user is already logged in via Firebase
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        // Initialize views
        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        btnLogin = findViewById(R.id.btnLogin);
        txtSignup = findViewById(R.id.txtLogin); // This is txtLogin in layout but used for "Sign Up" text
        eyeIcon = findViewById(R.id.eyeIcon);

        // 👁️ Password show/hide
        eyeIcon.setOnClickListener(v -> {
            boolean isHidden = password.getInputType() ==
                    (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            password.setInputType(
                    isHidden
                            ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );

            password.setSelection(password.length());
        });

        // 🔥 LOGIN BUTTON (using Firebase Auth)
        btnLogin.setOnClickListener(v -> {
            String inputEmail = email.getText().toString().trim();
            String inputPassword = password.getText().toString().trim();

            if (inputEmail.isEmpty() || inputPassword.isEmpty()) {
                Toast.makeText(this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Firebase Login logic
            mAuth.signInWithEmailAndPassword(inputEmail, inputPassword)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Check if contacts are already added
                            checkContactsAndNavigate();
                        } else {
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : "Login Failed";
                            Toast.makeText(this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // SIGN UP CLICK - Go to Register
        txtSignup.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void checkContactsAndNavigate() {
        String userId = mAuth.getCurrentUser().getUid();
        String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
        DatabaseReference db = FirebaseDatabase.getInstance(dbUrl).getReference("users").child(userId);

            db.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        // Save user basic info to SharedPreferences
                        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
                        SharedPreferences.Editor editor = pref.edit();
                        editor.putString("name", snapshot.child("name").getValue(String.class));
                        editor.putString("email", snapshot.child("email").getValue(String.class));
                        editor.putBoolean("isLoggedIn", true);
                        editor.apply();

                        // Check if emergencyContacts exists
                        if (snapshot.hasChild("emergencyContacts")) {
                            // Contacts already exist, go straight to Dashboard
                            Toast.makeText(LoginActivity.this, "Welcome Back! 👋", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        } else {
                            // First time/No contacts, go to Emergency Contact setup
                            Toast.makeText(LoginActivity.this, "Complete your safety setup", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(LoginActivity.this, EmergencyContactActivity.class));
                        }
                        finish();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                }
            });
    }
}
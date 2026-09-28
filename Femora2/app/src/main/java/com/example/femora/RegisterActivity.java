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

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RegisterActivity extends AppCompatActivity {

    EditText etName, etPhone, etEmail, etPassword, etConfirmPassword, etAge;
    Button btnRegister;
    TextView txtLogin;
    ImageView eyeIcon;

    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 🔥 Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // 🔥 Firebase connection test
        // Explicitly passing the Database URL because it's in the Asia-Southeast1 region
        String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
        DatabaseReference testDB = FirebaseDatabase.getInstance(dbUrl).getReference("test");

        testDB.setValue("Hello Firebase")
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(this, "Firebase Connected ✅", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed ❌: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );

        // UI Binding
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etAge = findViewById(R.id.etAge);
        btnRegister = findViewById(R.id.btnRegister);
        txtLogin = findViewById(R.id.txtLogin);
        eyeIcon = findViewById(R.id.eyeIcon);

        // 👁️ Password show/hide
        eyeIcon.setOnClickListener(v -> {
            boolean isHidden = etPassword.getInputType() ==
                    (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            etPassword.setInputType(
                    isHidden
                            ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            );

            etPassword.setSelection(etPassword.length());
        });

        // 🔥 REGISTER BUTTON
        btnRegister.setOnClickListener(v -> {

            String name = etName.getText().toString();
            String phone = etPhone.getText().toString();
            String email = etEmail.getText().toString();
            String password = etPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();
            String age = etAge.getText().toString();

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty() || age.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();

            } else if (phone.length() != 10) {
                Toast.makeText(this, "Phone number must be 10 digits", Toast.LENGTH_SHORT).show();

            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();

            } else if (!password.matches("^(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$")) {
                Toast.makeText(this, "Password must be at least 8 characters, include one uppercase letter and one symbol", Toast.LENGTH_LONG).show();

            } else if (!password.equals(confirmPassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();

            } else {

                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {

                            if (task.isSuccessful()) {

                                if (mAuth.getCurrentUser() != null) {

                                    String userId = mAuth.getCurrentUser().getUid();

                                    // Using the same explicit URL for saving user data
                                    DatabaseReference db = FirebaseDatabase.getInstance(dbUrl).getReference("users");

                                    User user = new User(name, phone, email, age);

                                    db.child(userId).setValue(user);

                                    // Save to SharedPreferences for immediate use in MainActivity
                                    SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = pref.edit();
                                    editor.putString("name", name);
                                    editor.putString("email", email);
                                    editor.apply();
                                }

                                Toast.makeText(this, "Registered Successfully", Toast.LENGTH_SHORT).show();

                                // Go to next screen
                                startActivity(new Intent(RegisterActivity.this, EmergencyContactActivity.class));
                                finish();

                            } else {
                                String errorMsg = task.getException() != null
                                        ? task.getException().getMessage()
                                        : "Unknown error";

                                Toast.makeText(this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
            }
        });

        // 🔁 Go to Login
        txtLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }
}
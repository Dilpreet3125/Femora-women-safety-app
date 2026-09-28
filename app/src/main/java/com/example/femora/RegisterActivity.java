package com.example.femora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/*
 * ════════════════════════════════════════════════════════════════════════
 *  WHY "Your request to verify your email has expired or the link has
 *  already been used" APPEARS — AND HOW WE FIX IT:
 *
 *  Root cause 1 — Multiple emails sent for same account
 *    The old code could trigger sendEmailVerification() more than once
 *    (on DB failure retry, on addOnSuccessListener missing, etc.).
 *    Firebase verification links are ONE-TIME USE. When a new email is
 *    sent, ALL previous links for that account are instantly invalidated.
 *    Clicking an old link → "expired or already used".
 *    FIX: sendEmailVerification() is called EXACTLY ONCE, only after
 *         reload() confirms session is live, guarded by a boolean flag.
 *
 *  Root cause 2 — Link genuinely expired (>24 hours)
 *    Firebase email verification links expire after 24 hours by default.
 *    FIX: LoginActivity detects isEmailVerified()==false at login time,
 *         automatically resends a FRESH link, and shows a clear message.
 *
 *  Root cause 3 — Old link clicked after new registration attempt
 *    User registered, got a link, didn't click it, registered again
 *    with the same email → new link sent → old link now invalid.
 *    FIX: At registration, check if this email already has an account
 *         with an unverified state and resend instead of re-creating.
 * ════════════════════════════════════════════════════════════════════════
 */
public class RegisterActivity extends AppCompatActivity {

    private static final String TAG    = "RegisterActivity";
    private static final String DB_URL =
            "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";

    private EditText    etName, etPhone, etEmail, etPassword, etAge;
    private Button      btnRegister;
    private ImageView   eyeIcon;
    private ProgressBar progressBar;

    private FirebaseAuth mAuth;
    private boolean      isGoogleSetup        = false;
    // Guard flag — ensures sendEmailVerification is called EXACTLY once
    private boolean      verificationSent     = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth         = FirebaseAuth.getInstance();
        isGoogleSetup = getIntent().getBooleanExtra("isGoogleNew", false);

        initViews();

        if (isGoogleSetup) {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                etEmail.setText(user.getEmail());
                etEmail.setEnabled(false);
                if (user.getDisplayName() != null && !user.getDisplayName().isEmpty())
                    etName.setText(user.getDisplayName());
            }
        }

        btnRegister.setOnClickListener(v -> handleRegistration());

        findViewById(R.id.txtLogin).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        eyeIcon.setOnClickListener(v -> togglePassword());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HANDLE REGISTRATION
    // ─────────────────────────────────────────────────────────────────────────
    private void handleRegistration() {
        String name     = etName.getText().toString().trim();
        String phone    = etPhone.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String age      = etAge.getText().toString().trim();

        if (name.isEmpty()) { etName.setError("Full name is required"); etName.requestFocus(); return; }
        if (phone.isEmpty()) { etPhone.setError("Phone number is required"); etPhone.requestFocus(); return; }
        if (!isGoogleSetup) {
            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Enter a valid email address"); etEmail.requestFocus(); return;
            }
            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters"); etPassword.requestFocus(); return;
            }
        }
        if (age.isEmpty()) { etAge.setError("Age is required"); etAge.requestFocus(); return; }

        showLoading(true);

        if (isGoogleSetup) {
            handleGoogleUserSetup(name, phone, email, age);
        } else {
            handleManualRegistration(name, phone, email, password, age);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MANUAL REGISTRATION
    //  Correct order:
    //    1. createUserWithEmailAndPassword
    //    2. updateProfile (display name)
    //    3. Write to Realtime DB
    //    4. reload() — commits session so email send has a valid token
    //    5. sendEmailVerification() — called ONCE, guarded by verificationSent
    //    6. signOut() — ONLY after email task completes
    //    7. Go to LoginActivity
    // ─────────────────────────────────────────────────────────────────────────
    private void handleManualRegistration(String name, String phone,
                                          String email, String password, String age) {
        verificationSent = false; // reset guard for this registration attempt

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, createTask -> {
                    if (!createTask.isSuccessful()) {
                        showLoading(false);
                        String err = createTask.getException() != null
                                ? createTask.getException().getMessage() : "Registration failed";
                        Log.e(TAG, "createUser: " + err);

                        // FIX: If the email already exists but is unverified,
                        //      sign in and resend the verification link instead
                        //      of showing a confusing error.
                        if (err != null && err.contains("email address is already in use")) {
                            handleExistingUnverifiedAccount(email, password, name, phone, age);
                        } else {
                            Toast.makeText(this, "Registration failed: " + err,
                                    Toast.LENGTH_LONG).show();
                        }
                        return;
                    }

                    FirebaseUser newUser = mAuth.getCurrentUser();
                    if (newUser == null) {
                        showLoading(false);
                        Toast.makeText(this, "Unexpected error. Try again.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Step 2: Set display name
                    new UserProfileChangeRequest.Builder()
                            .setDisplayName(name).build();
                    newUser.updateProfile(
                            new UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    ).addOnCompleteListener(profileTask -> {

                        // Step 3: Write to DB
                        FirebaseDatabase.getInstance(DB_URL)
                                .getReference("users")
                                .child(newUser.getUid())
                                .setValue(new User(name, phone, email, age))
                                .addOnCompleteListener(dbTask -> {

                                    if (!dbTask.isSuccessful())
                                        Log.e(TAG, "DB write failed: " + dbTask.getException());

                                    // Save locally
                                    saveLocally(name, phone, email, age, false);

                                    // Step 4: reload() → Step 5: send email
                                    newUser.reload().addOnCompleteListener(
                                            reloadTask -> sendVerificationEmailOnce(newUser, email));
                                });
                    });
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FIX FOR "EMAIL ALREADY IN USE" — handle existing unverified accounts
    //
    //  Scenario: User registered before, got a link, never clicked it, now
    //  tries to register again with the same email → Firebase says "already
    //  in use". The fix: sign in to that account, check if it's unverified,
    //  and resend a FRESH link (which invalidates the old one cleanly).
    //
    //  This prevents the user from ending up with a stale old link AND a
    //  new link — which is exactly what causes the "expired" error.
    // ─────────────────────────────────────────────────────────────────────────
    private void handleExistingUnverifiedAccount(String email, String password,
                                                 String name, String phone, String age) {
        showLoading(true);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, signInTask -> {
                    if (!signInTask.isSuccessful()) {
                        showLoading(false);
                        Toast.makeText(this,
                                "This email is already registered. "
                                        + "Please log in or use 'Forgot Password'.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    FirebaseUser existing = mAuth.getCurrentUser();
                    if (existing == null) { showLoading(false); return; }

                    existing.reload().addOnCompleteListener(reloadTask -> {
                        if (existing.isEmailVerified()) {
                            // Already verified — tell them to just log in
                            showLoading(false);
                            mAuth.signOut();
                            Toast.makeText(this,
                                    "This email is already registered and verified. "
                                            + "Please log in.",
                                    Toast.LENGTH_LONG).show();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        } else {
                            // Unverified — update DB data and resend a fresh link
                            FirebaseDatabase.getInstance(DB_URL)
                                    .getReference("users")
                                    .child(existing.getUid())
                                    .setValue(new User(name, phone, email, age))
                                    .addOnCompleteListener(dbTask -> {
                                        saveLocally(name, phone, email, age, false);
                                        sendVerificationEmailOnce(existing, email);
                                    });
                        }
                    });
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SEND VERIFICATION EMAIL — called EXACTLY ONCE per registration attempt.
    //
    //  The boolean guard `verificationSent` prevents any code path from
    //  accidentally calling this twice, which would immediately invalidate
    //  the first link and cause the "expired" error on the user's device.
    //
    //  signOut() is called INSIDE this callback — AFTER the email is sent —
    //  not before. Signing out before this call destroys the session token
    //  that Firebase needs to send the email.
    // ─────────────────────────────────────────────────────────────────────────
    private void sendVerificationEmailOnce(FirebaseUser user, String email) {
        if (verificationSent) {
            // Guard: already sent — do not send again
            Log.w(TAG, "sendVerificationEmailOnce: already sent, skipping duplicate call");
            navigateToLogin(email);
            return;
        }
        verificationSent = true;

        user.sendEmailVerification().addOnCompleteListener(verifyTask -> {
            // Sign out AFTER the email task completes — never before
            mAuth.signOut();
            showLoading(false);

            if (verifyTask.isSuccessful()) {
                Log.d(TAG, "Verification email sent to: " + email);
                Toast.makeText(this,
                        "✅ Account created!\n\n"
                                + "A fresh verification link has been sent to:\n"
                                + email + "\n\n"
                                + "• Check your inbox and spam folder\n"
                                + "• The link expires in 24 hours\n"
                                + "• Do not click any old links — only use the latest email",
                        Toast.LENGTH_LONG).show();
            } else {
                String err = verifyTask.getException() != null
                        ? verifyTask.getException().getMessage() : "";
                Log.e(TAG, "sendEmailVerification failed: " + err);
                Toast.makeText(this,
                        "Account created, but the verification email failed.\n\n"
                                + "When you log in, we'll automatically resend it.",
                        Toast.LENGTH_LONG).show();
            }

            navigateToLogin(email);
        });
    }

    private void navigateToLogin(String email) {
        Intent intent = new Intent(this, LoginActivity.class);
        // Pass the email so LoginActivity can pre-fill the email field
        intent.putExtra("prefill_email", email);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GOOGLE USER SETUP — no email verification needed
    // ─────────────────────────────────────────────────────────────────────────
    private void handleGoogleUserSetup(String name, String phone, String email, String age) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            showLoading(false);
            Toast.makeText(this, "Session expired. Sign in with Google again.",
                    Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        user.updateProfile(new UserProfileChangeRequest.Builder()
                .setDisplayName(name).build()
        ).addOnCompleteListener(pt ->
                FirebaseDatabase.getInstance(DB_URL)
                        .getReference("users")
                        .child(user.getUid())
                        .setValue(new User(name, phone, email, age))
                        .addOnCompleteListener(task -> {
                            showLoading(false);
                            if (task.isSuccessful()) {
                                saveLocally(name, phone, email, age, true);
                                Toast.makeText(this,
                                        "Profile saved! Now set up your emergency contacts.",
                                        Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(this, EmergencyContactActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            } else {
                                String err = task.getException() != null
                                        ? task.getException().getMessage() : "Failed";
                                Toast.makeText(this, "Failed to save data: " + err,
                                        Toast.LENGTH_SHORT).show();
                            }
                            finish();
                        })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private void saveLocally(String name, String phone, String email,
                             String age, boolean isLoggedIn) {
        SharedPreferences.Editor ed = getSharedPreferences("UserData", MODE_PRIVATE).edit();
        ed.putString("name",  name)
                .putString("phone", phone)
                .putString("email", email)
                .putString("age",   age);
        if (isLoggedIn) ed.putBoolean("isLoggedIn", true);
        ed.apply();
    }

    private void initViews() {
        etName      = findViewById(R.id.etName);
        etPhone     = findViewById(R.id.etPhone);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        etAge       = findViewById(R.id.etAge);
        btnRegister = findViewById(R.id.btnRegister);
        eyeIcon     = findViewById(R.id.eyeIcon);
        progressBar = findViewById(R.id.registerProgressBar);
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null)
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (btnRegister != null)
            btnRegister.setEnabled(!isLoading);
    }

    private void togglePassword() {
        boolean isHidden = etPassword.getInputType() ==
                (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setInputType(isHidden
                ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPassword.setSelection(etPassword.length());
    }
}
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class LoginActivity extends AppCompatActivity {

    private EditText   email, password;
    private Button     btnLogin;

    private View btnGoogleLogin;
    private TextView   txtSignup, txtForgotPassword;
    private ImageView  eyeIcon;
    private ProgressBar progressBar;

    private FirebaseAuth       mAuth;
    private GoogleSignInClient googleSignInClient;

    private static final String DB_URL        = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String WEB_CLIENT_ID = "704911599115-c2pg5oot9n3e7l6qob4p825lb4tftt6b.apps.googleusercontent.com";

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                showLoading(false);
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task =
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null) {
                            firebaseAuthWithGoogle(account.getIdToken());
                        }
                    } catch (ApiException e) {
                        Toast.makeText(this,
                                "Sign-in Failed: " + e.getStatusCode(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        initViews();
        setupGoogleSignIn();

        btnLogin.setOnClickListener(v -> handleManualLogin());

        btnGoogleLogin.setOnClickListener(v -> {
            showLoading(true);
            // Sign out stale session before launching picker
            googleSignInClient.signOut().addOnCompleteListener(this, t -> {
                googleSignInLauncher.launch(googleSignInClient.getSignInIntent());
            });
        });

        // FIX 3: Do NOT call finish() here — keeps LoginActivity in the back stack
        // so the user can press Back from RegisterActivity to return here.
        txtSignup.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        eyeIcon.setOnClickListener(v -> togglePassword());

        txtForgotPassword.setOnClickListener(v -> handleForgotPassword());
    }


    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }


    private void firebaseAuthWithGoogle(String idToken) {
        showLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    user.reload().addOnCompleteListener(
                            reloadTask -> checkDatabaseAndNavigate(user));
                }
            } else {
                showLoading(false);
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void checkDatabaseAndNavigate(FirebaseUser user) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(user.getUid());

        dbRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    if (user.isEmailVerified()) {
                        // Profile exists + email verified → save prefs and navigate
                        completeLogin(snapshot);
                    } else {
                        // Profile exists but not verified
                        requestVerification(user);
                    }
                } else {
                    // No profile in DB yet (new Google user)
                    // FIX 2: Google new user → go to RegisterActivity for profile setup
                    showLoading(false);
                    Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                    intent.putExtra("isGoogleNew", true);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
        });
    }


    private void requestVerification(FirebaseUser user) {
        user.sendEmailVerification().addOnCompleteListener(task -> {
            showLoading(false);
            Toast.makeText(this,
                    "Verify email: " + user.getEmail(),
                    Toast.LENGTH_LONG).show();
            mAuth.signOut();
            googleSignInClient.signOut();
        });
    }


    private void completeLogin(DataSnapshot snapshot) {
        showLoading(false);

        // Save all user data to SharedPreferences
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString("name",    snapshot.child("name").getValue(String.class));
        editor.putString("email",   snapshot.child("email").getValue(String.class));
        editor.putString("phone",   snapshot.child("phone").getValue(String.class));
        editor.putString("age",     snapshot.child("age").getValue(String.class));
        editor.putBoolean("isLoggedIn", true);

        String profileImg = snapshot.child("profileImage").getValue(String.class);
        if (profileImg != null) editor.putString("profile_image", profileImg);

        editor.apply();

        // FIX 4: Show "Welcome Back" for every returning user
        Toast.makeText(this, "Welcome Back! 👋", Toast.LENGTH_SHORT).show();

        Intent intent;
        if (snapshot.hasChild("emergencyContacts")) {
            // Contacts already set → go to main dashboard
            intent = new Intent(LoginActivity.this, MainActivity.class);
        } else {
            // Profile exists but no contacts yet → complete safety setup
            intent = new Intent(LoginActivity.this, EmergencyContactActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Manual Email + Password Login
    private void handleManualLogin() {
        String em = email.getText().toString().trim();
        String pw = password.getText().toString().trim();

        if (!validateInputs(em, pw)) return;

        showLoading(true);
        mAuth.signInWithEmailAndPassword(em, pw).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    user.reload().addOnCompleteListener(reloadTask -> {
                        if (user.isEmailVerified()) {
                            checkDatabaseAndNavigate(user);
                        } else {
                            showLoading(false);
                            user.sendEmailVerification();
                            Toast.makeText(this,
                                    "Verify email first.",
                                    Toast.LENGTH_SHORT).show();
                            mAuth.signOut();
                        }
                    });
                }
            } else {
                showLoading(false);
                String errorMsg = task.getException() != null
                        ? task.getException().getMessage()
                        : "Login Failed";
                Toast.makeText(this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Forgot Password

    private void handleForgotPassword() {
        String em = email.getText().toString().trim();
        if (em.isEmpty()) {
            Toast.makeText(this,
                    "Please enter your email to reset password",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mAuth.sendPasswordResetEmail(em)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Reset link sent to your email!",
                                Toast.LENGTH_LONG).show();
                    } else {
                        String err = task.getException() != null
                                ? task.getException().getMessage()
                                : "Failed";
                        Toast.makeText(this,
                                "Failed to send reset email: " + err,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }


    private void initViews() {
        email             = findViewById(R.id.email);
        password          = findViewById(R.id.password);
        btnLogin          = findViewById(R.id.btnLogin);
        btnGoogleLogin    = findViewById(R.id.btnGoogleLogin);
        txtSignup         = findViewById(R.id.txtLogin);
        txtForgotPassword = findViewById(R.id.txtForgotPassword);
        eyeIcon           = findViewById(R.id.eyeIcon);
        progressBar       = findViewById(R.id.loginProgressBar);
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null)
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void togglePassword() {
        boolean isHidden = password.getInputType() ==
                (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setInputType(
                isHidden
                        ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        password.setSelection(password.length());
    }

    private boolean validateInputs(String e, String p) {
        if (e.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            email.setError("Invalid Email");
            return false;
        }
        if (p.isEmpty()) {
            password.setError("Password required");
            return false;
        }
        return true;
    }
}
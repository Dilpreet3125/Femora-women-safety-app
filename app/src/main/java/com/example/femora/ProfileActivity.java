package com.example.femora;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

public class ProfileActivity extends AppCompatActivity {

    // ── Header Views ──────────────────────────────────────────────────────────
    private TextView tvName, tvEmail;
    private ImageView ivProfileImage;
    private CardView btnAddImage, btnDeleteImage;
    private ImageView btnBack;

    // ── Section: My Account ───────────────────────────────────────────────────
    private LinearLayout rowPersonalInfo, rowChangePassword, rowTrustedContacts;

    // ── Section: Safety Tools ─────────────────────────────────────────────────
    private MaterialSwitch switchVoiceActivation;
    private LinearLayout rowQuickMessage;

    // ── Section: Help & Information ───────────────────────────────────────────
    private LinearLayout rowAppGuide;

    // ── Section: Privacy & Security ───────────────────────────────────────────
    private LinearLayout rowPrivacySecurity, rowDeleteAccount;

    // ── Logout ────────────────────────────────────────────────────────────────
    private CardView btnLogout;

    // ── Bottom Nav ────────────────────────────────────────────────────────────
    private BottomNavigationView bottomNavigationView;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private DatabaseReference userRef;
    private GoogleSignInClient mGoogleSignInClient;

    private static final String DB_URL =
            "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
    private static final String WEB_CLIENT_ID =
            "704911599115-c2pg5oot9n3e7l6qob4p825lb4tftt6b.apps.googleusercontent.com";

    // ── Photo Picker ──────────────────────────────────────────────────────────
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                        updateProfileImageView(bitmap);
                        saveProfileImageLocally(bitmap);
                        uploadImageToFirebase(bitmap);
                    } catch (IOException e) {
                        Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        if (mAuth.getCurrentUser() != null) {
            userRef = FirebaseDatabase.getInstance(DB_URL)
                    .getReference("users")
                    .child(mAuth.getCurrentUser().getUid());
        }

        initViews();
        loadUserData();
        loadProfileImage();
        loadSafetyToolsState();
        setupClickListeners();
        setupBottomNav();
    }

    private void initViews() {
        tvName              = findViewById(R.id.tvProfileName);
        tvEmail             = findViewById(R.id.tvProfileEmail);
        ivProfileImage      = findViewById(R.id.ivProfileImage);
        btnAddImage         = findViewById(R.id.btnAddImage);
        btnDeleteImage      = findViewById(R.id.btnDeleteImage); // Added this
        btnBack             = findViewById(R.id.btnBack);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        rowPersonalInfo    = findViewById(R.id.rowPersonalInfo);
        rowChangePassword  = findViewById(R.id.rowChangePassword);
        rowTrustedContacts = findViewById(R.id.rowTrustedContacts);

        switchVoiceActivation = findViewById(R.id.switchVoiceActivation);
        rowQuickMessage       = findViewById(R.id.rowQuickMessage);

        if (switchVoiceActivation != null) {
            switchVoiceActivation.setShowText(false);
            switchVoiceActivation.setTextOn("");
            switchVoiceActivation.setTextOff("");
            switchVoiceActivation.setText("");
        }

        rowAppGuide = findViewById(R.id.rowAppGuide);
        rowPrivacySecurity = findViewById(R.id.rowPrivacySecurity);
        rowDeleteAccount   = findViewById(R.id.rowDeleteAccount);
        btnLogout = findViewById(R.id.btnLogout);
    }

    private void loadUserData() {
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
        tvName.setText(pref.getString("name", "User"));
        tvEmail.setText(pref.getString("email", "email@example.com"));

        if (userRef != null) {
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String name  = snapshot.child("name").getValue(String.class);
                        String email = snapshot.child("email").getValue(String.class);
                        if (name  != null) { tvName.setText(name);  pref.edit().putString("name", name).apply(); }
                        if (email != null) { tvEmail.setText(email); pref.edit().putString("email", email).apply(); }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
        }
    }

    private void loadProfileImage() {
        SharedPreferences pref = getSharedPreferences("UserData", MODE_PRIVATE);
        String encoded = pref.getString("profile_image", "");
        if (!encoded.isEmpty()) decodeAndSetImage(encoded);

        if (userRef != null) {
            userRef.child("profileImage").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String enc = snapshot.getValue(String.class);
                        if (enc != null && !enc.isEmpty()) {
                            decodeAndSetImage(enc);
                            pref.edit().putString("profile_image", enc).apply();
                        }
                    }
                }
                @Override public void onCancelled(@NonNull DatabaseError e) {}
            });
        }
    }

    private void decodeAndSetImage(String encoded) {
        try {
            byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bmp != null) updateProfileImageView(bmp);
        } catch (Exception ignored) {}
    }

    private void updateProfileImageView(Bitmap bitmap) {
        runOnUiThread(() -> {
            ivProfileImage.setImageBitmap(bitmap);
            ivProfileImage.setPadding(0, 0, 0, 0);
            ivProfileImage.clearColorFilter();
        });
    }

    private void saveProfileImageLocally(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        getSharedPreferences("UserData", MODE_PRIVATE)
                .edit().putString("profile_image", encoded).apply();
    }

    private void uploadImageToFirebase(Bitmap bitmap) {
        if (userRef == null) return;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        String encoded = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        userRef.child("profileImage").setValue(encoded)
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Image sync failed", Toast.LENGTH_SHORT).show());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE PROFILE IMAGE
    // ─────────────────────────────────────────────────────────────────────────
    private void deleteProfileImage() {
        // Clear from SharedPreferences
        getSharedPreferences("UserData", MODE_PRIVATE).edit().remove("profile_image").apply();

        // Clear from Firebase
        if (userRef != null) {
            userRef.child("profileImage").removeValue().addOnFailureListener(e ->
                    Toast.makeText(this, "Failed to remove from cloud", Toast.LENGTH_SHORT).show());
        }

        // Reset UI to default state
        runOnUiThread(() -> {
            ivProfileImage.setImageResource(R.drawable.ic_profile);
            // Add padding back so the icon sits nicely in the center
            ivProfileImage.setPadding(30, 30, 30, 30);
            // Re-apply the gray color filter so it is visible against the white background
            ivProfileImage.setColorFilter(android.graphics.Color.parseColor("#BDBDBD"));
        });

        Toast.makeText(this, "Profile image removed", Toast.LENGTH_SHORT).show();
    }

    private void loadSafetyToolsState() {
        SharedPreferences pref = getSharedPreferences("SafetyTools", MODE_PRIVATE);
        boolean voiceEnabled = pref.getBoolean("voice_enabled", false);

        if (switchVoiceActivation != null) switchVoiceActivation.setChecked(voiceEnabled);

        if (voiceEnabled) {
            startForegroundService(new Intent(this, VoiceActivationService.class));
        }
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnAddImage.setOnClickListener(v ->
                pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        // Delete Image Listener
        btnDeleteImage.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Remove Profile Image")
                    .setMessage("Are you sure you want to remove your profile picture?")
                    .setPositiveButton("Remove", (dialog, which) -> deleteProfileImage())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        rowPersonalInfo.setOnClickListener(v ->
                startActivity(new Intent(this, PersonalInfoActivity.class)));

        rowChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        rowTrustedContacts.setOnClickListener(v ->
                startActivity(new Intent(this, EmergencyContactActivity.class)));

        switchVoiceActivation.setOnCheckedChangeListener((btn, isChecked) -> {
            getSharedPreferences("SafetyTools", MODE_PRIVATE)
                    .edit().putBoolean("voice_enabled", isChecked).apply();

            Intent voiceIntent = new Intent(this, VoiceActivationService.class);
            if (isChecked) {
                startForegroundService(voiceIntent);
            } else {
                stopService(voiceIntent);
            }

            Toast.makeText(this,
                    isChecked ? "✅ Voice Activation Enabled" : "Voice Activation Disabled",
                    Toast.LENGTH_SHORT).show();
        });

        rowQuickMessage.setOnClickListener(v -> showQuickMessageDialog());

        if (rowAppGuide != null) {
            rowAppGuide.setOnClickListener(v -> showAppGuideDialog());
        }

        rowPrivacySecurity.setOnClickListener(v -> showPrivacyInfoDialog());

        rowDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        btnLogout.setOnClickListener(v -> showLogoutConfirmDialog());
    }

    private void showAppGuideDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_app_guide, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView btnClose = dialogView.findViewById(R.id.btnGuideClose);
        View btnGotIt = dialogView.findViewById(R.id.btnGuideGotIt);

        View.OnClickListener dismissListener = v -> dialog.dismiss();
        if (btnClose != null) btnClose.setOnClickListener(dismissListener);
        if (btnGotIt != null) btnGotIt.setOnClickListener(dismissListener);

        android.widget.VideoView videoView = dialogView.findViewById(R.id.videoGuideDemo);
        if (videoView != null) {
            String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.app_demo_video;
            android.net.Uri uri = android.net.Uri.parse(videoPath);
            videoView.setVideoURI(uri);

            videoView.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                mp.setVolume(0f, 0f);
                videoView.start();
            });
        }

        TextView btnAppDemoOverlay = dialogView.findViewById(R.id.btnAppDemoOverlay);
        if (btnAppDemoOverlay != null) {
            btnAppDemoOverlay.setOnClickListener(v -> {
                if (videoView != null && videoView.isPlaying()) {
                    videoView.pause();
                }
                showFullScreenVideoDialog();
            });
        }

        dialog.show();
    }

    private void showFullScreenVideoDialog() {
        android.app.Dialog fullScreenDialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        android.widget.RelativeLayout layout = new android.widget.RelativeLayout(this);
        layout.setBackgroundColor(android.graphics.Color.BLACK);

        android.widget.VideoView fullVideoView = new android.widget.VideoView(this);
        android.widget.RelativeLayout.LayoutParams videoParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT);
        videoParams.addRule(android.widget.RelativeLayout.CENTER_IN_PARENT);
        layout.addView(fullVideoView, videoParams);

        ImageView closeBtn = new ImageView(this);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setColorFilter(android.graphics.Color.WHITE);
        closeBtn.setPadding(40, 40, 40, 40);

        android.widget.RelativeLayout.LayoutParams closeParams = new android.widget.RelativeLayout.LayoutParams(
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT);
        closeParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP);
        closeParams.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
        closeParams.setMargins(0, 30, 30, 0);
        layout.addView(closeBtn, closeParams);

        fullScreenDialog.setContentView(layout);

        String videoPath = "android.resource://" + getPackageName() + "/" + R.raw.app_demo_video;
        fullVideoView.setVideoURI(android.net.Uri.parse(videoPath));

        android.widget.MediaController mediaController = new android.widget.MediaController(this);
        mediaController.setAnchorView(fullVideoView);
        fullVideoView.setMediaController(mediaController);

        fullVideoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            fullVideoView.start();
        });

        closeBtn.setOnClickListener(v -> fullScreenDialog.dismiss());

        fullScreenDialog.show();
    }

    private void showChangePasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        ViewFlipper viewFlipper   = dialogView.findViewById(R.id.viewFlipper);
        ImageView   btnDialogBack = dialogView.findViewById(R.id.btnDialogBack);
        EditText etCurrentPassword = dialogView.findViewById(R.id.etCurrentPassword);
        EditText etNewPassword     = dialogView.findViewById(R.id.etNewPassword);
        EditText etConfirmPassword = dialogView.findViewById(R.id.etConfirmPassword);
        ImageButton btnToggleCurrent = dialogView.findViewById(R.id.btnToggleCurrentPassword);
        ImageButton btnToggleNew     = dialogView.findViewById(R.id.btnToggleNewPassword);
        ImageButton btnToggleConfirm = dialogView.findViewById(R.id.btnToggleConfirmPassword);
        TextView tvCurrentError  = dialogView.findViewById(R.id.tvCurrentPasswordError);
        TextView tvNewError      = dialogView.findViewById(R.id.tvNewPasswordError);
        TextView tvConfirmError  = dialogView.findViewById(R.id.tvConfirmPasswordError);
        TextView ruleMinLength = dialogView.findViewById(R.id.ruleMinLength);
        TextView ruleUppercase = dialogView.findViewById(R.id.ruleUppercase);
        TextView ruleDigit     = dialogView.findViewById(R.id.ruleDigit);
        TextView ruleSpecial   = dialogView.findViewById(R.id.ruleSpecial);
        Button btnConfirmChange = dialogView.findViewById(R.id.btnConfirmChange);
        EditText etSendCodeUsername = dialogView.findViewById(R.id.etSendCodeUsername);
        EditText etSendCodeEmail    = dialogView.findViewById(R.id.etSendCodeEmail);
        Button   btnSendRecovery    = dialogView.findViewById(R.id.btnSendRecoveryCode);
        ProgressBar progressScreen1 = dialogView.findViewById(R.id.progressSendCode);
        TextView tvSendCodeError    = dialogView.findViewById(R.id.tvSendCodeError);
        TextView tvTryAnotherWay    = dialogView.findViewById(R.id.tvTryAnotherWay);
        EditText otpBox1 = dialogView.findViewById(R.id.otpBox1);
        EditText otpBox2 = dialogView.findViewById(R.id.otpBox2);
        EditText otpBox3 = dialogView.findViewById(R.id.otpBox3);
        EditText otpBox4 = dialogView.findViewById(R.id.otpBox4);
        EditText otpBox5 = dialogView.findViewById(R.id.otpBox5);
        EditText otpBox6 = dialogView.findViewById(R.id.otpBox6);
        EditText[] otpBoxes = {otpBox1, otpBox2, otpBox3, otpBox4, otpBox5, otpBox6};
        Button      btnVerifyCode    = dialogView.findViewById(R.id.btnVerifyCode);
        ProgressBar progressScreen2  = dialogView.findViewById(R.id.progressVerifyCode);
        TextView    tvOtpError       = dialogView.findViewById(R.id.tvOtpError);
        TextView    tvResendCode     = dialogView.findViewById(R.id.tvResendCode);
        TextView    tvResendCountdown= dialogView.findViewById(R.id.tvResendCountdown);
        TextView    tvVerifySubtitle = dialogView.findViewById(R.id.tvVerifySubtitle);

        final String[] generatedOtp = {""};
        final boolean[] showCurrent = {false};
        final boolean[] showNew     = {false};
        final boolean[] showConfirm = {false};

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser != null) {
            if (firebaseUser.getDisplayName() != null)
                etSendCodeUsername.setText(firebaseUser.getDisplayName());
            if (firebaseUser.getEmail() != null)
                etSendCodeEmail.setText(firebaseUser.getEmail());
        }

        Runnable[] showScreen = new Runnable[3];
        showScreen[0] = () -> {
            viewFlipper.setDisplayedChild(0);
            btnDialogBack.setVisibility(View.GONE);
        };
        showScreen[1] = () -> {
            viewFlipper.setDisplayedChild(1);
            btnDialogBack.setVisibility(View.VISIBLE);
        };
        showScreen[2] = () -> {
            viewFlipper.setDisplayedChild(2);
            btnDialogBack.setVisibility(View.VISIBLE);
        };

        btnDialogBack.setOnClickListener(v -> {
            int current = viewFlipper.getDisplayedChild();
            if (current == 2) showScreen[1].run();
            else              showScreen[0].run();
        });

        btnToggleCurrent.setOnClickListener(v -> {
            showCurrent[0] = !showCurrent[0];
            int type = showCurrent[0]
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            etCurrentPassword.setInputType(type);
            etCurrentPassword.setSelection(etCurrentPassword.getText().length());
            btnToggleCurrent.setAlpha(showCurrent[0] ? 1.0f : 0.5f);
        });

        btnToggleNew.setOnClickListener(v -> {
            showNew[0] = !showNew[0];
            int type = showNew[0]
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            etNewPassword.setInputType(type);
            etNewPassword.setSelection(etNewPassword.getText().length());
            btnToggleNew.setAlpha(showNew[0] ? 1.0f : 0.5f);
        });

        btnToggleConfirm.setOnClickListener(v -> {
            showConfirm[0] = !showConfirm[0];
            int type = showConfirm[0]
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            etConfirmPassword.setInputType(type);
            etConfirmPassword.setSelection(etConfirmPassword.getText().length());
            btnToggleConfirm.setAlpha(showConfirm[0] ? 1.0f : 0.5f);
        });

        etNewPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                updatePasswordRules(s.toString(), ruleMinLength, ruleUppercase, ruleDigit, ruleSpecial);
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        btnConfirmChange.setOnClickListener(btnView -> {
            String current = etCurrentPassword.getText().toString().trim();
            String newPw   = etNewPassword.getText().toString().trim();
            String confirm = etConfirmPassword.getText().toString().trim();

            tvCurrentError.setVisibility(View.GONE);
            tvNewError.setVisibility(View.GONE);
            tvConfirmError.setVisibility(View.GONE);

            boolean hasError = false;

            if (current.isEmpty()) {
                tvCurrentError.setText("Current password is required.");
                tvCurrentError.setVisibility(View.VISIBLE);
                hasError = true;
            }

            String strengthError = getPasswordStrengthError(newPw);
            if (strengthError != null) {
                tvNewError.setText(strengthError);
                tvNewError.setVisibility(View.VISIBLE);
                hasError = true;
            }

            if (!newPw.isEmpty() && !confirm.isEmpty() && !newPw.equals(confirm)) {
                tvConfirmError.setText("Passwords do not match.");
                tvConfirmError.setVisibility(View.VISIBLE);
                hasError = true;
            } else if (confirm.isEmpty()) {
                tvConfirmError.setText("Please confirm your new password.");
                tvConfirmError.setVisibility(View.VISIBLE);
                hasError = true;
            }

            if (!current.isEmpty() && !newPw.isEmpty() && current.equals(newPw)) {
                tvNewError.setText("New password must be different from your current password.");
                tvNewError.setVisibility(View.VISIBLE);
                hasError = true;
            }

            if (hasError) return;

            performPasswordChange(current, newPw, dialog);
        });

        btnSendRecovery.setOnClickListener(v -> {
            tvSendCodeError.setVisibility(View.GONE);
            String email = etSendCodeEmail.getText().toString().trim();

            if (email.isEmpty()) {
                tvSendCodeError.setText("No email address found for your account.");
                tvSendCodeError.setVisibility(View.VISIBLE);
                return;
            }

            progressScreen1.setVisibility(View.VISIBLE);
            btnSendRecovery.setEnabled(false);

            generatedOtp[0] = generateOtp();

            mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                progressScreen1.setVisibility(View.GONE);
                btnSendRecovery.setEnabled(true);

                if (task.isSuccessful()) {
                    String masked = maskEmail(email);
                    tvVerifySubtitle.setText("Enter the 6-digit code sent to " + masked + ".");

                    showScreen[2].run();
                    startResendCountdown(tvResendCode, tvResendCountdown);
                } else {
                    String err = task.getException() != null
                            ? task.getException().getMessage()
                            : "Failed to send code. Try again.";
                    tvSendCodeError.setText(err);
                    tvSendCodeError.setVisibility(View.VISIBLE);
                }
            });
        });

        tvTryAnotherWay.setOnClickListener(v -> showScreen[0].run());

        setupOtpBoxes(otpBoxes);

        tvResendCode.setOnClickListener(v -> {
            if (!tvResendCode.isEnabled()) return;
            String email = etSendCodeEmail.getText().toString().trim();
            generatedOtp[0] = generateOtp();
            mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Code resent!", Toast.LENGTH_SHORT).show();
                    tvResendCode.setEnabled(false);
                    tvResendCode.setAlpha(0.4f);
                    startResendCountdown(tvResendCode, tvResendCountdown);
                } else {
                    Toast.makeText(this, "Failed to resend. Try again.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnVerifyCode.setOnClickListener(v -> {
            tvOtpError.setVisibility(View.GONE);

            StringBuilder entered = new StringBuilder();
            for (EditText box : otpBoxes) entered.append(box.getText().toString().trim());

            if (entered.length() < 6) {
                tvOtpError.setText("Please enter the complete 6-digit code.");
                tvOtpError.setVisibility(View.VISIBLE);
                return;
            }

            if (!generatedOtp[0].isEmpty() && !entered.toString().equals(generatedOtp[0])) {
                tvOtpError.setText("Incorrect code. Please check your email and try again.");
                tvOtpError.setVisibility(View.VISIBLE);
                return;
            }

            progressScreen2.setVisibility(View.VISIBLE);
            btnVerifyCode.setEnabled(false);

            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                return;
            }

            String newPw = etNewPassword.getText().toString().trim();
            user.updatePassword(newPw).addOnCompleteListener(updateTask -> {
                progressScreen2.setVisibility(View.GONE);
                btnVerifyCode.setEnabled(true);

                if (updateTask.isSuccessful()) {
                    dialog.dismiss();
                    Toast.makeText(this,
                            "✅ Password updated! Please log in again.",
                            Toast.LENGTH_LONG).show();
                    performLogout();
                } else {
                    String err = updateTask.getException() != null
                            ? updateTask.getException().getMessage()
                            : "Update failed. Try again.";
                    tvOtpError.setText("Error: " + err);
                    tvOtpError.setVisibility(View.VISIBLE);
                }
            });
        });

        dialog.show();
    }

    private void setupOtpBoxes(EditText[] boxes) {
        for (int i = 0; i < boxes.length; i++) {
            final int index = i;
            boxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1 && index < boxes.length - 1) {
                        boxes[index + 1].requestFocus();
                    } else if (s.length() == 0 && index > 0) {
                        boxes[index - 1].requestFocus();
                    }
                }
            });
        }
    }

    private String generateOtp() {
        int code = 100000 + new Random().nextInt(900000);
        return String.valueOf(code);
    }

    private String maskEmail(String email) {
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return email;
        return email.substring(0, 2) + "*****" + email.substring(atIdx);
    }

    private void startResendCountdown(TextView tvResend, TextView tvCountdown) {
        tvResend.setEnabled(false);
        tvResend.setAlpha(0.4f);
        new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvCountdown.setText("(" + (millisUntilFinished / 1000) + "s)");
            }
            @Override
            public void onFinish() {
                tvCountdown.setText("");
                tvResend.setEnabled(true);
                tvResend.setAlpha(1.0f);
            }
        }.start();
    }

    private String getPasswordStrengthError(String password) {
        if (password.isEmpty()) {
            return "New password is required.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least 1 uppercase letter.";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least 1 digit.";
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) {
            return "Password must contain at least 1 special character.";
        }
        return null;
    }

    private void updatePasswordRules(String pw,
                                     TextView ruleMinLength,
                                     TextView ruleUppercase,
                                     TextView ruleDigit,
                                     TextView ruleSpecial) {
        String PASS = "#2ECC71";
        String FAIL = "#E74C3C";
        String CHECK = "✓  ";
        String CROSS = "✗  ";

        boolean lenOk     = pw.length() >= 8;
        boolean upperOk   = pw.matches(".*[A-Z].*");
        boolean digitOk   = pw.matches(".*[0-9].*");
        boolean specialOk = pw.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

        ruleMinLength.setText(CHECK + "At least 8 characters");
        ruleMinLength.setTextColor(android.graphics.Color.parseColor(lenOk ? PASS : FAIL));
        if (!lenOk) ruleMinLength.setText(CROSS + "At least 8 characters");

        ruleUppercase.setText(CHECK + "At least 1 uppercase letter (A-Z)");
        ruleUppercase.setTextColor(android.graphics.Color.parseColor(upperOk ? PASS : FAIL));
        if (!upperOk) ruleUppercase.setText(CROSS + "At least 1 uppercase letter (A-Z)");

        ruleDigit.setText(CHECK + "At least 1 digit (0-9)");
        ruleDigit.setTextColor(android.graphics.Color.parseColor(digitOk ? PASS : FAIL));
        if (!digitOk) ruleDigit.setText(CROSS + "At least 1 digit (0-9)");

        ruleSpecial.setText(CHECK + "At least 1 special character (!@#$%^&*)");
        ruleSpecial.setTextColor(android.graphics.Color.parseColor(specialOk ? PASS : FAIL));
        if (!specialOk) ruleSpecial.setText(CROSS + "At least 1 special character (!@#$%^&*)");
    }

    private void performPasswordChange(String currentPassword, String newPassword, AlertDialog dialog) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
            if (reAuthTask.isSuccessful()) {
                user.updatePassword(newPassword).addOnCompleteListener(updateTask -> {
                    if (updateTask.isSuccessful()) {
                        dialog.dismiss();
                        Toast.makeText(this,
                                "✅ Password updated! Please log in again.",
                                Toast.LENGTH_LONG).show();
                        performLogout();
                    } else {
                        String err = updateTask.getException() != null
                                ? updateTask.getException().getMessage()
                                : "Update failed. Try again.";
                        Toast.makeText(this, "Error: " + err, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                View dialogView = dialog.findViewById(R.id.tvCurrentPasswordError);
                if (dialogView instanceof TextView) {
                    ((TextView) dialogView).setText("Incorrect current password. Please try again.");
                    dialogView.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(this,
                            "Current password is incorrect.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showQuickMessageDialog() {
        SharedPreferences pref = getSharedPreferences("SafetyTools", MODE_PRIVATE);
        String savedMsg = pref.getString("quick_message",
                "🚨 I am in danger! Please help me immediately. - Sent via Femora");

        EditText etMessage = new EditText(this);
        etMessage.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etMessage.setLines(4);
        etMessage.setMaxLines(6);
        etMessage.setText(savedMsg);
        etMessage.setSelection(etMessage.getText().length());
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        etMessage.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("📩 Quick Emergency Message")
                .setMessage("This message will be sent to your trusted contacts when SOS is triggered.")
                .setView(etMessage)
                .setPositiveButton("Save", (dialog, which) -> {
                    String msg = etMessage.getText().toString().trim();
                    if (msg.isEmpty()) {
                        Toast.makeText(this, "Message cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pref.edit().putString("quick_message", msg).apply();
                    Toast.makeText(this, "✅ Emergency message saved!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPrivacyInfoDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🛡 Privacy & Security")
                .setMessage(
                        "Femora takes your privacy seriously:\n\n"
                                + "• Your location is ONLY shared during SOS events.\n\n"
                                + "• Your data is encrypted and stored securely on Firebase.\n\n"
                                + "• We do NOT sell or share your personal data with third parties.\n\n"
                                + "• You can delete your account and all associated data at any time.\n\n"
                                + "• Trusted contact numbers are stored locally and on Firebase for offline SOS."
                )
                .setPositiveButton("Got it", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ Delete Account")
                .setMessage(
                        "Are you sure you want to permanently delete your account?\n\n"
                                + "This will remove:\n"
                                + "• Your profile and personal info\n"
                                + "• All trusted contacts\n"
                                + "• All safety settings\n\n"
                                + "⚠️ This action CANNOT be undone."
                )
                .setPositiveButton("Continue", (dialog, which) -> showDeleteConfirmPasswordDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmPasswordDialog() {
        EditText etPassword = new EditText(this);
        etPassword.setHint("Enter your current password");
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        etPassword.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Enter your password to permanently delete your account.")
                .setView(etPassword)
                .setPositiveButton("Delete Permanently", (dialog, which) -> {
                    String pw = etPassword.getText().toString().trim();
                    if (pw.isEmpty()) {
                        Toast.makeText(this, "Password is required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performAccountDeletion(pw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performAccountDeletion(String password) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
            if (reAuthTask.isSuccessful()) {
                if (userRef != null) {
                    userRef.removeValue().addOnCompleteListener(dbTask -> {
                        user.delete().addOnCompleteListener(deleteTask -> {
                            if (deleteTask.isSuccessful()) {
                                clearLocalDataAndExit();
                                Toast.makeText(this,
                                        "Account deleted successfully.", Toast.LENGTH_SHORT).show();
                            } else {
                                String err = deleteTask.getException() != null
                                        ? deleteTask.getException().getMessage()
                                        : "Deletion failed";
                                Toast.makeText(this, "Error: " + err, Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                }
            } else {
                Toast.makeText(this,
                        "Incorrect password. Account not deleted.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLogoutConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of Femora?")
                .setPositiveButton("Log Out", (dialog, which) -> performLogout())
                .setNegativeButton("Stay", null)
                .show();
    }

    private void performLogout() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            clearLocalDataAndExit();
            Toast.makeText(this, "Logged out successfully.", Toast.LENGTH_SHORT).show();
        });
    }

    private void clearLocalDataAndExit() {
        getSharedPreferences("UserData",       MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("FemoraContacts", MODE_PRIVATE).edit().clear().apply();
        getSharedPreferences("SafetyTools",    MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupBottomNav() {
        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_map) {
                startActivity(new Intent(this, MapActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_tips) {
                startActivity(new Intent(this, SafetyTipsActivity.class));
                finish();
                return true;
            }
            return true;
        });
    }
}
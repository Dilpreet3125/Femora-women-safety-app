package com.example.femora;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class EmergencyContactActivity extends AppCompatActivity {

    LinearLayout addCard;
    LinearLayout emptyLayout;
    LinearLayout contactContainer;
    Button btnContinue;

    ArrayList<ContactModel> contactList = new ArrayList<>();
    FirebaseAuth mAuth;
    String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";

    // Contact Picker Launcher
    ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri contactUri = result.getData().getData();

                    Cursor cursor = getContentResolver().query(contactUri, null, null, null, null);

                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                        String name = cursor.getString(nameIndex);
                        String number = cursor.getString(numberIndex);

                        showRelationDialog(name, number);
                        cursor.close();
                    }
                }
            });

    private void showRelationDialog(String name, String number) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Select Relation");
        String[] relations = {"Mother", "Father", "Sibling", "Friend", "Relative", "Other"};
        builder.setItems(relations, (dialog, which) -> {
            String relation = relations[which];
            addContactToUI(name, number, relation);
            saveContactsToFirebase(false); // Silent s
            // ave
        });
        builder.show();
    }

    // Permission Launcher
    ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openContacts();
                } else {
                    Toast.makeText(this, "Permission Required to add contacts", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contact);

        mAuth = FirebaseAuth.getInstance();

        // Initialize Views
        addCard = findViewById(R.id.addCard);
        emptyLayout = findViewById(R.id.emptyLayout);
        contactContainer = findViewById(R.id.contactContainer);
        btnContinue = findViewById(R.id.btnContinue);

        // Add Contact Click
        addCard.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED) {
                openContacts();
            } else {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });

        // Continue Button Click
        btnContinue.setOnClickListener(v -> {
            if (contactList.isEmpty()) {
                Toast.makeText(this, "Please add at least one contact", Toast.LENGTH_SHORT).show();
            } else {
                saveContactsToFirebase(true); // Navigate after save
            }
        });

        // Load existing contacts
        loadContactsFromFirebase();
    }

    private void loadContactsFromFirebase() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        DatabaseReference db = FirebaseDatabase.getInstance(dbUrl).getReference("users").child(userId).child("emergencyContacts");

        db.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    contactList.clear();
                    contactContainer.removeAllViews();
                    
                    for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                        ContactModel contact = contactSnapshot.getValue(ContactModel.class);
                        if (contact != null) {
                            addContactToUI(contact.name, contact.number, contact.relation);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    private void saveContactsToFirebase(boolean navigateToDashboard) {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        DatabaseReference db = FirebaseDatabase.getInstance(dbUrl).getReference("users").child(userId).child("emergencyContacts");

        if (navigateToDashboard) {
            btnContinue.setEnabled(false);
            btnContinue.setText("Saving...");
        }

        db.setValue(contactList)
                .addOnSuccessListener(aVoid -> {
                    if (navigateToDashboard) {
                        Toast.makeText(this, "Contacts saved successfully!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(EmergencyContactActivity.this, MainActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    if (navigateToDashboard) {
                        btnContinue.setEnabled(true);
                        btnContinue.setText("Back to Dashboard");
                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openContacts() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void addContactToUI(String name, String number, String relation) {
        // Create Model
        ContactModel model = new ContactModel(name, number, relation);
        
        // Check for duplicates
        boolean exists = false;
        for(ContactModel cm : contactList) {
            if(cm.number.equals(number)) { exists = true; break; }
        }
        if(!exists) contactList.add(model);

        // Hide empty state and show container
        emptyLayout.setVisibility(View.GONE);
        contactContainer.setVisibility(View.VISIBLE);

        // Inflate custom layout
        View contactView = LayoutInflater.from(this).inflate(R.layout.item_emergency_contact, contactContainer, false);

        TextView tvName = contactView.findViewById(R.id.tvContactName);
        TextView tvPhone = contactView.findViewById(R.id.tvContactPhone);
        TextView tvRelation = contactView.findViewById(R.id.tvRelation);
        TextView tvInitial = contactView.findViewById(R.id.tvContactInitial);
        ImageView btnRemove = contactView.findViewById(R.id.btnRemoveContact);
        ImageView btnCall = contactView.findViewById(R.id.btnCallContact);

        tvName.setText(name);
        tvPhone.setText(number);
        tvRelation.setText(relation != null ? relation : "Relation");
        
        if (name != null && !name.isEmpty()) {
            tvInitial.setText(name.substring(0, 1).toUpperCase());
        }

        btnRemove.setOnClickListener(v -> {
            contactContainer.removeView(contactView);
            contactList.remove(model);
            if (contactList.isEmpty()) {
                emptyLayout.setVisibility(View.VISIBLE);
                contactContainer.setVisibility(View.GONE);
                resetContinueButton();
            }
            saveContactsToFirebase(false); // Update DB on removal
        });

        btnCall.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:" + number));
            startActivity(intent);
        });

        contactContainer.addView(contactView);

        // Enable and style button
        btnContinue.setText("Back to Dashboard");
        btnContinue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF3D7F")));
        btnContinue.setTextColor(Color.WHITE);
        btnContinue.setEnabled(true);
    }

    private void resetContinueButton() {
        btnContinue.setText("Add contact to continue");
        btnContinue.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F2F4F9")));
        btnContinue.setTextColor(Color.parseColor("#B0B5D1"));
        btnContinue.setEnabled(false);
    }

    // Simple model class
    public static class ContactModel {
        public String name, number, relation;
        public ContactModel() {} // Required for Firebase
        public ContactModel(String name, String number, String relation) {
            this.name = name;
            this.number = number;
            this.relation = relation;
        }
    }
}
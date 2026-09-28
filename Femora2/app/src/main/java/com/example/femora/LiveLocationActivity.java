package com.example.femora;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LiveLocationActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvCurrentAddress;
    private ImageButton btnBack;
    private FirebaseAuth mAuth;
    private String dbUrl = "https://femora-f09c5-default-rtdb.asia-southeast1.firebasedatabase.app";
    private List<LatLng> markerPositions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_location);

        mAuth = FirebaseAuth.getInstance();
        tvCurrentAddress = findViewById(R.id.tvCurrentAddress);
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(true);

        // Get User Location
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng current = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.addMarker(new MarkerOptions()
                        .position(current)
                        .title("You are here")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                
                markerPositions.add(current);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(current, 15f));

                updateAddressText(location.getLatitude(), location.getLongitude());
                
                // Fetch and show contacts on map
                fetchAndShowContacts();
            } else {
                Toast.makeText(this, "Could not get your current location. Please check GPS.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchAndShowContacts() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        // Updated path to match EmergencyContactActivity
        DatabaseReference contactsRef = FirebaseDatabase.getInstance(dbUrl)
                .getReference("users")
                .child(userId)
                .child("emergencyContacts");

        contactsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    boolean contactsFoundOnMap = false;
                    for (DataSnapshot contactSnapshot : snapshot.getChildren()) {
                        String name = contactSnapshot.child("name").getValue(String.class);
                        
                        // Try to get coordinates. Note: These need to be updated in the DB by the contacts themselves 
                        // or via a service. For now, we'll try to find them if they exist.
                        Double lat = contactSnapshot.child("latitude").getValue(Double.class);
                        Double lng = contactSnapshot.child("longitude").getValue(Double.class);

                        if (lat != null && lng != null) {
                            LatLng contactPos = new LatLng(lat, lng);
                            mMap.addMarker(new MarkerOptions()
                                    .position(contactPos)
                                    .title(name)
                                    .snippet("Emergency Contact")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                            
                            markerPositions.add(contactPos);
                            contactsFoundOnMap = true;
                        }
                    }

                    // If we have multiple markers, adjust camera to show all
                    if (contactsFoundOnMap && markerPositions.size() > 1) {
                        adjustCameraToMarkers();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("LiveLocation", "Database error: " + error.getMessage());
            }
        });
    }

    private void adjustCameraToMarkers() {
        if (markerPositions.isEmpty()) return;

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng pos : markerPositions) {
            builder.include(pos);
        }
        LatLngBounds bounds = builder.build();
        int padding = 200; // offset from edges of the map in pixels
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
    }

    private void updateAddressText(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                tvCurrentAddress.setText(address);
            }
        } catch (IOException e) {
            tvCurrentAddress.setText("Lat: " + lat + ", Lng: " + lng);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onMapReady(mMap);
        } else if (requestCode == 100) {
            Toast.makeText(this, "Location permission is required to show the map", Toast.LENGTH_LONG).show();
        }
    }
}
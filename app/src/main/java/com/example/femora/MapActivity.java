package com.example.femora;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private TextView tvCurrentAddress, tvDistance, tvLocationTitle;
    private EditText etSearch;
    private ImageButton btnBack;
    private ImageView btnSearch;
    private CardView btnMyLocation, btnLayers;
    private Button btnDirections;
    private LatLng userLatLng, destinationLatLng;
    private Polyline routePolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map); // Use the correct layout for advanced features

        // Initialize Views
        tvCurrentAddress = findViewById(R.id.tvCurrentAddress);
        tvDistance = findViewById(R.id.tvDistance);
        tvLocationTitle = findViewById(R.id.tvLocationTitle);
        etSearch = findViewById(R.id.etSearch);
        btnBack = findViewById(R.id.btnBack);
        btnSearch = findViewById(R.id.btnSearch);
        btnMyLocation = findViewById(R.id.btnMyLocation);
        btnLayers = findViewById(R.id.btnLayers);
        btnDirections = findViewById(R.id.btnDirections);

        btnBack.setOnClickListener(v -> finish());

        // Search logic
        btnSearch.setOnClickListener(v -> searchLocation());
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchLocation();
                return true;
            }
            return false;
        });

        // My Location Button
        btnMyLocation.setOnClickListener(v -> {
            if (userLatLng != null && mMap != null) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f));
            }
        });

        // Map Layers Toggle
        btnLayers.setOnClickListener(v -> {
            if (mMap == null) return;
            int type = mMap.getMapType();
            if (type == GoogleMap.MAP_TYPE_NORMAL) mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            else if (type == GoogleMap.MAP_TYPE_SATELLITE) mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
            else mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        });

        // Directions Button
        btnDirections.setOnClickListener(v -> {
            if (destinationLatLng != null) {
                Uri gmmIntentUri = Uri.parse("google.navigation:q=" + destinationLatLng.latitude + "," + destinationLatLng.longitude);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    Toast.makeText(this, "Google Maps not installed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    private void searchLocation() {
        String locationName = etSearch.getText().toString();
        if (locationName.isEmpty()) return;

        Geocoder geocoder = new Geocoder(this);
        try {
            List<Address> addressList = geocoder.getFromLocationName(locationName, 1);
            if (addressList != null && !addressList.isEmpty()) {
                Address address = addressList.get(0);
                destinationLatLng = new LatLng(address.getLatitude(), address.getLongitude());

                mMap.clear();
                mMap.addMarker(new MarkerOptions()
                        .position(destinationLatLng)
                        .title(locationName)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                tvLocationTitle.setText(locationName);
                tvCurrentAddress.setText(address.getAddressLine(0));

                if (userLatLng != null) {
                    drawRoute(userLatLng, destinationLatLng);
                    float[] results = new float[1];
                    Location.distanceBetween(userLatLng.latitude, userLatLng.longitude,
                            destinationLatLng.latitude, destinationLatLng.longitude, results);
                    tvDistance.setText(String.format(Locale.US, "%.2f km", results[0] / 1000));

                    LatLngBounds bounds = new LatLngBounds.Builder()
                            .include(userLatLng)
                            .include(destinationLatLng)
                            .build();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 250));
                }
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Search error", Toast.LENGTH_SHORT).show();
        }
    }

    private void drawRoute(LatLng start, LatLng end) {
        if (routePolyline != null) routePolyline.remove();
        routePolyline = mMap.addPolyline(new PolylineOptions()
                .add(start, end)
                .width(12)
                .color(Color.parseColor("#1A73E8"))
                .geodesic(true));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(false); 

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f));
                updateAddressText(location.getLatitude(), location.getLongitude());
            }
        });
    }

    private void updateAddressText(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                tvCurrentAddress.setText(addresses.get(0).getAddressLine(0));
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onMapReady(mMap);
        }
    }
}
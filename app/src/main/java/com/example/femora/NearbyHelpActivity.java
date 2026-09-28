package com.example.femora;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.model.PlaceTypes;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NearbyHelpActivity extends AppCompatActivity {

    private NearbyHelpAdapter adapter;
    private List<NearbyHelp> helpList;
    private PlacesClient placesClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_help);

        // Initialize Places (Replace with your actual API Key)
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyByBbSMYhzRs6RjtGFdtzV-6Lqf_Ro6aS0");
        }
        placesClient = Places.createClient(this);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        RecyclerView rvNearbyHelp = findViewById(R.id.rvNearbyHelp);
        rvNearbyHelp.setLayoutManager(new LinearLayoutManager(this));

        helpList = new ArrayList<>();
        adapter = new NearbyHelpAdapter(this, helpList);
        rvNearbyHelp.setAdapter(adapter);

        getCurrentLocationAndFetchHelp();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void getCurrentLocationAndFetchHelp() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.PHONE_NUMBER, Place.Field.TYPES);
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

        placesClient.findCurrentPlace(request).addOnSuccessListener(((response) -> {
            helpList.clear();
            for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                Place place = placeLikelihood.getPlace();
                List<String> types = place.getPlaceTypes();
                
                if (types != null && (types.contains(PlaceTypes.POLICE) || types.contains(PlaceTypes.HOSPITAL))) {
                    helpList.add(new NearbyHelp(
                            place.getName(),
                            place.getAddress(),
                            "Nearby",
                            place.getPhoneNumber() != null ? place.getPhoneNumber() : "112",
                            types.contains(PlaceTypes.POLICE) ? android.R.drawable.ic_menu_compass : android.R.drawable.ic_menu_add
                    ));
                }
            }

            // Always add important national helplines
            helpList.add(new NearbyHelp("Women Helpline", "National Service", "Active", "1091", android.R.drawable.ic_menu_call));
            helpList.add(new NearbyHelp("Emergency Response", "Standard Service", "Active", "112", android.R.drawable.ic_menu_call));

            adapter.notifyDataSetChanged();
        })).addOnFailureListener((exception) -> {
            Log.e("NearbyHelp", "Places fetch failed: " + exception.getMessage());
            loadNationalEmergencyNumbers();
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadNationalEmergencyNumbers() {
        helpList.clear();
        helpList.add(new NearbyHelp("Police Station", "Check Map for Local", "Local", "100", android.R.drawable.ic_menu_compass));
        helpList.add(new NearbyHelp("Emergency Hospital", "Check Map for Local", "Local", "102", android.R.drawable.ic_menu_add));
        helpList.add(new NearbyHelp("Women Helpline", "National Service", "Always Active", "1091", android.R.drawable.ic_menu_call));
        helpList.add(new NearbyHelp("Emergency Response", "Standard Service", "Always Active", "112", android.R.drawable.ic_menu_call));
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocationAndFetchHelp();
        }
    }
}
package com.abc.googlemap2;

import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import com.google.maps.android.PolyUtil;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap googleMap;
    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationProviderClient;

    private AutoCompleteTextView sourceAutoCompleteTextView;
    private AutoCompleteTextView destinationAutoCompleteTextView;

    private Location location;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        Places.initialize(getApplicationContext(), "AIzaSyBCWQQzcLm97h6sf1Y6I9LsFbB_OpYMDcQ");
        placesClient = Places.createClient(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        sourceAutoCompleteTextView = findViewById(R.id.sourceAutoCompleteTextView);
        destinationAutoCompleteTextView = findViewById(R.id.destinationAutoCompleteTextView);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line);
        sourceAutoCompleteTextView.setAdapter(adapter);
        destinationAutoCompleteTextView.setAdapter(adapter);

        sourceAutoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            AutocompletePrediction prediction = (AutocompletePrediction) parent.getItemAtPosition(position);
            String placeId = prediction.getPlaceId();
            getPlaceDetails(placeId, sourceAutoCompleteTextView);
        });

        destinationAutoCompleteTextView.setOnItemClickListener((parent, view, position, id) -> {
            AutocompletePrediction prediction = (AutocompletePrediction) parent.getItemAtPosition(position);
            String placeId = prediction.getPlaceId();
            getPlaceDetails(placeId, destinationAutoCompleteTextView);
        });

        getCurrentLocation();

        Button findRouteButton = findViewById(R.id.findRouteButton);
        findRouteButton.setOnClickListener(view -> {
            String destinationLocation = destinationAutoCompleteTextView.getText().toString();
            if (!destinationLocation.isEmpty()) {
                getPlaceDetails(destinationLocation, destinationAutoCompleteTextView);
            } else {
                Toast.makeText(this, "Please enter a destination location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getCurrentLocation() {
        try {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(this, currentLocation -> {
                        if (currentLocation != null) {
                            location = currentLocation;
                            LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                            if (googleMap != null) {
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
                                googleMap.addMarker(new MarkerOptions().position(currentLatLng)
                                        .title("You are here")
                                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                                sourceAutoCompleteTextView.setText("Current Location");
                                sourceAutoCompleteTextView.setTag(currentLatLng);
                                destinationAutoCompleteTextView.setHint("Enter destination location");
                            }
                        }
                    })
                    .addOnFailureListener(this, e -> {
                        Toast.makeText(this, "Failed to get current location", Toast.LENGTH_SHORT).show();
                    });
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private void getPlaceDetails(String placeId, AutoCompleteTextView autoCompleteTextView) {
        List<Place.Field> placeFields = Arrays.asList(Place.Field.LAT_LNG, Place.Field.NAME);

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields).build();

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    LatLng latLng = place.getLatLng();
                    if (latLng != null) {
                        autoCompleteTextView.setTag(latLng);
                        if (googleMap != null) {
                            googleMap.clear();
                            googleMap.addMarker(new MarkerOptions().position(latLng).title(place.getName()));
                            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                            drawRoute(currentLatLng, latLng);
                        }
                    }
                })
                .addOnFailureListener(exception -> {
                    Toast.makeText(this, "Place details request failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void drawRoute(LatLng origin, LatLng destination) {
        GeoApiContext context = getGeoContext();

        DirectionsApiRequest directions = DirectionsApi.newRequest(context)
                .origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                .mode(TravelMode.DRIVING);

        try {
            DirectionsResult result = directions.await();
            if (result.routes != null && result.routes.length > 0) {
                List<LatLng> decodedPath = PolyUtil.decode(result.routes[0].overviewPolyline.getEncodedPath());
                PolylineOptions polylineOptions = new PolylineOptions()
                        .addAll(decodedPath)
                        .width(10)
                        .color(Color.BLACK);

                Polyline polyline = googleMap.addPolyline(polylineOptions);

                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                for (LatLng point : decodedPath) {
                    builder.include(point);
                }
                LatLngBounds bounds = builder.build();
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private GeoApiContext getGeoContext() {
        // Replace YOUR_GOOGLE_MAPS_DIRECTION_API_KEY with your actual Directions API key
        return new GeoApiContext.Builder()
                .apiKey("AIzaSyCF1I2bJuWD1joDNGw-EGwfkbKbbxenP3E")
                .build();
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        // Additional map setup can be done here
    }
}

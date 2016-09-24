package com.github.rasmussaks.akenajalukku;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.akexorcist.googledirection.DirectionCallback;
import com.akexorcist.googledirection.GoogleDirection;
import com.akexorcist.googledirection.model.Direction;
import com.akexorcist.googledirection.model.Route;
import com.akexorcist.googledirection.util.DirectionConverter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DirectionCallback {

    private static final int REQUEST_TO_SETUP_MAP = 1;
    private static String TAG = "aken-ajalukku";
    private static LatLng TEST_MARKER = new LatLng(58.3806563, 26.7241506);
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_TO_SETUP_MAP);
            return;
        }
        setupMap();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_TO_SETUP_MAP:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    setupMap();
                break;
        }
    }

    @SuppressWarnings("MissingPermission")
    private void setupMap() {
        map.setMyLocationEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        updateMap();
    }

    private void updateMap() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        }
        if (lastLocation != null && map != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 15));
            Log.v(TAG, "Getting directions");
            GoogleDirection
                    .withServerKey(null)
                    .from(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()))
                    .to(TEST_MARKER)
                    .transportMode("walking")
                    .execute(this);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        updateMap();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDirectionSuccess(Direction direction, String rawBody) {
        Log.v(TAG, "Got directions");
        Log.v(TAG, rawBody);
        if (direction.isOK()) {
            map.addMarker(new MarkerOptions().position(TEST_MARKER));
            Log.v(TAG, direction.getRouteList().toString());
            Route route = direction.getRouteList().get(0);
            ArrayList<LatLng> points = new ArrayList<>(route.getOverviewPolyline().getPointList());
            map.addPolyline(DirectionConverter.createPolyline(this, points, 5, Color.RED));
            LatLngBounds bnds = LatLngBounds.builder().include(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())).include(TEST_MARKER).build();
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bnds, 100));
        }
    }

    @Override
    public void onDirectionFailure(Throwable t) {

    }
}
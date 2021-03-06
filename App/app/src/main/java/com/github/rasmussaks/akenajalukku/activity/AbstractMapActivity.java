package com.github.rasmussaks.akenajalukku.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;

import com.github.rasmussaks.akenajalukku.R;
import com.github.rasmussaks.akenajalukku.fragment.DrawerFragment;
import com.github.rasmussaks.akenajalukku.fragment.JourneyFragment;
import com.github.rasmussaks.akenajalukku.fragment.JourneySelectionDrawerFragment;
import com.github.rasmussaks.akenajalukku.fragment.POIDrawerFragment;
import com.github.rasmussaks.akenajalukku.layout.NoTouchSlidingUpPanelLayout;
import com.github.rasmussaks.akenajalukku.model.Data;
import com.github.rasmussaks.akenajalukku.model.PointOfInterest;
import com.github.rasmussaks.akenajalukku.util.Constants;
import com.github.rasmussaks.akenajalukku.util.DirectionsTask;
import com.github.rasmussaks.akenajalukku.util.DirectionsTaskListener;
import com.github.rasmussaks.akenajalukku.util.DirectionsTaskResponse;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.Polyline;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.ArrayList;
import java.util.List;

import static com.github.rasmussaks.akenajalukku.util.Constants.TAG;

public abstract class AbstractMapActivity extends LocalizedActivity implements LocationListener,
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, DrawerFragment.DrawerFragmentListener,
        GoogleMap.OnMarkerClickListener, DirectionsTaskListener {

    private static final int REQUEST_TO_SETUP_MAP = 1;
    private static final int VIDEO_PLAYER_RESULT = 1;
    private boolean locationEnabled = false;
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private ArrayList<PointOfInterest> intentPois;
    private Location lastLocation;
    private Polyline currentPolyline;
    private SlidingUpPanelLayout drawerLayout;
    private List<PointOfInterest> highlightedPois = new ArrayList<>();
    private PointOfInterest currentDirections;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView();
        ActionBar bar = getSupportActionBar();
        if (bar != null) bar.hide();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("pois")) {
            intentPois = intent.getParcelableArrayListExtra("pois");
        }

        Fragment drawerFragment = getSupportFragmentManager().findFragmentById(R.id.drawer_container);
        if (drawerFragment != null) {
            ((DrawerFragment) drawerFragment).setDrawerFragmentListener(this);
        }
        if (savedInstanceState != null) {
            Data.instance = savedInstanceState.getParcelable("data");
            Log.d(TAG, "Load data " + Data.instance);
        }

        drawerLayout = (NoTouchSlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        drawerLayout.setPanelHeight(0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_TO_SETUP_MAP);
        } else {
            locationEnabled = true;
            loadMap();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("data", Data.instance);
    }

    public ArrayList<PointOfInterest> getPois() {
        return Data.instance.getPois();
    }

    public abstract void setContentView();

    @Override
    protected void onResume() {
        super.onResume();
        if (googleApiClient != null) {
            if (googleApiClient.isConnected())
                setupGoogleApiClient();
            else if (!googleApiClient.isConnecting())
                googleApiClient.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "Paused " + getClass().getSimpleName());

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "Started " + getClass().getSimpleName());
        if (googleApiClient != null) {
            googleApiClient.connect();
        }
        highlightedPois.clear();
        for (PointOfInterest poi : getPois()) {
            if (poi.getMarker() != null) {
                poi.getMarker().remove();
                poi.setMarker(null);
            }
        }
        if (map != null) {
            for (PointOfInterest poi : getPois()) {
                resetPoiMarker(poi);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "Stopped " + getClass().getSimpleName());

    }

    public boolean isLocationEnabled() {
        return locationEnabled;
    }

    public SlidingUpPanelLayout getDrawerLayout() {
        return drawerLayout;
    }

    public GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }

    public GoogleMap getMap() {
        return map;
    }

    private void loadMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        Log.i(TAG, "Map is ready");
        setupMap();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_TO_SETUP_MAP:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationEnabled = true;
                } else {
                    findViewById(R.id.location_button).setVisibility(View.GONE); //Hide location button if user hasn't given location permission
                }
                loadMap();
                break;
        }
    }

    public void onSettingsButtonClick(View view) {
        openSettings();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }


    @SuppressWarnings("MissingPermission")
    void setupMap() {
        map.clear();
        map.setOnMarkerClickListener(this);
        map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style));
        if (locationEnabled) map.setMyLocationEnabled(true);
        map.setPadding(0, getResources().getDimensionPixelSize(R.dimen.mapview_top_padding), 0, 0);
        map.getUiSettings().setMyLocationButtonEnabled(false);
        Log.d(TAG, "setupMap");
        highlightedPois.clear();
        for (PointOfInterest poi : getPois()) {
            if (poi.getMarker() != null) {
                poi.getMarker().remove();
                poi.setMarker(null);
            }
        }
        for (PointOfInterest poi : getPois()) {
            resetPoiMarker(poi);
        }
        startGoogleApiClient();

        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                AbstractMapActivity.this.onMapLoaded();
                highlightClosePois();
            }
        });
    }

    private void startGoogleApiClient() {
        //If location is enabled, start the Google API client
        if (googleApiClient == null && locationEnabled) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
            googleApiClient.connect();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIDEO_PLAYER_RESULT) {
            onVideoPlayerResult();
        }
    }

    public void onVideoPlayerResult() {

    }

    @SuppressWarnings("MissingPermission")
    public void resetCamera(boolean animate) {
        CameraUpdate update = null;
        if (map != null) {
            LatLngBounds.Builder bounds = LatLngBounds.builder();
            ArrayList<PointOfInterest> pois = getPois();

            //Show the PoIs the intent wanted to show us
            if (intentPois != null) {
                pois = intentPois;
                intentPois = null;
                if (pois.size() == 1) {
                    openPoiDetailDrawer(Data.instance.getPoiById(pois.get(0).getId()));
                }
            }
            for (PointOfInterest poi : pois) {
                bounds.include(poi.getLocation());
            }
            if (lastLocation != null && getDistanceBetween(new LatLng(lastLocation.getLatitude(),
                    lastLocation.getLongitude()), bounds.build().getCenter()) < 1000_000) {
                bounds.include(new LatLng(lastLocation.getLatitude(),
                        lastLocation.getLongitude()));
            }
            update = CameraUpdateFactory.newLatLngBounds(bounds.build(), 250);

        }
        if (update != null) {
            if (animate) {
                map.animateCamera(update);
            } else {
                map.moveCamera(update);
            }
        }
    }

    public void resetCameraOnUser() {
        if (lastLocation != null && locationEnabled) {
            CameraUpdate update = CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 15);
            map.animateCamera(update);
        }
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void removePolyline() {
        if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
        }
    }


    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @SuppressWarnings("MissingPermission")
    private void setupGoogleApiClient() {
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, createLocationRequest(), this);
        lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
    }

    public void showDirectionsTo(PointOfInterest poi) {
        if (getLastLocation() != null) {
            currentDirections = poi;
            new DirectionsTask(this, this).execute(
                    new LatLng(getLastLocation().getLatitude(), getLastLocation().getLongitude()),
                    poi.getLocation()
            );
        }
    }

    public void highlightPoiMarker(PointOfInterest poi) {
        if (poi != null) {
            if (highlightedPois.contains(poi))
                return; //Don't want to update markers when not necessary
            if (poi.getMarker() != null) poi.getMarker().remove();
            poi.setMarker(map.addMarker(poi.getMarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_mapmarker_play))));
            highlightedPois.add(poi);
        }
    }

    public void resetPoiMarker(PointOfInterest poi) {
        if (poi != null) {
            if (poi.getMarker() != null && !highlightedPois.contains(poi)) return;
            if (poi.getMarker() != null) poi.getMarker().remove();
            poi.setMarker(map.addMarker(poi.getMarkerOptions()));
            highlightedPois.remove(poi);
        }
    }

    @Override
    public void onDirectionsTaskResponse(DirectionsTaskResponse response) {
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        Log.v(TAG, "Got directions");
        if (response != null) {
            List<LatLng> points = response.getPolylineOptions().getPoints();
            LatLngBounds.Builder bnds = LatLngBounds.builder().include(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()));
            for (LatLng point : points) {
                bnds.include(point);
            }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bnds.build(), 250));
            currentPolyline = map.addPolyline(response.getPolylineOptions());
        }
    }

    public void onJourneyDetailSelect(int journeyId) {

    }

    public void onJourneyDetailButtonClick(int journeyId) {

    }

    public void openPoiDetailDrawer(PointOfInterest poi) {
        POIDrawerFragment fragment = new POIDrawerFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("poi", poi);
        bundle.putBoolean("close", isCloseTo(poi));
        fragment.setDrawerFragmentListener(this);
        fragment.setArguments(bundle);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.drawer_container, fragment);
        transaction.commit();
        drawerLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    public void openJourneySelectionDrawer() {
        JourneySelectionDrawerFragment fragment = new JourneySelectionDrawerFragment();
        fragment.setDrawerFragmentListener(this);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.drawer_container, fragment);
        transaction.commit();
        drawerLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    public void openJourneyDetailDrawer(int journeyId, int journeyState) {
        JourneyFragment fragment = new JourneyFragment();
        Bundle bundle = new Bundle();
        bundle.putInt("journeyId", journeyId);
        bundle.putInt("state", journeyState);
        fragment.setDrawerFragmentListener(this);
        fragment.setArguments(bundle);
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.drawer_container, fragment);
        transaction.commit();
        drawerLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        setupGoogleApiClient();
        updateMap();
        Log.d(TAG, "Registered location updates listener");
    }

    public void updateMap() {
        resetCamera(false);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void onMapLoaded() {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e(TAG, "Failed to connect");
        Log.e(TAG, connectionResult.getErrorMessage());
        throw new RuntimeException(connectionResult.getErrorMessage());
    }

    @Override
    public void onCloseDrawer() {
        drawerLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        resetCamera(true);
    }

    public void onJourneyButtonClick(View view) {
        openJourneySelectionDrawer();
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        Log.v(TAG, "Location changed");
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return true;
    }

    public void onVideoImageClick(View view) {
        POIDrawerFragment fragment = (POIDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.drawer_container);
        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.putExtra("url", fragment.getPoi().getVideoUrl());
        intent.putExtra("title", fragment.getPoi().getTitle(getLocale()));
        startActivityForResult(intent, VIDEO_PLAYER_RESULT);
    }

    public float getDistanceTo(PointOfInterest poi) {
        if (getLastLocation() == null) return Float.MAX_VALUE;
        return getDistanceBetween(new LatLng(getLastLocation().getLatitude(), getLastLocation().getLongitude()), poi.getLocation());
    }

    public float getDistanceBetween(LatLng loc1, LatLng loc2) {
        float[] results = new float[1];
        Location.distanceBetween(loc1.latitude, loc1.longitude,
                loc2.latitude, loc2.longitude, results);
        return results[0];
    }

    public boolean isCloseTo(PointOfInterest poi) {
        return getDistanceTo(poi) <= Constants.GEOFENCE_RADIUS_IN_METERS;
    }

    protected void highlightClosePois() {
        for (PointOfInterest poi : getPois()) {
            if (isCloseTo(poi)) {
                highlightPoiMarker(poi);
                if (poi == currentDirections) {
                    if (currentPolyline != null) {
                        currentPolyline.remove();
                        currentPolyline = null;
                    }
                    currentDirections = null;
                }
            } else {
                resetPoiMarker(poi);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            onCloseDrawer();
        } else if (currentPolyline != null) {
            currentPolyline.remove();
            currentPolyline = null;
            currentDirections = null;
            resetCamera(true);
        } else {
            finish();
        }
    }

    public void onResetLocationButtonClick(View view) {
        resetCameraOnUser();
    }

    public List<PointOfInterest> getHighlightedPois() {
        return highlightedPois;
    }

    public void onDirectionsTextClick(View view) {
        POIDrawerFragment fragment = (POIDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.drawer_container);
        showDirectionsTo(fragment.getPoi());
        onCloseDrawer();
    }
}

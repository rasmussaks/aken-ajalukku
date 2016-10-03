package com.github.rasmussaks.akenajalukku.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.akexorcist.googledirection.DirectionCallback;
import com.akexorcist.googledirection.GoogleDirection;
import com.akexorcist.googledirection.model.Direction;
import com.akexorcist.googledirection.model.Route;
import com.akexorcist.googledirection.util.DirectionConverter;
import com.github.rasmussaks.akenajalukku.R;
import com.github.rasmussaks.akenajalukku.fragment.POIDrawerFragment;
import com.github.rasmussaks.akenajalukku.model.PointOfInterest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
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
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        DirectionCallback, GoogleMap.OnMarkerClickListener, SlidingUpPanelLayout.PanelSlideListener,
        LocationListener {

    private static final int REQUEST_TO_SETUP_MAP = 1;
    private static final int REQUEST_TO_REGISTER_LISTENER = 2;
    private static String TAG = "aken-ajalukku";
    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private Location lastLocation;
    private PointOfInterest currentPOI;
    private List<PointOfInterest> pois = new ArrayList<>();
    private Polyline currentPolyline;
    private SlidingUpPanelLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar bar = getSupportActionBar();
        if (bar != null) bar.hide();
        setContentView(R.layout.activity_map);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        drawerLayout = (SlidingUpPanelLayout) findViewById(R.id.sliding_layout);
        drawerLayout.addPanelSlideListener(this);
        drawerLayout.setPanelHeight(0);
        drawerLayout.setTouchEnabled(false);
        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setOnMarkerClickListener(this);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_TO_SETUP_MAP);
            return;
        }
        Log.i(TAG, "Map is ready");
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            openSettings();
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_TO_SETUP_MAP:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    setupMap();
                break;
            case REQUEST_TO_REGISTER_LISTENER:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    registerLocationUpdatesListener();
                    updateMap();
                }
                break;
        }
    }

    @SuppressWarnings("MissingPermission")
    private void setupMap() {
        map.setMyLocationEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setMyLocationButtonEnabled(false);

        addPOI(new PointOfInterest(new LatLng(58.3824298, 26.7145573), "Baeri ja Jakobi ristmik", "Päris põnev", "http://i.imgur.com/FGCgIB7.jpg"));
        addPOI(new PointOfInterest(new LatLng(58.380144, 26.7223035), "Raekoja plats", "Raekoda on cool", "http://i.imgur.com/ewugjb2.jpg"));
        addPOI(new PointOfInterest(new LatLng(58.3740385, 26.7071558), "Tartu rongijaam", "Choo choo", "http://i.imgur.com/mRFDWKl.jpg"));
        map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                updateMap();
            }
        });
    }

    private void updateMap() {
        resetCamera(false);
    }

    public void resetCamera(boolean animate) {
        CameraUpdate update = null;
        if (map != null) {
            LatLngBounds.Builder bounds = LatLngBounds.builder();
            for (PointOfInterest poi : pois) {
                bounds.include(poi.getLocation());
            }
            if (lastLocation != null) {
                bounds.include(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()));
            }
            update = CameraUpdateFactory.newLatLngBounds(bounds.build(), 200);

        }
        if (update != null) {
            if (animate) {
                map.animateCamera(update);
            } else {
                map.moveCamera(update);
            }
        }
    }

    public void addPOI(PointOfInterest poi) {
        pois.add(poi);
        poi.setMarker(map.addMarker(new MarkerOptions().position(poi.getLocation())));
    }

    public void setFocusedPOI(PointOfInterest poi) {
        resetPoiMarker(currentPOI);
        currentPOI = poi;
        if (lastLocation != null) {
            GoogleDirection
                    .withServerKey(null)
                    .from(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()))
                    .to(poi.getLocation())
                    .transportMode("walking")
                    .execute(this);
        } else { //No location available at the moment, just focus the marker
            if (currentPOI != null && currentPOI.getMarker() != null) {
                currentPOI.getMarker().remove();
                currentPOI.setMarker(null);
            }
            if (currentPolyline != null) {
                currentPolyline.remove();
            }
            Log.v(TAG, "Setting focused POI");
            highlightPoiMarker(currentPOI);
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(poi.getLocation(), 15.5f));
        }

    }

    public void highlightPoiMarker(PointOfInterest poi) {
        if (poi != null) {
            if (poi.getMarker() != null) poi.getMarker().remove();
            poi.setMarker(map.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)).position(poi.getLocation())));
        }
    }

    public void resetPoiMarker(PointOfInterest poi) {
        if (poi != null) {
            if (poi.getMarker() != null) poi.getMarker().remove();
            poi.setMarker(map.addMarker(new MarkerOptions().position(poi.getLocation())));
        }
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            registerLocationUpdatesListener();
            updateMap();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_TO_REGISTER_LISTENER);
        }
    }

    @SuppressWarnings("MissingPermission")
    private PendingResult<Status> registerLocationUpdatesListener() {
        return LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, createLocationRequest(), this);
    }

    protected LocationRequest createLocationRequest() {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDirectionSuccess(Direction direction, String rawBody) {
        resetPoiMarker(currentPOI);
        if (currentPolyline != null) {
            currentPolyline.remove();
        }
        Log.v(TAG, "Got directions");
        Log.v(TAG, rawBody);
        highlightPoiMarker(currentPOI);
        if (direction.isOK()) {
            Log.v(TAG, direction.getRouteList().toString());
            Route route = direction.getRouteList().get(0);
            ArrayList<LatLng> points = new ArrayList<>(route.getOverviewPolyline().getPointList());
            currentPolyline = map.addPolyline(DirectionConverter.createPolyline(this, points, 5, Color.RED));
            LatLngBounds.Builder bnds = LatLngBounds.builder().include(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()));
            for (LatLng point : points) {
                bnds.include(point);
            }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bnds.build(), 200));
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPOI.getLocation(), 15.5f));
        }
    }

    @Override
    public void onDirectionFailure(Throwable t) {

    }

    public void onSettingsButtonClick(View view) {
        openSettings();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        for (PointOfInterest poi : pois) {
            if (marker.equals(poi.getMarker())) {
                if (currentPOI == poi) {
                    POIDrawerFragment fragment = new POIDrawerFragment();
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("poi", poi);
                    fragment.setArguments(bundle);
                    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                    transaction.replace(R.id.drawer_container, fragment);
                    transaction.commit();
                    drawerLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                } else {
                    setFocusedPOI(poi);
                }
                return true;
            }
        }
        return false;
    }


    @Override
    public void onPanelSlide(View panel, float slideOffset) {

    }

    @Override
    public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {

    }

    public void onCloseDrawer(View view) {
        drawerLayout.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
    }

    @Override
    public void onLocationChanged(Location location) {
        lastLocation = location;
        Log.v(TAG, "Location changed");
    }
}
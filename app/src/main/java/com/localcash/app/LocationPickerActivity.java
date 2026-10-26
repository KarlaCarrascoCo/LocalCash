package com.localcash.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;


import com.google.android.material.button.MaterialButton;
import com.localcash.app.map.MapStyleConfig;
import com.localcash.app.security.ProtectedActivity;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;

import java.util.Locale;

/** Selector de coordenadas manuales. No consulta ni modifica la base de datos. */
public final class LocationPickerActivity extends ProtectedActivity {
    public static final String EXTRA_LATITUDE = "selected_latitude";
    public static final String EXTRA_LONGITUDE = "selected_longitude";
    private static final String TAG = "LocalCashLocationPicker";
    private static final long LOAD_TIMEOUT_MS = 20_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MapView mapView;
    private MapLibreMap map;
    private Marker selectedMarker;
    private Double latitude;
    private Double longitude;
    private TextView coordinates;
    private View loadingContainer;
    private ProgressBar loading;
    private MaterialButton retry;
    private MaterialButton confirm;
    private long startedAt;
    private boolean rendered;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_location_picker);
        coordinates = findViewById(R.id.text_selected_coordinates);
        loadingContainer = findViewById(R.id.layout_picker_loading);
        loading = findViewById(R.id.progress_picker_loading);
        retry = findViewById(R.id.button_retry_picker_map);
        confirm = findViewById(R.id.button_confirm_location);
        latitude = getIntent().hasExtra(EXTRA_LATITUDE)
                ? getIntent().getDoubleExtra(EXTRA_LATITUDE, MapStyleConfig.DEFAULT_LATITUDE) : null;
        longitude = getIntent().hasExtra(EXTRA_LONGITUDE)
                ? getIntent().getDoubleExtra(EXTRA_LONGITUDE, MapStyleConfig.DEFAULT_LONGITUDE) : null;
        confirm.setEnabled(latitude != null && longitude != null);
        actualizarCoordenadas();
        findViewById(R.id.button_cancel_location).setOnClickListener(view -> finish());
        confirm.setOnClickListener(view -> confirmarUbicacion());
        retry.setOnClickListener(view -> solicitarEstilo());
        iniciarMapa(state);
    }

    private void iniciarMapa(Bundle state) {
        startedAt = SystemClock.elapsedRealtime();
        try {
            MapLibre.getInstance(getApplicationContext());
            FrameLayout container = findViewById(R.id.location_picker_map_container);
            mapView = new MapView(this);
            mapView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(mapView, 0);
            mapView.onCreate(state);
            mapView.addOnDidFinishRenderingMapListener(fullyRendered -> {
                if (fullyRendered && !rendered) {
                    rendered = true;
                    handler.removeCallbacks(loadTimeout);
                    loadingContainer.setVisibility(View.GONE);
                    Log.i(TAG, "Mapa de selección visible en " + elapsed() + " ms.");
                }
            });
            mapView.addOnDidFailLoadingMapListener(this::mostrarError);
            mapView.addOnRenderErrorListener(() -> mostrarError(getString(R.string.map_render_error)));
            mapView.getMapAsync(mapLibreMap -> {
                map = mapLibreMap;
                map.getUiSettings().setScrollGesturesEnabled(true);
                map.getUiSettings().setZoomGesturesEnabled(true);
                map.getUiSettings().setDoubleTapGesturesEnabled(true);
                map.getUiSettings().setQuickZoomGesturesEnabled(true);
                map.addOnMapClickListener(point -> {
                    seleccionarPunto(point);
                    return true;
                });
                solicitarEstilo();
            });
        } catch (RuntimeException | LinkageError error) {
            Log.e(TAG, "No se pudo iniciar el selector de ubicación.", error);
            mostrarError(getString(R.string.map_unavailable));
        }
    }

    private void solicitarEstilo() {
        if (map == null) return;
        rendered = false;
        startedAt = SystemClock.elapsedRealtime();
        loadingContainer.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        handler.removeCallbacks(loadTimeout);
        handler.postDelayed(loadTimeout, LOAD_TIMEOUT_MS);
        map.setStyle(new Style.Builder().fromUri(MapStyleConfig.STYLE_URL), style -> {
            LatLng position = latitude == null || longitude == null
                    ? new LatLng(MapStyleConfig.DEFAULT_LATITUDE, MapStyleConfig.DEFAULT_LONGITUDE)
                    : new LatLng(latitude, longitude);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(position,
                    latitude == null ? MapStyleConfig.DEFAULT_ZOOM : 15d));
            if (latitude != null && longitude != null) seleccionarPunto(position);
        });
    }

    private final Runnable loadTimeout = () -> {
        if (!rendered) mostrarError(getString(R.string.map_load_timeout));
    };

    private void mostrarError(String message) {
        handler.removeCallbacks(loadTimeout);
        loadingContainer.setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
        retry.setVisibility(View.VISIBLE);
        coordinates.setText(message);
        Log.w(TAG, "El mapa de selección no estuvo disponible: " + message);
    }

    private void seleccionarPunto(LatLng point) {
        latitude = point.getLatitude();
        longitude = point.getLongitude();
        if (selectedMarker != null) map.removeMarker(selectedMarker);
        selectedMarker = map.addMarker(new MarkerOptions().position(point)
                .title(getString(R.string.location_picker_marker_title)));
        confirm.setEnabled(true);
        actualizarCoordenadas();
    }

    private void actualizarCoordenadas() {
        if (latitude == null || longitude == null) {
            coordinates.setText(R.string.location_picker_instruction);
            return;
        }
        coordinates.setText(getString(R.string.location_saved_format, latitude, longitude));
    }

    private void confirmarUbicacion() {
        if (latitude == null || longitude == null) return;
        Intent result = new Intent();
        result.putExtra(EXTRA_LATITUDE, latitude);
        result.putExtra(EXTRA_LONGITUDE, longitude);
        setResult(RESULT_OK, result);
        finish();
    }

    private long elapsed() { return SystemClock.elapsedRealtime() - startedAt; }

    @Override protected void onStart() {
        super.onStart();
        if (!requireAuthenticatedSession()) return;
        if (mapView != null) mapView.onStart();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!requireAuthenticatedSession()) return;
        if (mapView != null) mapView.onResume();
    }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle outState) { if (mapView != null) mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onDestroy() { handler.removeCallbacks(loadTimeout); if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
}

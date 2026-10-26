package com.localcash.app;

import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.map.MapStyleConfig;
import com.localcash.app.model.Movimiento;
import com.localcash.app.security.ProtectedActivity;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Mapa MapLibre de las coordenadas guardadas; no solicita ubicación del dispositivo. */
public final class MapActivity extends ProtectedActivity {

    private static final String TAG = "LocalCashMap";
    private static final long MAP_LOAD_TIMEOUT_MS = 20_000L;
    private static final int MAX_MOVEMENTS = 100;
    private static final int REQUEST_EXPORT_GEOLOCATED = 71;
    private static final int REQUEST_CENTER_LOCATION = 72;
    private static final double OFFLINE_REGION_HALF_SPAN = 0.015d;
    private static final double OFFLINE_MIN_ZOOM = 10d;
    private static final double OFFLINE_MAX_ZOOM = 14d;

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Movimiento> movimientosConUbicacion = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "CL"));

    private MapView mapView;
    private MapLibreMap map;
    private TextView status;
    private MaterialButton downloadOfflineButton;
    private MaterialButton retryButton;
    private ProgressBar loadingIndicator;
    private View loadingContainer;
    private final Map<Marker, List<Movimiento>> markerGroups = new HashMap<>();
    private final Map<String, Marker> markersByCoordinate = new HashMap<>();
    private long mapLoadStartedAt;
    private boolean firstFrameRendered;
    private boolean firstTileActionLogged;
    private boolean mapAvailable;
    private boolean styleLoaded;
    private boolean markersRendered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_map);

        status = findViewById(R.id.text_map_status);
        downloadOfflineButton = findViewById(R.id.button_download_offline_map);
        retryButton = findViewById(R.id.button_retry_map);
        loadingIndicator = findViewById(R.id.progress_map_loading);
        loadingContainer = findViewById(R.id.layout_map_loading);
        downloadOfflineButton.setOnClickListener(view -> descargarRegionOffline());
        findViewById(R.id.button_export_geolocated).setOnClickListener(view -> solicitarDestinoExportacion());
        findViewById(R.id.button_center_current_location).setOnClickListener(
                view -> centrarEnUbicacionActual());
        retryButton.setOnClickListener(view -> reintentarCargaMapa());
        mostrarCargaMapa();
        iniciarMapa(state);
        cargarMovimientos();
        if (mapAvailable) informarRegionOfflineExistente();
    }

    /**
     * MapView contiene código nativo. Se crea de forma programática para poder conservar la
     * actividad abierta si el dispositivo no dispone de un renderizador compatible.
     */
    private void iniciarMapa(Bundle state) {
        mapLoadStartedAt = SystemClock.elapsedRealtime();
        Log.i(TAG, "Apertura de MapActivity: inicia MapLibre.");
        try {
            MapLibre.getInstance(getApplicationContext());
            FrameLayout container = findViewById(R.id.map_container);
            mapView = new MapView(this);
            mapView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(mapView, 0);
            mapView.onCreate(state);
            instalarDiagnosticosDeCarga();
            mapAvailable = true;
            mapView.getMapAsync(mapLibreMap -> {
                try {
                    map = mapLibreMap;
                    Log.i(TAG, "MapLibre listo en " + tiempoCargaActual() + " ms; se solicita el estilo.");
                    configurarGestos();
                    mapView.addOnDidFailLoadingMapListener(this::mostrarErrorCargaMapa);
                    mapView.addOnRenderErrorListener(
                            () -> mostrarErrorRenderizado());
                    map.setOnMarkerClickListener(this::mostrarGrupoDeMarcadores);
                    solicitarEstilo();
                } catch (RuntimeException | LinkageError error) {
                    Log.e(TAG, "No se pudo inicializar MapLibre.", error);
                    mostrarMapaNoDisponible();
                }
            });
        } catch (RuntimeException | LinkageError error) {
            Log.e(TAG, "No se pudo crear MapView.", error);
            mostrarMapaNoDisponible();
        }
    }

    private void instalarDiagnosticosDeCarga() {
        mapView.addOnWillStartLoadingMapListener(() ->
                Log.i(TAG, "MapLibre comienza a cargar recursos en " + tiempoCargaActual() + " ms."));
        mapView.addOnDidFinishLoadingStyleListener(() ->
                Log.i(TAG, "Estilo cargado en " + tiempoCargaActual() + " ms."));
        mapView.addOnTileActionListener((operation, x, y, z, sourceId, tileSize, url) -> {
            if (!firstTileActionLogged) {
                firstTileActionLogged = true;
                Log.i(TAG, "Primera actividad de tesela (" + operation + ") en "
                        + tiempoCargaActual() + " ms: " + url);
            }
        });
        mapView.addOnDidFinishRenderingMapListener(fullyRendered -> {
            if (fullyRendered && !firstFrameRendered) {
                firstFrameRendered = true;
                mainHandler.removeCallbacks(mapLoadTimeout);
                loadingContainer.setVisibility(View.GONE);
                Log.i(TAG, "Primer mapa renderizado en " + tiempoCargaActual() + " ms.");
            }
        });
    }

    private void solicitarEstilo() {
        if (map == null) {
            return;
        }
        styleLoaded = false;
        markersRendered = false;
        firstFrameRendered = false;
        firstTileActionLogged = false;
        markerGroups.clear();
        markersByCoordinate.clear();
        mapLoadStartedAt = SystemClock.elapsedRealtime();
        mostrarCargaMapa();
        mainHandler.removeCallbacks(mapLoadTimeout);
        mainHandler.postDelayed(mapLoadTimeout, MAP_LOAD_TIMEOUT_MS);
        Log.i(TAG, "Solicitando estilo " + MapStyleConfig.STYLE_URL);
        map.setStyle(new Style.Builder().fromUri(MapStyleConfig.STYLE_URL), style -> {
                        styleLoaded = true;
                        Log.i(TAG, "Callback de estilo recibido en " + tiempoCargaActual() + " ms.");
                        renderizarMovimientos();
        });
    }

    private final Runnable mapLoadTimeout = () -> {
        if (!firstFrameRendered) {
            Log.w(TAG, "MapLibre superó " + MAP_LOAD_TIMEOUT_MS + " ms sin un mapa visible.");
            loadingContainer.setVisibility(View.VISIBLE);
            loadingIndicator.setVisibility(View.GONE);
            retryButton.setVisibility(View.VISIBLE);
            status.setText(R.string.map_load_timeout);
        }
    };

    private long tiempoCargaActual() {
        return SystemClock.elapsedRealtime() - mapLoadStartedAt;
    }

    private void mostrarCargaMapa() {
        loadingContainer.setVisibility(View.VISIBLE);
        loadingIndicator.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        status.setText(R.string.map_loading);
    }

    private void reintentarCargaMapa() {
        if (!mapAvailable || map == null) {
            iniciarMapa(null);
            return;
        }
        solicitarEstilo();
    }

    private void configurarGestos() {
        map.getUiSettings().setScrollGesturesEnabled(true);
        map.getUiSettings().setZoomGesturesEnabled(true);
        map.getUiSettings().setDoubleTapGesturesEnabled(true);
        map.getUiSettings().setQuickZoomGesturesEnabled(true);
        map.getUiSettings().setRotateGesturesEnabled(true);
    }

    private void mostrarErrorCargaMapa(String error) {
        styleLoaded = false;
        mainHandler.removeCallbacks(mapLoadTimeout);
        downloadOfflineButton.setEnabled(false);
        loadingContainer.setVisibility(View.VISIBLE);
        loadingIndicator.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
        Log.e(TAG, "MapLibre informó un error de carga en " + tiempoCargaActual() + " ms: " + error);
        status.setText(getString(R.string.map_load_error_detail, error));
    }

    private void mostrarErrorRenderizado() {
        mainHandler.removeCallbacks(mapLoadTimeout);
        loadingContainer.setVisibility(View.VISIBLE);
        loadingIndicator.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
        Log.e(TAG, "MapLibre informó un error de renderizado en " + tiempoCargaActual() + " ms.");
        status.setText(R.string.map_render_error);
    }

    private void mostrarMapaNoDisponible() {
        mapAvailable = false;
        mainHandler.removeCallbacks(mapLoadTimeout);
        downloadOfflineButton.setEnabled(false);
        loadingContainer.setVisibility(View.VISIBLE);
        loadingIndicator.setVisibility(View.GONE);
        retryButton.setVisibility(View.VISIBLE);
        status.setText(R.string.map_unavailable);
    }

    private void cargarMovimientos() {
        databaseExecutor.execute(() -> {
            List<Movimiento> encontrados = new ArrayList<>();
            try {
                for (Movimiento movimiento : new MovimientoRepository(this).obtenerRecientes(MAX_MOVEMENTS)) {
                    if (movimiento.getLatitud() != null && movimiento.getLongitud() != null) {
                        encontrados.add(movimiento);
                    }
                }
            } catch (RuntimeException exception) {
                mainHandler.post(() -> status.setText(R.string.error_database_operation));
                return;
            }
            mainHandler.post(() -> {
                movimientosConUbicacion.clear();
                movimientosConUbicacion.addAll(encontrados);
                downloadOfflineButton.setEnabled(mapAvailable && !encontrados.isEmpty());
                if (mapAvailable && encontrados.isEmpty()) {
                    status.setText(R.string.map_empty);
                }
                renderizarMovimientos();
            });
        });
    }

    private void renderizarMovimientos() {
        if (!mapAvailable || map == null || !styleLoaded || movimientosConUbicacion.isEmpty() || markersRendered) {
            return;
        }
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int markers = 0;
        LatLng uniquePosition = null;
        try {
            for (Movimiento movimiento : movimientosConUbicacion) {
                if (!esCoordenadaValida(movimiento)) continue;
                LatLng position = new LatLng(movimiento.getLatitud(), movimiento.getLongitud());
                bounds.include(position);
                uniquePosition = position;
                String coordinateKey = movimiento.getLatitud() + "," + movimiento.getLongitud();
                Marker marker = markersByCoordinate.get(coordinateKey);
                if (marker == null) {
                    marker = map.addMarker(new MarkerOptions()
                            .position(position)
                            .title(movimiento.getDescripcion().isEmpty()
                                    ? getString(R.string.map_default_marker_title) : movimiento.getDescripcion())
                            .snippet(formatearDetalleMarcador(movimiento)));
                    markersByCoordinate.put(coordinateKey, marker);
                    markerGroups.put(marker, new ArrayList<>());
                    markers++;
                }
                List<Movimiento> group = markerGroups.get(marker);
                group.add(movimiento);
            }
            if (markers == 0) {
                status.setText(R.string.map_empty);
                return;
            }
            // MapLibre no permite construir LatLngBounds con un único punto. Ese caso
            // ocurría al tener un solo movimiento geolocalizado y abortaba el proceso nativo.
            if (markers == 1 && uniquePosition != null) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(uniquePosition, 15d));
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 96));
            }
            markersRendered = true;
            status.setText(R.string.map_online_ready);
        } catch (RuntimeException | LinkageError error) {
            mostrarMapaNoDisponible();
        }
    }

    private static boolean esCoordenadaValida(Movimiento movimiento) {
        if (movimiento.getLatitud() == null || movimiento.getLongitud() == null) return false;
        double latitud = movimiento.getLatitud();
        double longitud = movimiento.getLongitud();
        return !Double.isNaN(latitud) && !Double.isNaN(longitud)
                && !Double.isInfinite(latitud) && !Double.isInfinite(longitud)
                && latitud >= -90d && latitud <= 90d && longitud >= -180d && longitud <= 180d;
    }

    private String formatearDetalleMarcador(Movimiento movimiento) {
        String cuenta = movimiento.getNombreCuenta() == null ? "" : movimiento.getNombreCuenta();
        String categoria = Movimiento.TIPO_GASTO.equals(movimiento.getTipo())
                ? movimiento.getCategoria() : getString(R.string.income_internal_category);
        return movimiento.getTipo() + " · " + currencyFormat.format(movimiento.getMonto())
                + " · " + categoria + "\n" + dateFormat.format(new Date(movimiento.getFecha()))
                + (cuenta.isEmpty() ? "" : " · " + cuenta);
    }

    private boolean mostrarGrupoDeMarcadores(Marker marker) {
        List<Movimiento> group = markerGroups.get(marker);
        if (group == null || group.size() < 2) {
            return false;
        }
        String[] labels = new String[group.size()];
        for (int index = 0; index < group.size(); index++) {
            Movimiento movimiento = group.get(index);
            labels[index] = movimiento.getDescripcion() + " · "
                    + currencyFormat.format(movimiento.getMonto());
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.map_shared_location_title)
                .setItems(labels, (dialog, which) -> map.selectMarker(marker))
                .show();
        return true;
    }

    private void descargarRegionOffline() {
        if (!mapAvailable) {
            status.setText(R.string.map_unavailable);
            return;
        }
        if (movimientosConUbicacion.isEmpty()) {
            status.setText(R.string.map_download_no_location);
            return;
        }
        if (!hayConexion()) {
            status.setText(R.string.map_download_requires_connection);
            return;
        }
        Movimiento referencia = movimientosConUbicacion.get(0);
        LatLngBounds bounds = new LatLngBounds.Builder()
                .include(new LatLng(referencia.getLatitud() - OFFLINE_REGION_HALF_SPAN,
                        referencia.getLongitud() - OFFLINE_REGION_HALF_SPAN))
                .include(new LatLng(referencia.getLatitud() + OFFLINE_REGION_HALF_SPAN,
                        referencia.getLongitud() + OFFLINE_REGION_HALF_SPAN))
                .build();
        OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(
                MapStyleConfig.STYLE_URL, bounds, OFFLINE_MIN_ZOOM, OFFLINE_MAX_ZOOM,
                getResources().getDisplayMetrics().density);
        downloadOfflineButton.setEnabled(false);
        status.setText(R.string.map_download_started);
        OfflineManager.getInstance(getApplicationContext()).createOfflineRegion(definition,
                "localcash-region".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new OfflineManager.CreateOfflineRegionCallback() {
                    @Override public void onCreate(OfflineRegion region) {
                        observarDescarga(region);
                        region.setDownloadState(OfflineRegion.STATE_ACTIVE);
                    }
                    @Override public void onError(String error) {
                        downloadOfflineButton.setEnabled(true);
                        status.setText(getString(R.string.map_download_error, error));
                    }
                });
    }

    private void observarDescarga(OfflineRegion region) {
        region.setObserver(new OfflineRegion.OfflineRegionObserver() {
            @Override public void onStatusChanged(OfflineRegionStatus offlineStatus) {
                long total = Math.max(offlineStatus.getRequiredResourceCount(), 1L);
                status.setText(getString(R.string.map_download_progress,
                        offlineStatus.getCompletedResourceCount(), total));
                if (offlineStatus.isComplete()) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE);
                    downloadOfflineButton.setEnabled(true);
                    status.setText(R.string.map_download_complete);
                }
            }
            @Override public void onError(OfflineRegionError error) {
                downloadOfflineButton.setEnabled(true);
                status.setText(getString(R.string.map_download_error, error.getMessage()));
            }
            @Override public void mapboxTileCountLimitExceeded(long limit) {
                downloadOfflineButton.setEnabled(true);
                status.setText(R.string.map_tile_limit_error);
            }
        });
    }

    private void informarRegionOfflineExistente() {
        try {
            OfflineManager.getInstance(getApplicationContext()).listOfflineRegions(
                new OfflineManager.ListOfflineRegionsCallback() {
                    @Override public void onList(OfflineRegion[] regions) {
                        if (regions.length > 0 && movimientosConUbicacion.isEmpty()) {
                            status.setText(R.string.map_offline_available);
                        }
                    }
                    @Override public void onError(String error) { }
                });
        } catch (RuntimeException | LinkageError error) {
            mostrarMapaNoDisponible();
        }
    }

    private boolean hayConexion() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** Centra la cámara sin cambiar las coordenadas persistidas de ningún movimiento. */
    private void centrarEnUbicacionActual() {
        if (!mapAvailable || map == null) {
            status.setText(R.string.map_unavailable);
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CENTER_LOCATION);
            status.setText(R.string.location_permission_requesting);
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            status.setText(R.string.location_services_disabled);
            return;
        }
        try {
            Location location = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
            if (location == null && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (location == null) {
                status.setText(R.string.location_unavailable);
                return;
            }
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), 15d));
            status.setText(R.string.map_centered_current_location);
        } catch (SecurityException exception) {
            status.setText(R.string.location_permission_required);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CENTER_LOCATION) {
            boolean granted = false;
            for (int result : grantResults) {
                granted |= result == PackageManager.PERMISSION_GRANTED;
            }
            if (granted) {
                centrarEnUbicacionActual();
            } else {
                status.setText(R.string.location_permission_denied);
            }
        }
    }

    private void solicitarDestinoExportacion() {
        if (movimientosConUbicacion.isEmpty()) {
            status.setText(R.string.map_export_empty);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "localcash_movimientos_geolocalizados.csv");
        startActivityForResult(intent, REQUEST_EXPORT_GEOLOCATED);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT_GEOLOCATED && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            exportarCsv(data.getData());
        }
    }

    private void exportarCsv(Uri destino) {
        databaseExecutor.execute(() -> {
            try (OutputStream output = getContentResolver().openOutputStream(destino, "w")) {
                if (output == null) throw new java.io.IOException("Destino no disponible");
                StringBuilder csv = new StringBuilder("fecha,tipo,descripcion,monto,cuenta,categoria,latitud,longitud\n");
                for (Movimiento movimiento : new MovimientoRepository(this).obtenerHistorial(-1L, null)) {
                    if (movimiento.getLatitud() != null && movimiento.getLongitud() != null) {
                        csv.append(csv(movimiento.getFecha())).append(',').append(csv(movimiento.getTipo())).append(',')
                                .append(csv(movimiento.getDescripcion())).append(',').append(movimiento.getMonto()).append(',')
                                .append(csv(movimiento.getNombreCuenta())).append(',').append(csv(movimiento.getCategoria())).append(',')
                                .append(movimiento.getLatitud()).append(',').append(movimiento.getLongitud()).append('\n');
                    }
                }
                output.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                mainHandler.post(() -> status.setText(R.string.map_export_success));
            } catch (Exception error) {
                mainHandler.post(() -> status.setText(R.string.map_export_error));
            }
        });
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }

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
    @Override protected void onDestroy() { mainHandler.removeCallbacks(mapLoadTimeout); databaseExecutor.shutdownNow(); if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
}

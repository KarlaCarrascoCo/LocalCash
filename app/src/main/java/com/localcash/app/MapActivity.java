package com.localcash.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.model.Movimiento;

import org.maplibre.android.MapLibre;
import org.maplibre.android.annotations.MarkerOptions;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Mapa MapLibre de las coordenadas guardadas; no solicita ubicación del dispositivo. */
public final class MapActivity extends AppCompatActivity {

    private static final String MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json";
    private static final int MAX_MOVEMENTS = 100;
    private static final int REQUEST_EXPORT_GEOLOCATED = 71;
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
    private boolean mapAvailable;
    private boolean styleLoaded;
    private boolean markersRendered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_map);

        status = findViewById(R.id.text_map_status);
        downloadOfflineButton = findViewById(R.id.button_download_offline_map);
        downloadOfflineButton.setOnClickListener(view -> descargarRegionOffline());
        findViewById(R.id.button_export_geolocated).setOnClickListener(view -> solicitarDestinoExportacion());
        iniciarMapa(state);
        cargarMovimientos();
        if (mapAvailable) informarRegionOfflineExistente();
    }

    /**
     * MapView contiene código nativo. Se crea de forma programática para poder conservar la
     * actividad abierta si el dispositivo no dispone de un renderizador compatible.
     */
    private void iniciarMapa(Bundle state) {
        try {
            MapLibre.getInstance(getApplicationContext());
            FrameLayout container = findViewById(R.id.map_container);
            mapView = new MapView(this);
            mapView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            container.addView(mapView);
            mapView.onCreate(state);
            mapAvailable = true;
            mapView.getMapAsync(mapLibreMap -> {
                try {
                    map = mapLibreMap;
                    mapView.addOnDidFailLoadingMapListener(errorMessage -> status.setText(R.string.map_load_error));
                    map.setStyle(new Style.Builder().fromUri(MAP_STYLE_URL), style -> {
                        styleLoaded = true;
                        renderizarMovimientos();
                    });
                } catch (RuntimeException | LinkageError error) {
                    mostrarMapaNoDisponible();
                }
            });
        } catch (RuntimeException | LinkageError error) {
            mostrarMapaNoDisponible();
        }
    }

    private void mostrarMapaNoDisponible() {
        mapAvailable = false;
        downloadOfflineButton.setEnabled(false);
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
        try {
            for (Movimiento movimiento : movimientosConUbicacion) {
                if (!esCoordenadaValida(movimiento)) continue;
                LatLng position = new LatLng(movimiento.getLatitud(), movimiento.getLongitud());
                bounds.include(position);
                map.addMarker(new MarkerOptions()
                        .position(position)
                        .title(movimiento.getDescripcion().isEmpty()
                                ? getString(R.string.map_default_marker_title) : movimiento.getDescripcion())
                        .snippet(formatearDetalleMarcador(movimiento)));
                markers++;
            }
            if (markers == 0) {
                status.setText(R.string.map_empty);
                return;
            }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 96));
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
        return movimiento.getTipo() + " · " + currencyFormat.format(movimiento.getMonto())
                + "\n" + dateFormat.format(new Date(movimiento.getFecha()))
                + (cuenta.isEmpty() ? "" : " · " + cuenta);
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
                MAP_STYLE_URL, bounds, OFFLINE_MIN_ZOOM, OFFLINE_MAX_ZOOM,
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

    @Override protected void onStart() { super.onStart(); if (mapView != null) mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); if (mapView != null) mapView.onResume(); }
    @Override protected void onPause() { if (mapView != null) mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { if (mapView != null) mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapView != null) mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle outState) { if (mapView != null) mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onDestroy() { databaseExecutor.shutdownNow(); if (mapView != null) mapView.onDestroy(); super.onDestroy(); }
}

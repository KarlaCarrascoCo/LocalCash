package com.localcash.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

/** Mapa MapLibre de las coordenadas guardadas; no solicita ubicación del dispositivo. */
public final class MapActivity extends AppCompatActivity {

    private static final String MAP_STYLE_URL = "https://demotiles.maplibre.org/style.json";
    private static final int MAX_MOVEMENTS = 100;
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
    private boolean styleLoaded;
    private boolean markersRendered;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // Debe inicializarse antes de inflar MapView.
        MapLibre.getInstance(getApplicationContext());
        setContentView(R.layout.activity_map);

        mapView = findViewById(R.id.map_view);
        status = findViewById(R.id.text_map_status);
        downloadOfflineButton = findViewById(R.id.button_download_offline_map);
        mapView.onCreate(state);
        downloadOfflineButton.setOnClickListener(view -> descargarRegionOffline());

        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            mapView.addOnDidFailLoadingMapListener(errorMessage -> status.setText(R.string.map_load_error));
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE_URL), style -> {
                styleLoaded = true;
                renderizarMovimientos();
            });
        });
        cargarMovimientos();
        informarRegionOfflineExistente();
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
                downloadOfflineButton.setEnabled(!encontrados.isEmpty());
                if (encontrados.isEmpty()) {
                    status.setText(R.string.map_empty);
                }
                renderizarMovimientos();
            });
        });
    }

    private void renderizarMovimientos() {
        if (map == null || !styleLoaded || movimientosConUbicacion.isEmpty() || markersRendered) {
            return;
        }
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Movimiento movimiento : movimientosConUbicacion) {
            LatLng position = new LatLng(movimiento.getLatitud(), movimiento.getLongitud());
            bounds.include(position);
            map.addMarker(new MarkerOptions()
                    .position(position)
                    .title(movimiento.getDescripcion().isEmpty()
                            ? getString(R.string.map_default_marker_title) : movimiento.getDescripcion())
                    .snippet(formatearDetalleMarcador(movimiento)));
        }
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 96));
        markersRendered = true;
        status.setText(R.string.map_online_ready);
    }

    private String formatearDetalleMarcador(Movimiento movimiento) {
        String cuenta = movimiento.getNombreCuenta() == null ? "" : movimiento.getNombreCuenta();
        return movimiento.getTipo() + " · " + currencyFormat.format(movimiento.getMonto())
                + "\n" + dateFormat.format(new Date(movimiento.getFecha()))
                + (cuenta.isEmpty() ? "" : " · " + cuenta);
    }

    private void descargarRegionOffline() {
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
        OfflineManager.getInstance(getApplicationContext()).listOfflineRegions(
                new OfflineManager.ListOfflineRegionsCallback() {
                    @Override public void onList(OfflineRegion[] regions) {
                        if (regions.length > 0 && movimientosConUbicacion.isEmpty()) {
                            status.setText(R.string.map_offline_available);
                        }
                    }
                    @Override public void onError(String error) { }
                });
    }

    private boolean hayConexion() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network network = manager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onSaveInstanceState(Bundle outState) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState); }
    @Override protected void onDestroy() { databaseExecutor.shutdownNow(); mapView.onDestroy(); super.onDestroy(); }
}

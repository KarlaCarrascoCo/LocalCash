package com.localcash.app;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.model.Movimiento;
import com.localcash.app.ui.LocationMapView;

import java.util.ArrayList;
import java.util.List;

/** Visualización interactiva local de ubicaciones guardadas opcionalmente. */
public final class MapActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_map);
        List<Movimiento> ubicados = new ArrayList<>();
        for (Movimiento movimiento : new MovimientoRepository(this).obtenerRecientes(100)) if (movimiento.getLatitud() != null) ubicados.add(movimiento);
        ((LocationMapView) findViewById(R.id.location_map)).setMovimientos(ubicados);
        if (ubicados.isEmpty()) ((TextView) findViewById(R.id.text_map_status)).setText(R.string.map_empty);
    }
}

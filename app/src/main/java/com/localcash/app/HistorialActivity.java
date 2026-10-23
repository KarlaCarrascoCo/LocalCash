package com.localcash.app;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.localcash.app.data.CuentaRepository;
import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.model.Cuenta;
import com.localcash.app.model.Movimiento;
import com.localcash.app.security.AuthSession;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Consulta todo el historial local sin el límite del panel de inicio. */
public final class HistorialActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
    private final DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "CL"));
    private final List<Cuenta> cuentas = new ArrayList<>();
    private long cuentaSeleccionada = Cuenta.ID_TODAS_LAS_CUENTAS;
    private String tipoSeleccionado;
    private LinearLayout lista;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AuthSession.isAuthenticated()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_historial);
        lista = findViewById(R.id.list_history);
        MaterialAutoCompleteTextView filtroCuenta = findViewById(R.id.input_history_account);
        MaterialAutoCompleteTextView filtroTipo = findViewById(R.id.input_history_type);
        configurarFiltroTipos(filtroTipo);
        executor.execute(() -> {
            List<Cuenta> leidas = new CuentaRepository(this).obtenerTodas();
            runOnUiThread(() -> configurarFiltroCuentas(filtroCuenta, leidas));
        });
    }

    private void configurarFiltroTipos(MaterialAutoCompleteTextView input) {
        String[] etiquetas = getResources().getStringArray(R.array.history_types);
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, etiquetas));
        input.setText(etiquetas[0], false);
        input.setOnItemClickListener((parent, view, position, id) -> {
            tipoSeleccionado = position == 1 ? Movimiento.TIPO_INGRESO
                    : position == 2 ? Movimiento.TIPO_GASTO : null;
            cargar();
        });
    }

    private void configurarFiltroCuentas(MaterialAutoCompleteTextView input, List<Cuenta> leidas) {
        cuentas.clear(); cuentas.addAll(leidas);
        List<String> etiquetas = new ArrayList<>();
        etiquetas.add(getString(R.string.account_filter_all));
        for (Cuenta cuenta : cuentas) etiquetas.add(cuenta.getNombre());
        input.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, etiquetas));
        input.setText(etiquetas.get(0), false);
        input.setOnItemClickListener((parent, view, position, id) -> {
            cuentaSeleccionada = position == 0 ? Cuenta.ID_TODAS_LAS_CUENTAS : cuentas.get(position - 1).getId();
            cargar();
        });
        cargar();
    }

    private void cargar() {
        long cuenta = cuentaSeleccionada;
        String tipo = tipoSeleccionado;
        executor.execute(() -> {
            List<Movimiento> movimientos = new MovimientoRepository(this).obtenerHistorial(cuenta, tipo);
            runOnUiThread(() -> mostrar(movimientos));
        });
    }

    private void mostrar(List<Movimiento> movimientos) {
        lista.removeAllViews();
        if (movimientos.isEmpty()) {
            TextView vacio = new TextView(this); vacio.setText(R.string.history_empty);
            vacio.setTextColor(getColor(R.color.lc_text_secondary)); lista.addView(vacio); return;
        }
        for (Movimiento movimiento : movimientos) {
            TextView fila = new TextView(this);
            fila.setPadding(0, 18, 0, 18);
            fila.setTextColor(getColor(R.color.lc_text_primary));
            String categoria = Movimiento.TIPO_GASTO.equals(movimiento.getTipo())
                    ? " · " + movimiento.getCategoria() : "";
            fila.setText(getString(R.string.history_item_format, movimiento.getDescripcion(),
                    movimiento.getTipo(), currency.format(movimiento.getMonto()),
                    date.format(movimiento.getFecha()), movimiento.getNombreCuenta(), categoria));
            lista.addView(fila);
        }
    }

    @Override protected void onDestroy() { executor.shutdown(); super.onDestroy(); }
}

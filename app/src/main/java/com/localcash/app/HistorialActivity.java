package com.localcash.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.model.Cuenta;
import com.localcash.app.model.Movimiento;
import com.localcash.app.security.AuthSession;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Consulta el historial completo almacenado localmente, sin límite de movimientos. */
public final class HistorialActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
    private final DateFormat date = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "CL"));
    private String tipoSeleccionado;
    private LinearLayout lista;
    private MaterialButton allButton;
    private MaterialButton incomeButton;
    private MaterialButton expenseButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AuthSession.isAuthenticated()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_historial);
        lista = findViewById(R.id.list_history);
        allButton = findViewById(R.id.button_history_filter_all);
        incomeButton = findViewById(R.id.button_history_filter_income);
        expenseButton = findViewById(R.id.button_history_filter_expense);
        findViewById(R.id.button_history_back).setOnClickListener(view -> finish());
        configurarNavegacion();
        configurarFiltros();
        cargar();
    }

    private void configurarNavegacion() {
        findViewById(R.id.button_history_nav_home).setOnClickListener(view -> abrir(MainActivity.class));
        findViewById(R.id.button_history_nav_history).setOnClickListener(view -> { });
        findViewById(R.id.button_history_nav_shopping).setOnClickListener(view -> abrir(ComprasActivity.class));
        findViewById(R.id.button_history_nav_more).setOnClickListener(view -> abrir(MasActivity.class));
    }

    private void abrir(Class<?> destination) {
        startActivity(new Intent(this, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void configurarFiltros() {
        allButton.setOnClickListener(view -> seleccionarTipo(null));
        incomeButton.setOnClickListener(view -> seleccionarTipo(Movimiento.TIPO_INGRESO));
        expenseButton.setOnClickListener(view -> seleccionarTipo(Movimiento.TIPO_GASTO));
        actualizarFiltroVisual();
    }

    private void seleccionarTipo(String tipo) {
        if ((tipo == null && tipoSeleccionado == null)
                || (tipo != null && tipo.equals(tipoSeleccionado))) {
            return;
        }
        tipoSeleccionado = tipo;
        actualizarFiltroVisual();
        cargar();
    }

    private void actualizarFiltroVisual() {
        allButton.setChecked(tipoSeleccionado == null);
        incomeButton.setChecked(Movimiento.TIPO_INGRESO.equals(tipoSeleccionado));
        expenseButton.setChecked(Movimiento.TIPO_GASTO.equals(tipoSeleccionado));
    }

    private void cargar() {
        final String tipo = tipoSeleccionado;
        executor.execute(() -> {
            List<Movimiento> movimientos = new MovimientoRepository(this)
                    .obtenerHistorial(Cuenta.ID_TODAS_LAS_CUENTAS, tipo);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && coincideConFiltroActual(tipo)) {
                    mostrar(movimientos);
                }
            });
        });
    }

    private boolean coincideConFiltroActual(String tipo) {
        return tipo == null ? tipoSeleccionado == null : tipo.equals(tipoSeleccionado);
    }

    private void mostrar(List<Movimiento> movimientos) {
        lista.removeAllViews();
        if (movimientos.isEmpty()) {
            TextView vacio = new TextView(this);
            vacio.setPadding(0, 24, 0, 24);
            vacio.setText(R.string.history_empty);
            vacio.setTextColor(getColor(R.color.lc_text_secondary));
            lista.addView(vacio);
            return;
        }
        LayoutInflater inflater = getLayoutInflater();
        for (Movimiento movimiento : movimientos) {
            View fila = inflater.inflate(R.layout.item_movimiento, lista, false);
            vincularMovimiento(fila, movimiento);
            fila.setOnClickListener(view -> mostrarDetalle(movimiento));
            lista.addView(fila);
        }
    }

    private void vincularMovimiento(View fila, Movimiento movimiento) {
        boolean ingreso = Movimiento.TIPO_INGRESO.equals(movimiento.getTipo());
        ((TextView) fila.findViewById(R.id.text_movement_title)).setText(movimiento.getDescripcion());
        ((TextView) fila.findViewById(R.id.text_movement_detail)).setText(
                getString(R.string.history_movement_detail_format,
                        ingreso ? getString(R.string.form_income) : movimiento.getCategoria(),
                        date.format(movimiento.getFecha()), movimiento.getNombreCuenta()));
        TextView location = fila.findViewById(R.id.text_movement_location);
        if (movimiento.getDireccionAproximada() == null || movimiento.getDireccionAproximada().trim().isEmpty()) {
            location.setVisibility(View.GONE);
        } else {
            location.setText(getString(R.string.location_approximate_address_format,
                    movimiento.getDireccionAproximada()));
            location.setVisibility(View.VISIBLE);
        }
        ImageView icon = fila.findViewById(R.id.image_movement_icon);
        icon.setImageResource(ingreso ? R.drawable.ic_wallet : R.drawable.ic_arrow_downward);
        icon.setBackgroundResource(ingreso ? R.drawable.bg_income_icon : R.drawable.bg_expense_icon);
        icon.setImageTintList(getColorStateList(ingreso ? R.color.lc_income : R.color.lc_expense));
        TextView amount = fila.findViewById(R.id.text_movement_amount);
        amount.setText(getString(ingreso ? R.string.amount_income_format : R.string.amount_expense_format,
                currency.format(movimiento.getMonto())));
        amount.setTextColor(getColor(ingreso ? R.color.lc_income : R.color.lc_expense));
    }

    private void mostrarDetalle(Movimiento movimiento) {
        boolean ingreso = Movimiento.TIPO_INGRESO.equals(movimiento.getTipo());
        String categoria = ingreso ? getString(R.string.income_internal_category) : movimiento.getCategoria();
        String ubicacion = movimiento.getDireccionAproximada() == null
                || movimiento.getDireccionAproximada().trim().isEmpty()
                ? getString(R.string.location_optional) : movimiento.getDireccionAproximada();
        new AlertDialog.Builder(this)
                .setTitle(movimiento.getDescripcion())
                .setMessage(getString(R.string.history_detail_format,
                        ingreso ? getString(R.string.form_income) : getString(R.string.form_expense),
                        currency.format(movimiento.getMonto()), categoria,
                        date.format(movimiento.getFecha()), movimiento.getNombreCuenta(), ubicacion))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override protected void onDestroy() { executor.shutdown(); super.onDestroy(); }
}

package com.localcash.app;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import com.localcash.app.data.GastoCompartidoRepository;
import com.localcash.app.model.GastoCompartido;
import com.localcash.app.security.ProtectedActivity;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Muestra una compra grupal y permite actualizar cada reembolso de forma persistente. */
public final class DeudaGrupoActivity extends ProtectedActivity {
    public static final String EXTRA_DESCRIPCION = "descripcion";
    public static final String EXTRA_MONTO_TOTAL = "monto_total";
    public static final String EXTRA_PARTICIPANTES = "participantes";

    private GastoCompartidoRepository repository;
    private String descripcion;
    private long montoTotal;
    private int participantes;
    private LinearLayout lista;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_deuda_grupo);
        descripcion = getIntent().getStringExtra(EXTRA_DESCRIPCION);
        montoTotal = getIntent().getLongExtra(EXTRA_MONTO_TOTAL, 0L);
        participantes = getIntent().getIntExtra(EXTRA_PARTICIPANTES, 0);
        repository = new GastoCompartidoRepository(this); lista = findViewById(R.id.list_group_debts);
        ((TextView) findViewById(R.id.text_group_description)).setText(descripcion);
        ((TextView) findViewById(R.id.text_group_total)).setText(NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(montoTotal));
        findViewById(R.id.button_back_group).setOnClickListener(v -> finish()); cargar();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!requireAuthenticatedSession()) return;
        if (repository != null) cargar();
    }

    private void cargar() {
        lista.removeAllViews();
        List<GastoCompartido> deudas = repository.obtenerPorGrupo(descripcion, montoTotal, participantes);
        for (GastoCompartido deuda : deudas) {
            MaterialCardView tarjeta = new MaterialCardView(this);
            tarjeta.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface));
            tarjeta.setRadius(dp(16)); tarjeta.setStrokeWidth(dp(1));
            tarjeta.setStrokeColor(ContextCompat.getColor(this, deuda.isPagado() ? R.color.lc_income : R.color.lc_sage_outline));
            tarjeta.setCardElevation(0f);
            LinearLayout contenido = new LinearLayout(this); contenido.setOrientation(LinearLayout.VERTICAL);
            contenido.setPadding(dp(14), dp(10), dp(14), dp(4));
            TextView participante = new TextView(this);
            participante.setText(deuda.getContacto()); participante.setTextColor(getColor(R.color.lc_text_primary));
            participante.setTextSize(16); participante.setTypeface(participante.getTypeface(), android.graphics.Typeface.BOLD);
            contenido.addView(participante);
            TextView monto = new TextView(this);
            monto.setText(getString(R.string.shared_participant_card_detail,
                    NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(deuda.getMontoDeuda()),
                    getString(deuda.isPagado() ? R.string.shared_status_paid : R.string.shared_status_pending)));
            monto.setTextColor(getColor(R.color.lc_text_secondary)); contenido.addView(monto);
            CheckBox fila = new CheckBox(this);
            fila.setPadding(0, 0, 0, 0); fila.setTextColor(getColor(R.color.lc_text_primary));
            fila.setText(R.string.shared_mark_paid);
            fila.setChecked(deuda.isPagado());
            fila.setOnCheckedChangeListener((button, pagado) -> {
                repository.marcarPagado(deuda, pagado);
                cargar();
            });
            contenido.addView(fila); tarjeta.addView(contenido);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT); params.topMargin = dp(8); lista.addView(tarjeta, params);
        }
    }

    private int dp(int valor) { return (int) (valor * getResources().getDisplayMetrics().density); }
}

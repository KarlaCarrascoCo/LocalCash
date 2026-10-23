package com.localcash.app;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.localcash.app.data.GastoCompartidoRepository;
import com.localcash.app.model.GastoCompartido;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Muestra una compra grupal y permite actualizar cada reembolso de forma persistente. */
public final class DeudaGrupoActivity extends AppCompatActivity {
    public static final String EXTRA_DESCRIPCION = "descripcion";
    public static final String EXTRA_MONTO_TOTAL = "monto_total";
    public static final String EXTRA_PARTICIPANTES = "participantes";

    private GastoCompartidoRepository repository;
    private String descripcion;
    private long montoTotal;
    private int participantes;
    private LinearLayout lista;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_deuda_grupo);
        descripcion = getIntent().getStringExtra(EXTRA_DESCRIPCION);
        montoTotal = getIntent().getLongExtra(EXTRA_MONTO_TOTAL, 0L);
        participantes = getIntent().getIntExtra(EXTRA_PARTICIPANTES, 0);
        repository = new GastoCompartidoRepository(this); lista = findViewById(R.id.list_group_debts);
        ((TextView) findViewById(R.id.text_group_description)).setText(descripcion);
        ((TextView) findViewById(R.id.text_group_total)).setText(NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(montoTotal));
        findViewById(R.id.button_back_group).setOnClickListener(v -> finish()); cargar();
    }

    @Override protected void onResume() { super.onResume(); if (repository != null) cargar(); }

    private void cargar() {
        lista.removeAllViews();
        List<GastoCompartido> deudas = repository.obtenerPorGrupo(descripcion, montoTotal, participantes);
        for (GastoCompartido deuda : deudas) {
            CheckBox fila = new CheckBox(this);
            fila.setPadding(0, 16, 0, 16); fila.setTextColor(getColor(R.color.lc_text_primary));
            fila.setText(getString(R.string.shared_participant_debt_format, deuda.getContacto(),
                    NumberFormat.getCurrencyInstance(new Locale("es", "CL")).format(deuda.getMontoDeuda()),
                    getString(deuda.isPagado() ? R.string.shared_status_paid : R.string.shared_status_pending)));
            fila.setChecked(deuda.isPagado());
            fila.setOnCheckedChangeListener((button, pagado) -> {
                repository.marcarPagado(deuda, pagado);
                cargar();
            });
            lista.addView(fila);
        }
    }
}

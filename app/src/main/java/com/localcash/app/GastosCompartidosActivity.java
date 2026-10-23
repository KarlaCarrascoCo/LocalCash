package com.localcash.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.localcash.app.data.ContactoRepository;
import com.localcash.app.data.GastoCompartidoRepository;
import com.localcash.app.model.Contacto;
import com.localcash.app.model.GastoCompartido;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** División local de gastos entre el usuario y varios contactos. */
public final class GastosCompartidosActivity extends AppCompatActivity {
    private ContactoRepository contactos;
    private GastoCompartidoRepository gastos;
    private EditText descripcion, total;
    private LinearLayout lista;
    private TextView contactosSeleccionadosTexto;
    private List<Contacto> contactosDisponibles = new ArrayList<>();
    private final List<Contacto> contactosSeleccionados = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_gastos_compartidos);
        contactos = new ContactoRepository(this);
        gastos = new GastoCompartidoRepository(this);
        descripcion = findViewById(R.id.input_shared_description);
        total = findViewById(R.id.input_shared_total);
        lista = findViewById(R.id.list_shared);
        contactosSeleccionadosTexto = findViewById(R.id.text_shared_contacts);
        findViewById(R.id.button_select_shared_contacts).setOnClickListener(v -> seleccionarContactos());
        findViewById(R.id.button_add_shared).setOnClickListener(v -> agregar());
        cargar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargar();
    }

    private void seleccionarContactos() {
        if (contactosDisponibles.isEmpty()) {
            Toast.makeText(this, R.string.error_shared_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] nombres = new String[contactosDisponibles.size()];
        boolean[] marcados = new boolean[nombres.length];
        for (int i = 0; i < nombres.length; i++) {
            Contacto contacto = contactosDisponibles.get(i);
            nombres[i] = contacto.getNombre();
            marcados[i] = contieneContacto(contacto.getId());
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_select_shared_contacts)
                .setMultiChoiceItems(nombres, marcados, (dialog, which, checked) -> {
                    Contacto contacto = contactosDisponibles.get(which);
                    if (checked && !contieneContacto(contacto.getId())) {
                        contactosSeleccionados.add(contacto);
                    } else if (!checked) {
                        quitarContacto(contacto.getId());
                    }
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> actualizarResumenContactos())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void agregar() {
        long monto;
        try {
            monto = Long.parseLong(total.getText().toString());
        } catch (NumberFormatException e) {
            monto = 0;
        }
        String texto = descripcion.getText().toString().trim();
        if (contactosSeleccionados.isEmpty() || texto.isEmpty() || monto <= contactosSeleccionados.size()) {
            Toast.makeText(this, R.string.error_shared_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        gastos.crearParaContactos(contactosSeleccionados, texto, monto);
        descripcion.setText("");
        total.setText("");
        contactosSeleccionados.clear();
        cargar();
    }

    private void cargar() {
        contactosDisponibles = contactos.obtenerTodos();
        for (int i = contactosSeleccionados.size() - 1; i >= 0; i--) {
            if (!contieneEnDisponibles(contactosSeleccionados.get(i).getId())) {
                contactosSeleccionados.remove(i);
            }
        }
        actualizarResumenContactos();
        lista.removeAllViews();
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
        for (GastoCompartido gasto : gastos.obtenerTodos()) {
            CheckBox item = new CheckBox(this);
            item.setPadding(0, 16, 0, 16);
            item.setTextColor(getColor(R.color.lc_text_primary));
            item.setText(getString(R.string.shared_item_format, gasto.getContacto(),
                    gasto.getDescripcion(), formato.format(gasto.getMontoDeuda()), gasto.getParticipantes())
                    + " · " + getString(gasto.isPagado() ? R.string.shared_status_paid : R.string.shared_status_pending));
            item.setChecked(gasto.isPagado());
            item.setOnCheckedChangeListener((button, pagado) -> gastos.marcarPagado(gasto.getId(), pagado));
            lista.addView(item);
        }
        if (lista.getChildCount() == 0) {
            TextView vacio = new TextView(this);
            vacio.setText(R.string.shared_empty);
            lista.addView(vacio);
        }
    }

    private void actualizarResumenContactos() {
        if (contactosSeleccionados.isEmpty()) {
            contactosSeleccionadosTexto.setText(R.string.shared_contacts_none);
        } else {
            contactosSeleccionadosTexto.setText(
                    getString(R.string.shared_contacts_selected, contactosSeleccionados.size()));
        }
    }

    private boolean contieneContacto(long id) {
        for (Contacto contacto : contactosSeleccionados) if (contacto.getId() == id) return true;
        return false;
    }

    private boolean contieneEnDisponibles(long id) {
        for (Contacto contacto : contactosDisponibles) if (contacto.getId() == id) return true;
        return false;
    }

    private void quitarContacto(long id) {
        for (int i = contactosSeleccionados.size() - 1; i >= 0; i--) {
            if (contactosSeleccionados.get(i).getId() == id) contactosSeleccionados.remove(i);
        }
    }
}

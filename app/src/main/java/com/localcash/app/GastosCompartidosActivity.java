package com.localcash.app;

import android.os.Bundle;
import android.content.Intent;
import android.widget.EditText;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.google.android.material.button.MaterialButton;
import java.util.Locale;

/** División local de gastos entre el usuario y varios contactos. */
public final class GastosCompartidosActivity extends AppCompatActivity {
    private ContactoRepository contactos;
    private GastoCompartidoRepository gastos;
    private EditText descripcion, total;
    private EditText detalle;
    private LinearLayout lista;
    private TextView contactosSeleccionadosTexto;
    private List<Contacto> contactosDisponibles = new ArrayList<>();
    private final List<Contacto> contactosSeleccionados = new ArrayList<>();
    private final Map<Long, Long> asignacionesPersonalizadas = new HashMap<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_gastos_compartidos);
        contactos = new ContactoRepository(this);
        gastos = new GastoCompartidoRepository(this);
        descripcion = findViewById(R.id.input_shared_description);
        total = findViewById(R.id.input_shared_total);
        detalle = findViewById(R.id.input_shared_details);
        lista = findViewById(R.id.list_shared);
        contactosSeleccionadosTexto = findViewById(R.id.text_shared_contacts);
        findViewById(R.id.button_select_shared_contacts).setOnClickListener(v -> seleccionarContactos());
        findViewById(R.id.button_add_shared).setOnClickListener(v -> agregar());
        findViewById(R.id.button_custom_allocation).setOnClickListener(v -> asignarMontosPersonalizados());
        findViewById(R.id.button_back_shared).setOnClickListener(v -> finish());
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
        try {
            List<String> items = new ArrayList<>();
            for (String linea : detalle.getText().toString().split("\\n")) {
                if (!linea.trim().isEmpty()) items.add(linea.trim());
            }
            if (items.isEmpty()) {
                if (!asignacionesPersonalizadas.isEmpty()) {
                    throw new IllegalArgumentException("La asignación personalizada requiere detalle.");
                }
                gastos.crearParaContactos(contactosSeleccionados, texto, monto);
            } else {
                gastos.crearCompraDetallada(contactosSeleccionados, texto, monto, items,
                        asignacionesPersonalizadas.isEmpty() ? null : asignacionesPersonalizadas);
            }
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.error_shared_detail, Toast.LENGTH_LONG).show();
            return;
        }
        descripcion.setText("");
        total.setText("");
        detalle.setText("");
        contactosSeleccionados.clear();
        asignacionesPersonalizadas.clear();
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
        Map<String, List<GastoCompartido>> grupos = new LinkedHashMap<>();
        for (GastoCompartido gasto : gastos.obtenerTodos()) {
            String clave = gasto.getDescripcion() + "\u0000" + gasto.getMontoTotal() + "\u0000" + gasto.getParticipantes();
            List<GastoCompartido> grupo = grupos.get(clave);
            if (grupo == null) { grupo = new ArrayList<>(); grupos.put(clave, grupo); }
            grupo.add(gasto);
        }
        for (List<GastoCompartido> grupo : grupos.values()) {
            GastoCompartido primero = grupo.get(0);
            int pendientes = 0;
            for (GastoCompartido gasto : grupo) if (!gasto.isPagado()) pendientes++;
            MaterialButton item = new MaterialButton(this);
            item.setPadding(0, 16, 0, 16);
            item.setText(getString(R.string.shared_group_item_format, primero.getDescripcion(),
                    formato.format(primero.getMontoTotal()), pendientes, grupo.size()));
            item.setOnClickListener(v -> startActivity(new Intent(this, DeudaGrupoActivity.class)
                    .putExtra(DeudaGrupoActivity.EXTRA_DESCRIPCION, primero.getDescripcion())
                    .putExtra(DeudaGrupoActivity.EXTRA_MONTO_TOTAL, primero.getMontoTotal())
                    .putExtra(DeudaGrupoActivity.EXTRA_PARTICIPANTES, primero.getParticipantes())));
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

    private void asignarMontosPersonalizados() {
        if (contactosSeleccionados.isEmpty()) {
            Toast.makeText(this, R.string.shared_contacts_none, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout campos = new LinearLayout(this); campos.setOrientation(LinearLayout.VERTICAL);
        campos.setPadding(48, 0, 48, 0);
        Map<Long, EditText> entradas = new HashMap<>();
        for (Contacto contacto : contactosSeleccionados) {
            EditText entrada = new EditText(this); entrada.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            entrada.setHint(contacto.getNombre());
            if (asignacionesPersonalizadas.containsKey(contacto.getId())) {
                entrada.setText(String.valueOf(asignacionesPersonalizadas.get(contacto.getId())));
            }
            entradas.put(contacto.getId(), entrada); campos.addView(entrada);
        }
        new AlertDialog.Builder(this).setTitle(R.string.shared_custom_allocation_title).setView(campos)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(android.R.string.ok, (d, w) -> {
                    asignacionesPersonalizadas.clear();
                    try {
                        for (Map.Entry<Long, EditText> entrada : entradas.entrySet()) {
                            asignacionesPersonalizadas.put(entrada.getKey(), Long.parseLong(entrada.getValue().getText().toString()));
                        }
                    } catch (NumberFormatException error) {
                        asignacionesPersonalizadas.clear();
                        Toast.makeText(this, R.string.error_shared_detail, Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }
}

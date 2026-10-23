package com.localcash.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;

import com.localcash.app.data.ContactoRepository;
import com.localcash.app.model.Contacto;

/** Agenda local, independiente de los contactos del teléfono. */
public final class ContactosActivity extends AppCompatActivity {
    private ContactoRepository repository; private EditText nombre, telefono; private LinearLayout lista;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_contactos); repository = new ContactoRepository(this);
        nombre = findViewById(R.id.input_contact_name); telefono = findViewById(R.id.input_contact_phone); lista = findViewById(R.id.list_contacts);
        findViewById(R.id.button_add_contact).setOnClickListener(v -> agregar()); cargar();
    }
    private void agregar() {
        String valor = nombre.getText().toString().trim(); if (valor.isEmpty()) { Toast.makeText(this, R.string.error_contact_name, Toast.LENGTH_SHORT).show(); return; }
        repository.crear(valor, telefono.getText().toString().trim()); nombre.setText(""); telefono.setText(""); cargar();
    }
    private void cargar() {
        lista.removeAllViews(); for (Contacto contacto : repository.obtenerTodos()) {
            LinearLayout fila = new LinearLayout(this); fila.setPadding(0, 12, 0, 12);
            TextView item = new TextView(this); item.setTextColor(getColor(R.color.lc_text_primary));
            item.setText(getString(R.string.contact_item_format, contacto.getNombre(), contacto.getTelefono()));
            fila.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton editar = new MaterialButton(this); editar.setText(R.string.action_edit);
            editar.setOnClickListener(v -> editar(contacto)); fila.addView(editar);
            MaterialButton eliminar = new MaterialButton(this); eliminar.setText(R.string.action_delete);
            eliminar.setOnClickListener(v -> confirmarEliminar(contacto)); fila.addView(eliminar);
            lista.addView(fila);
        }
        if (lista.getChildCount() == 0) { TextView vacio = new TextView(this); vacio.setText(R.string.contacts_empty); lista.addView(vacio); }
    }

    private void editar(Contacto contacto) {
        LinearLayout campos = new LinearLayout(this); campos.setOrientation(LinearLayout.VERTICAL); int p = 48;
        campos.setPadding(p, 0, p, 0);
        EditText nuevoNombre = new EditText(this); nuevoNombre.setHint(R.string.contact_name_hint); nuevoNombre.setText(contacto.getNombre()); campos.addView(nuevoNombre);
        EditText nuevoTelefono = new EditText(this); nuevoTelefono.setHint(R.string.contact_phone_hint); nuevoTelefono.setText(contacto.getTelefono()); campos.addView(nuevoTelefono);
        new AlertDialog.Builder(this).setTitle(R.string.contact_edit_title).setView(campos)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_save, (d, w) -> {
                    String nombreEditado = nuevoNombre.getText().toString().trim();
                    if (nombreEditado.isEmpty()) { Toast.makeText(this, R.string.error_contact_name, Toast.LENGTH_SHORT).show(); return; }
                    repository.actualizar(new Contacto(contacto.getId(), nombreEditado, nuevoTelefono.getText().toString().trim())); cargar();
                }).show();
    }

    private void confirmarEliminar(Contacto contacto) {
        new AlertDialog.Builder(this).setTitle(R.string.delete_contact_title).setMessage(R.string.delete_contact_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_delete, (d, w) -> {
                    if (!repository.eliminarSiNoTieneDeudas(contacto.getId())) Toast.makeText(this, R.string.error_contact_delete_protected, Toast.LENGTH_LONG).show();
                    cargar();
                }).show();
    }
}

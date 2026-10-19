package com.localcash.app;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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
            TextView item = new TextView(this); item.setPadding(0, 16, 0, 16); item.setTextColor(getColor(R.color.lc_text_primary));
            item.setText(getString(R.string.contact_item_format, contacto.getNombre(), contacto.getTelefono())); lista.addView(item);
        }
        if (lista.getChildCount() == 0) { TextView vacio = new TextView(this); vacio.setText(R.string.contacts_empty); lista.addView(vacio); }
    }
}

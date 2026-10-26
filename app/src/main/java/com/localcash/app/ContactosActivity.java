package com.localcash.app;

import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.database.sqlite.SQLiteConstraintException;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import com.localcash.app.data.ContactoRepository;
import com.localcash.app.model.Contacto;
import com.localcash.app.security.ProtectedActivity;

/** Agenda local, independiente de los contactos del teléfono. */
public final class ContactosActivity extends ProtectedActivity {
    private ContactoRepository repository; private EditText nombre, telefono; private LinearLayout lista;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_contactos); repository = new ContactoRepository(this);
        nombre = findViewById(R.id.input_contact_name); telefono = findViewById(R.id.input_contact_phone); lista = findViewById(R.id.list_contacts);
        findViewById(R.id.button_add_contact).setOnClickListener(v -> agregar()); cargar();
    }
    private void agregar() {
        String valor = nombre.getText().toString().trim(); if (valor.isEmpty()) { Toast.makeText(this, R.string.error_contact_name, Toast.LENGTH_SHORT).show(); return; }
        repository.crear(valor, telefono.getText().toString().trim()); nombre.setText(""); telefono.setText(""); cargar();
    }
    private void cargar() {
        lista.removeAllViews(); for (Contacto contacto : repository.obtenerTodos()) {
            lista.addView(crearTarjetaContacto(contacto));
        }
        if (lista.getChildCount() == 0) { TextView vacio = new TextView(this); vacio.setText(R.string.contacts_empty); lista.addView(vacio); }
    }

    private MaterialCardView crearTarjetaContacto(Contacto contacto) {
        MaterialCardView tarjeta = new MaterialCardView(this);
        tarjeta.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface));
        tarjeta.setRadius(dp(16));
        tarjeta.setStrokeWidth(dp(1));
        tarjeta.setStrokeColor(ContextCompat.getColor(this, R.color.lc_sage_outline));
        tarjeta.setCardElevation(0f);

        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(dp(14), dp(12), dp(14), dp(6));
        LinearLayout encabezado = new LinearLayout(this);
        encabezado.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(this);
        avatar.setText(inicialesDe(contacto.getNombre()));
        avatar.setTextColor(getColor(R.color.lc_green_dark));
        avatar.setTextSize(14);
        avatar.setTypeface(avatar.getTypeface(), android.graphics.Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.bg_contact_avatar);
        encabezado.addView(avatar, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textosParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textosParams.setMarginStart(dp(12));
        TextView nombreContacto = new TextView(this);
        nombreContacto.setText(contacto.getNombre());
        nombreContacto.setTextColor(getColor(R.color.lc_text_primary));
        nombreContacto.setTextSize(16);
        nombreContacto.setTypeface(nombreContacto.getTypeface(), android.graphics.Typeface.BOLD);
        nombreContacto.setSingleLine(false);
        TextView telefonoContacto = new TextView(this);
        String telefono = contacto.getTelefono() == null ? "" : contacto.getTelefono().trim();
        telefonoContacto.setText(telefono.isEmpty() ? getString(R.string.contact_phone_missing) : telefono);
        telefonoContacto.setTextColor(getColor(R.color.lc_text_secondary));
        telefonoContacto.setTextSize(13);
        telefonoContacto.setSingleLine(false);
        textos.addView(nombreContacto);
        textos.addView(telefonoContacto);
        encabezado.addView(textos, textosParams);
        contenido.addView(encabezado);

        LinearLayout acciones = new LinearLayout(this);
        acciones.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        acciones.setPadding(0, dp(4), 0, 0);
        MaterialButton editar = botonAccion(R.string.action_edit, R.drawable.ic_edit, R.color.lc_green_dark);
        editar.setOnClickListener(v -> editar(contacto));
        acciones.addView(editar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        MaterialButton eliminar = botonAccion(R.string.action_delete, R.drawable.ic_delete, R.color.lc_orange_earth);
        eliminar.setOnClickListener(v -> confirmarEliminar(contacto));
        LinearLayout.LayoutParams eliminarParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
        eliminarParams.setMarginStart(dp(4));
        acciones.addView(eliminar, eliminarParams);
        contenido.addView(acciones);

        tarjeta.addView(contenido);
        LinearLayout.LayoutParams tarjetaParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tarjetaParams.topMargin = dp(10);
        tarjeta.setLayoutParams(tarjetaParams);
        return tarjeta;
    }

    private MaterialButton botonAccion(int texto, int icono, int color) {
        MaterialButton boton = new MaterialButton(this);
        boton.setText(texto);
        boton.setAllCaps(false);
        boton.setTextSize(11);
        boton.setTextColor(getColor(color));
        boton.setIconResource(icono);
        boton.setIconTint(ColorStateList.valueOf(getColor(color)));
        boton.setIconSize(dp(16));
        boton.setIconPadding(dp(4));
        boton.setMinWidth(0);
        boton.setMinimumWidth(0);
        boton.setInsetTop(0);
        boton.setInsetBottom(0);
        boton.setStrokeWidth(0);
        boton.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        return boton;
    }

    private String inicialesDe(String nombreContacto) {
        String[] partes = nombreContacto.trim().split("\\s+");
        StringBuilder iniciales = new StringBuilder();
        for (String parte : partes) {
            if (!parte.isEmpty()) {
                iniciales.append(parte.substring(0, 1).toUpperCase());
                if (iniciales.length() == 2) break;
            }
        }
        return iniciales.length() == 0 ? "?" : iniciales.toString();
    }

    private int dp(int valor) {
        return (int) (valor * getResources().getDisplayMetrics().density);
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
                    try {
                        if (!repository.eliminarSiNoTieneDeudas(contacto.getId())) {
                            Toast.makeText(this, R.string.error_contact_delete_protected, Toast.LENGTH_LONG).show();
                        }
                    } catch (SQLiteConstraintException error) {
                        Toast.makeText(this, R.string.error_contact_delete_protected, Toast.LENGTH_LONG).show();
                    }
                    cargar();
                }).show();
    }
}

package com.localcash.app;

import android.os.Bundle;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.localcash.app.data.CuentaRepository;
import com.localcash.app.model.Cuenta;
import com.localcash.app.ui.CuentaFormDialog;
import com.localcash.app.security.AuthSession;

/** Administración local de cuentas; no permite borrar cuentas con movimientos. */
public final class CuentasActivity extends AppCompatActivity {
    private CuentaRepository repository;
    private LinearLayout lista;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AuthSession.isAuthenticated()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_cuentas);
        repository = new CuentaRepository(this); lista = findViewById(R.id.list_accounts);
        findViewById(R.id.button_add_account).setOnClickListener(view -> editar(null)); cargar();
    }

    private void cargar() {
        lista.removeAllViews();
        for (Cuenta cuenta : repository.obtenerTodas()) {
            LinearLayout fila = new LinearLayout(this); fila.setGravity(Gravity.CENTER_VERTICAL);
            TextView texto = new TextView(this); texto.setTextColor(getColor(R.color.lc_text_primary));
            texto.setText(getString(R.string.account_item_format, cuenta.getNombre(), cuenta.getTipo()));
            fila.addView(texto, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton editar = new MaterialButton(this); editar.setText(R.string.action_edit);
            editar.setOnClickListener(v -> editar(cuenta)); fila.addView(editar);
            MaterialButton eliminar = new MaterialButton(this); eliminar.setText(R.string.action_delete);
            eliminar.setOnClickListener(v -> confirmarEliminar(cuenta)); fila.addView(eliminar);
            lista.addView(fila);
        }
    }

    private void editar(Cuenta cuenta) {
        new CuentaFormDialog(this, cuenta, resultado -> {
            try {
                if (resultado.getId() == 0) repository.crear(resultado); else repository.actualizar(resultado);
                cargar();
            } catch (RuntimeException error) {
                Toast.makeText(this, R.string.error_account_create, Toast.LENGTH_LONG).show();
            }
        }).show();
    }

    private void confirmarEliminar(Cuenta cuenta) {
        new AlertDialog.Builder(this).setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message).setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (d, w) -> {
                    if (!repository.eliminarSiNoTieneMovimientos(cuenta.getId())) {
                        Toast.makeText(this, R.string.error_account_delete_protected, Toast.LENGTH_LONG).show();
                    }
                    cargar();
                }).show();
    }
}

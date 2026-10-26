package com.localcash.app;

import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.localcash.app.data.CuentaRepository;
import com.localcash.app.model.Cuenta;
import com.localcash.app.ui.CuentaFormDialog;
import com.localcash.app.security.AuthSession;
import com.localcash.app.security.ProtectedActivity;

/** Administración local de cuentas; no permite borrar cuentas con movimientos. */
public final class CuentasActivity extends ProtectedActivity {
    private CuentaRepository repository;
    private LinearLayout lista;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_cuentas);
        repository = new CuentaRepository(this); lista = findViewById(R.id.list_accounts);
        findViewById(R.id.button_add_account).setOnClickListener(view -> editar(null)); cargar();
    }

    private void cargar() {
        lista.removeAllViews();
        for (Cuenta cuenta : repository.obtenerTodas()) {
            lista.addView(crearTarjetaCuenta(cuenta));
        }
    }

    private MaterialCardView crearTarjetaCuenta(Cuenta cuenta) {
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

        ImageView icono = new ImageView(this);
        icono.setImageResource(iconoParaTipo(cuenta.getTipo()));
        icono.setImageTintList(ColorStateList.valueOf(getColor(R.color.lc_green_dark)));
        icono.setBackgroundResource(R.drawable.bg_tool_icon_light);
        icono.setPadding(dp(10), dp(10), dp(10), dp(10));
        encabezado.addView(icono, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textosParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textosParams.setMarginStart(dp(12));

        TextView nombre = new TextView(this);
        nombre.setText(cuenta.getNombre());
        nombre.setTextColor(getColor(R.color.lc_text_primary));
        nombre.setTextSize(16);
        nombre.setTypeface(nombre.getTypeface(), android.graphics.Typeface.BOLD);
        nombre.setSingleLine(false);

        TextView tipo = new TextView(this);
        tipo.setText(nombreTipoCuenta(cuenta.getTipo()));
        tipo.setTextColor(getColor(R.color.lc_text_secondary));
        tipo.setTextSize(13);
        tipo.setSingleLine(false);

        textos.addView(nombre);
        textos.addView(tipo);
        encabezado.addView(textos, textosParams);
        contenido.addView(encabezado);

        LinearLayout acciones = new LinearLayout(this);
        acciones.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        acciones.setPadding(0, dp(4), 0, 0);
        MaterialButton editar = botonAccion(R.string.action_edit, R.drawable.ic_edit, R.color.lc_green_dark);
        editar.setOnClickListener(v -> editar(cuenta));
        acciones.addView(editar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        MaterialButton eliminar = botonAccion(R.string.action_delete, R.drawable.ic_delete, R.color.lc_orange_earth);
        eliminar.setOnClickListener(v -> confirmarEliminar(cuenta));
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

    private String nombreTipoCuenta(String tipo) {
        if (Cuenta.TIPO_CUENTA_VISTA.equals(tipo)) return getString(R.string.account_type_vista);
        if (Cuenta.TIPO_CUENTA_CORRIENTE.equals(tipo)) return getString(R.string.account_type_corriente);
        if (Cuenta.TIPO_EFECTIVO.equals(tipo)) return getString(R.string.account_type_efectivo);
        if (Cuenta.TIPO_AHORROS.equals(tipo)) return getString(R.string.account_type_ahorros);
        if (Cuenta.TIPO_TARJETA_CREDITO.equals(tipo)) return getString(R.string.account_type_credit_card);
        if (Cuenta.TIPO_CUENTA_DIGITAL.equals(tipo)) return getString(R.string.account_type_digital);
        return getString(R.string.account_type_other);
    }

    private int iconoParaTipo(String tipo) {
        if (Cuenta.TIPO_CUENTA_VISTA.equals(tipo) || Cuenta.TIPO_CUENTA_CORRIENTE.equals(tipo)) {
            return R.drawable.ic_bank;
        }
        if (Cuenta.TIPO_EFECTIVO.equals(tipo) || Cuenta.TIPO_AHORROS.equals(tipo)) {
            return R.drawable.ic_wallet_coins;
        }
        return R.drawable.ic_wallet;
    }

    private int dp(int valor) {
        return (int) (valor * getResources().getDisplayMetrics().density);
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

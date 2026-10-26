package com.localcash.app;

import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.localcash.app.data.ContactoRepository;
import com.localcash.app.data.GastoCompartidoRepository;
import com.localcash.app.model.Contacto;
import com.localcash.app.model.GastoCompartido;
import com.localcash.app.security.ProtectedActivity;
import com.localcash.app.util.SharedExpenseRules;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import androidx.core.content.ContextCompat;
import java.util.Locale;

/** División local de gastos entre el usuario y varios contactos. */
public final class GastosCompartidosActivity extends ProtectedActivity {
    private ContactoRepository contactos;
    private GastoCompartidoRepository gastos;
    private EditText descripcion, total;
    private EditText detalle;
    private LinearLayout lista;
    private LinearLayout contenedorDetalle;
    private TextView contactosSeleccionadosTexto, textoDistribucion, textoConfirmacion;
    private View tarjetaConfirmacion, contenedorDeudas;
    private MaterialButton botonEquitativa, botonPersonalizada;
    private List<Contacto> contactosDisponibles = new ArrayList<>();
    private final List<Contacto> contactosSeleccionados = new ArrayList<>();
    private final Map<Long, Long> asignacionesPersonalizadas = new HashMap<>();
    private long montoUsuarioPersonalizado = -1L;
    private boolean confirmacionVisible;
    private boolean guardando;
    private boolean mostrarPagadas;
    private PendienteConfirmacion pendienteConfirmacion;
    private boolean creandoParticipante;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_gastos_compartidos);
        contactos = new ContactoRepository(this);
        gastos = new GastoCompartidoRepository(this);
        descripcion = findViewById(R.id.input_shared_description);
        total = findViewById(R.id.input_shared_total);
        detalle = findViewById(R.id.input_shared_details);
        lista = findViewById(R.id.list_shared);
        contactosSeleccionadosTexto = findViewById(R.id.text_shared_contacts);
        contenedorDetalle = findViewById(R.id.container_shared_details);
        textoDistribucion = findViewById(R.id.text_shared_distribution);
        tarjetaConfirmacion = findViewById(R.id.card_shared_confirmation);
        textoConfirmacion = findViewById(R.id.text_shared_confirmation);
        contenedorDeudas = findViewById(R.id.container_shared_debts);
        botonEquitativa = findViewById(R.id.button_equal_allocation);
        botonPersonalizada = findViewById(R.id.button_custom_allocation);
        findViewById(R.id.button_select_shared_contacts).setOnClickListener(v -> seleccionarContactos());
        findViewById(R.id.button_add_shared).setOnClickListener(v -> agregar());
        botonPersonalizada.setOnClickListener(v -> asignarMontosPersonalizados());
        botonEquitativa.setOnClickListener(v -> {
            asignacionesPersonalizadas.clear(); montoUsuarioPersonalizado = -1L;
            textoDistribucion.setText(R.string.shared_distribution_equal);
            actualizarEstiloDivision();
        });
        findViewById(R.id.button_toggle_shared_detail).setOnClickListener(v -> alternarDetalle());
        findViewById(R.id.button_cancel_shared_confirmation).setOnClickListener(v -> ocultarConfirmacion());
        findViewById(R.id.button_confirm_shared).setOnClickListener(v -> confirmarPendiente());
        findViewById(R.id.button_filter_shared_pending).setOnClickListener(v -> { mostrarPagadas = false; cargar(); });
        findViewById(R.id.button_filter_shared_paid).setOnClickListener(v -> { mostrarPagadas = true; cargar(); });
        findViewById(R.id.button_toggle_shared_debts).setOnClickListener(v -> alternarDeudas());
        findViewById(R.id.button_back_shared).setOnClickListener(v -> finish());
        cargar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!requireAuthenticatedSession()) return;
        cargar();
    }

    private void seleccionarContactos() {
        contactosDisponibles = contactos.obtenerTodos();
        String[] nombres = new String[contactosDisponibles.size()];
        boolean[] marcados = new boolean[nombres.length];
        for (int i = 0; i < nombres.length; i++) {
            Contacto contacto = contactosDisponibles.get(i);
            nombres[i] = contacto.getNombre();
            marcados[i] = contieneContacto(contacto.getId());
        }
        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        contenido.setPadding(padding, 0, padding, 0);
        ListView listaContactos = new ListView(this);
        listaContactos.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listaContactos.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_multiple_choice, nombres));
        for (int i = 0; i < marcados.length; i++) listaContactos.setItemChecked(i, marcados[i]);
        listaContactos.setOnItemClickListener((parent, view, which, id) -> {
                    Contacto contacto = contactosDisponibles.get(which);
                    if (listaContactos.isItemChecked(which) && !contieneContacto(contacto.getId())) {
                        contactosSeleccionados.add(contacto);
                    } else if (!listaContactos.isItemChecked(which)) {
                        quitarContacto(contacto.getId());
                    }
                    asignacionesPersonalizadas.clear();
                    montoUsuarioPersonalizado = -1L;
        });
        contenido.addView(listaContactos, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(280)));
        MaterialButton agregar = new MaterialButton(this);
        agregar.setText(getString(R.string.action_add_shared_participant));
        agregar.setAllCaps(false);
        agregar.setCornerRadius(dp(16));
        agregar.setTextColor(getColor(R.color.white));
        agregar.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.lc_green_dark)));
        agregar.setStrokeWidth(0);
        contenido.addView(agregar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        AlertDialog selector = new AlertDialog.Builder(this)
                .setTitle(R.string.action_select_shared_contacts)
                .setView(contenido)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> actualizarResumenContactos())
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        agregar.setOnClickListener(v -> {
            selector.dismiss();
            mostrarFormularioNuevoParticipante();
        });
        selector.show();
    }

    private void mostrarFormularioNuevoParticipante() {
        if (creandoParticipante) return;
        LinearLayout campos = new LinearLayout(this);
        campos.setOrientation(LinearLayout.VERTICAL);
        campos.setPadding(dp(24), 0, dp(24), 0);
        EditText nombre = new EditText(this);
        nombre.setHint(R.string.contact_name_hint);
        EditText telefono = new EditText(this);
        telefono.setHint(R.string.contact_phone_hint);
        telefono.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        campos.addView(nombre); campos.addView(telefono);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.shared_new_participant_title)
                .setView(campos).setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_add, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String nombreNuevo = nombre.getText().toString().trim();
            if (nombreNuevo.isEmpty()) { nombre.setError(getString(R.string.error_contact_name)); return; }
            if (existeContactoConNombre(nombreNuevo)) {
                nombre.setError(getString(R.string.error_shared_contact_duplicate)); return;
            }
            if (creandoParticipante) return;
            creandoParticipante = true;
            try {
                long id = contactos.crear(nombreNuevo, telefono.getText().toString().trim());
                if (id <= 0) throw new IllegalStateException();
                contactosDisponibles = contactos.obtenerTodos();
                for (Contacto contacto : contactosDisponibles) {
                    if (contacto.getId() == id && !contieneContacto(id)) contactosSeleccionados.add(contacto);
                }
                asignacionesPersonalizadas.clear();
                montoUsuarioPersonalizado = -1L;
                actualizarResumenContactos();
                dialog.dismiss();
                seleccionarContactos();
            } catch (RuntimeException error) {
                Toast.makeText(this, R.string.error_shared_contact_save, Toast.LENGTH_LONG).show();
            } finally {
                creandoParticipante = false;
            }
        }));
        dialog.show();
    }

    private boolean existeContactoConNombre(String nombre) {
        for (Contacto contacto : contactosDisponibles) {
            if (contacto.getNombre().trim().equalsIgnoreCase(nombre.trim())) return true;
        }
        return false;
    }

    private void agregar() {
        if (confirmacionVisible || guardando) return;
        descripcion.setError(null);
        total.setError(null);
        detalle.setError(null);
        long monto = leerMontoTotal();
        String texto = descripcion.getText().toString().trim();
        if (texto.isEmpty()) {
            descripcion.setError(getString(R.string.error_shared_description));
            return;
        }
        if (monto <= 0) return;
        if (contactosSeleccionados.isEmpty()) {
            Toast.makeText(this, R.string.shared_contacts_none, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> items;
        try {
            items = SharedExpenseRules.validarDetalleOpcional(detalle.getText().toString(), monto);
        } catch (IllegalArgumentException error) {
            detalle.setError(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Distribucion distribucion = resolverDistribucion(monto);
            mostrarConfirmacion(texto, monto, items, distribucion);
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private long leerMontoTotal() {
        try {
            long monto = Long.parseLong(total.getText().toString().trim());
            if (monto <= 0) throw new NumberFormatException();
            return monto;
        } catch (NumberFormatException error) {
            total.setError(getString(R.string.error_shared_total));
            return -1L;
        }
    }

    private Distribucion resolverDistribucion(long montoTotal) {
        List<Long> ids = new ArrayList<>();
        for (Contacto contacto : contactosSeleccionados) ids.add(contacto.getId());
        SharedExpenseRules.validarParticipantes(ids);
        if (asignacionesPersonalizadas.isEmpty()) {
            SharedExpenseRules.DivisionEquitativa division =
                    SharedExpenseRules.dividirEquitativamente(montoTotal, contactosSeleccionados.size());
            Map<Long, Long> montos = new LinkedHashMap<>();
            for (Contacto contacto : contactosSeleccionados) {
                montos.put(contacto.getId(), division.getMontoPorContacto());
            }
            return new Distribucion(montos, division.getMontoUsuario());
        }
        SharedExpenseRules.validarAsignacionesPersonalizadas(montoTotal, ids,
                asignacionesPersonalizadas, montoUsuarioPersonalizado);
        return new Distribucion(new LinkedHashMap<>(asignacionesPersonalizadas), montoUsuarioPersonalizado);
    }

    private void mostrarConfirmacion(String texto, long monto, List<String> items, Distribucion distribucion) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
        StringBuilder resumen = new StringBuilder();
        resumen.append(getString(R.string.shared_summary_total, formato.format(monto))).append("\n\n")
                .append(getString(R.string.shared_summary_participants)).append("\n")
                .append("• ").append(getString(R.string.shared_summary_user, formato.format(distribucion.montoUsuario)));
        for (Contacto contacto : contactosSeleccionados) {
            resumen.append("\n• ").append(contacto.getNombre()).append(": ")
                    .append(formato.format(distribucion.montosContactos.get(contacto.getId())));
        }
        if (!items.isEmpty()) resumen.append("\n\n").append(getString(R.string.shared_summary_detail, items.size()));
        confirmacionVisible = true;
        pendienteConfirmacion = new PendienteConfirmacion(texto, monto, items, distribucion);
        textoConfirmacion.setText(resumen);
        tarjetaConfirmacion.setVisibility(View.VISIBLE);
        ocultarTeclado();
        tarjetaConfirmacion.requestFocus();
    }

    private void confirmarPendiente() {
        if (pendienteConfirmacion == null || guardando) return;
        PendienteConfirmacion pendiente = pendienteConfirmacion;
        ocultarConfirmacion();
        guardarGasto(pendiente.descripcion, pendiente.monto, pendiente.items, pendiente.distribucion);
    }

    private void ocultarConfirmacion() {
        confirmacionVisible = false;
        pendienteConfirmacion = null;
        tarjetaConfirmacion.setVisibility(View.GONE);
    }

    private void alternarDetalle() {
        boolean visible = contenedorDetalle.getVisibility() == View.VISIBLE;
        contenedorDetalle.setVisibility(visible ? View.GONE : View.VISIBLE);
        ((MaterialButton) findViewById(R.id.button_toggle_shared_detail)).setText(
                visible ? R.string.action_add_shared_detail : R.string.action_hide_shared_detail);
    }

    private void ocultarTeclado() {
        InputMethodManager teclado = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View foco = getCurrentFocus();
        if (teclado != null && foco != null) teclado.hideSoftInputFromWindow(foco.getWindowToken(), 0);
    }

    private void guardarGasto(String texto, long monto, List<String> items, Distribucion distribucion) {
        if (guardando) return;
        guardando = true;
        try {
            if (items.isEmpty()) {
                gastos.crearParaContactos(contactosSeleccionados, texto, monto,
                        distribucion.montosContactos, distribucion.montoUsuario);
            } else {
                gastos.crearCompraDetallada(contactosSeleccionados, texto, monto, items,
                        distribucion.montosContactos, distribucion.montoUsuario);
            }
            descripcion.setText("");
            total.setText("");
            detalle.setText("");
            contactosSeleccionados.clear();
            asignacionesPersonalizadas.clear();
            montoUsuarioPersonalizado = -1L;
            ocultarConfirmacion();
            cargar();
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.error_shared_detail, Toast.LENGTH_LONG).show();
        } finally {
            guardando = false;
        }
    }

    private void cargar() {
        contactosDisponibles = contactos.obtenerTodos();
        for (int i = contactosSeleccionados.size() - 1; i >= 0; i--) {
            if (!contieneEnDisponibles(contactosSeleccionados.get(i).getId())) {
                contactosSeleccionados.remove(i);
            }
        }
        actualizarResumenContactos();
        textoDistribucion.setText(asignacionesPersonalizadas.isEmpty()
                ? R.string.shared_distribution_equal : R.string.shared_distribution_custom);
        actualizarEstiloDivision();
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
            if ((mostrarPagadas && pendientes > 0) || (!mostrarPagadas && pendientes == 0)) continue;
            lista.addView(crearTarjetaGrupo(primero, pendientes, grupo.size(), formato));
        }
        if (lista.getChildCount() == 0) {
            TextView vacio = new TextView(this);
            vacio.setText(R.string.shared_empty);
            lista.addView(vacio);
        }
    }

    private void actualizarResumenContactos() {
        if (contactosSeleccionados.isEmpty()) {
            contactosSeleccionadosTexto.setText(R.string.shared_contacts_empty_summary);
        } else {
            contactosSeleccionadosTexto.setText(
                    getString(R.string.shared_contacts_selected, contactosSeleccionados.size()));
        }
    }

    private void actualizarEstiloDivision() {
        boolean personalizada = !asignacionesPersonalizadas.isEmpty();
        estiloOpcionDivision(botonEquitativa, !personalizada);
        estiloOpcionDivision(botonPersonalizada, personalizada);
    }

    private void estiloOpcionDivision(MaterialButton boton, boolean seleccionada) {
        int color = getColor(seleccionada ? R.color.lc_green_dark : R.color.lc_surface);
        boton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        boton.setTextColor(getColor(seleccionada ? R.color.white : R.color.lc_green_dark));
        boton.setStrokeWidth(dp(1));
        boton.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.lc_sage_outline)));
    }

    private void alternarDeudas() {
        boolean visible = contenedorDeudas.getVisibility() == View.VISIBLE;
        contenedorDeudas.setVisibility(visible ? View.GONE : View.VISIBLE);
        ((MaterialButton) findViewById(R.id.button_toggle_shared_debts)).setText(
                visible ? R.string.action_show_shared_debts : R.string.action_hide_shared_debts);
    }

    private MaterialCardView crearTarjetaGrupo(GastoCompartido gasto, int pendientes, int totalDeudas,
                                                NumberFormat formato) {
        MaterialCardView tarjeta = new MaterialCardView(this);
        tarjeta.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface));
        tarjeta.setRadius(dp(16)); tarjeta.setStrokeWidth(dp(1));
        tarjeta.setStrokeColor(ContextCompat.getColor(this, pendientes == 0
                ? R.color.lc_income : R.color.lc_sage_outline));
        tarjeta.setCardElevation(0f);
        LinearLayout contenido = new LinearLayout(this); contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(dp(14), dp(12), dp(14), dp(6));
        TextView titulo = new TextView(this); titulo.setText(gasto.getDescripcion());
        titulo.setTextColor(getColor(R.color.lc_text_primary)); titulo.setTextSize(16);
        titulo.setTypeface(titulo.getTypeface(), android.graphics.Typeface.BOLD); contenido.addView(titulo);
        TextView monto = new TextView(this); monto.setText(formato.format(gasto.getMontoTotal()));
        monto.setTextColor(getColor(R.color.lc_orange_earth)); monto.setTextSize(15); contenido.addView(monto);
        TextView estado = new TextView(this); estado.setText(getString(R.string.shared_group_progress,
                totalDeudas - pendientes, totalDeudas, pendientes == 0
                        ? getString(R.string.shared_status_paid) : getString(R.string.shared_status_pending)));
        estado.setTextColor(getColor(R.color.lc_text_secondary)); contenido.addView(estado);
        MaterialButton ver = new MaterialButton(this); ver.setText(R.string.action_view_participants);
        ver.setAllCaps(false); ver.setTextSize(12); ver.setTextColor(getColor(R.color.lc_green_dark));
        ver.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        ver.setOnClickListener(v -> startActivity(new Intent(this, DeudaGrupoActivity.class)
                .putExtra(DeudaGrupoActivity.EXTRA_DESCRIPCION, gasto.getDescripcion())
                .putExtra(DeudaGrupoActivity.EXTRA_MONTO_TOTAL, gasto.getMontoTotal())
                .putExtra(DeudaGrupoActivity.EXTRA_PARTICIPANTES, gasto.getParticipantes())));
        LinearLayout acciones = new LinearLayout(this); acciones.setGravity(android.view.Gravity.END);
        acciones.addView(ver, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        contenido.addView(acciones); tarjeta.addView(contenido);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT); params.topMargin = dp(8); tarjeta.setLayoutParams(params);
        return tarjeta;
    }

    private int dp(int valor) {
        return (int) (valor * getResources().getDisplayMetrics().density);
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
        asignacionesPersonalizadas.remove(id);
    }

    private void asignarMontosPersonalizados() {
        if (contactosSeleccionados.isEmpty()) {
            Toast.makeText(this, R.string.shared_contacts_none, Toast.LENGTH_SHORT).show();
            return;
        }
        long montoTotal = leerMontoTotal();
        if (montoTotal <= 0) return;
        SharedExpenseRules.DivisionEquitativa division;
        try {
            division = SharedExpenseRules.dividirEquitativamente(montoTotal, contactosSeleccionados.size());
        } catch (IllegalArgumentException error) {
            total.setError(error.getMessage());
            return;
        }
        LinearLayout campos = new LinearLayout(this); campos.setOrientation(LinearLayout.VERTICAL);
        campos.setPadding(48, 0, 48, 0);
        Map<Long, EditText> entradas = new LinkedHashMap<>();
        for (Contacto contacto : contactosSeleccionados) {
            EditText entrada = new EditText(this); entrada.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            entrada.setHint(getString(R.string.shared_custom_contact_hint, contacto.getNombre()));
            entrada.setText(String.valueOf(asignacionesPersonalizadas.containsKey(contacto.getId())
                    ? asignacionesPersonalizadas.get(contacto.getId()) : division.getMontoPorContacto()));
            entradas.put(contacto.getId(), entrada); campos.addView(entrada);
        }
        EditText usuario = new EditText(this); usuario.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        usuario.setHint(R.string.shared_custom_user_hint);
        usuario.setText(String.valueOf(montoUsuarioPersonalizado > 0
                ? montoUsuarioPersonalizado : division.getMontoUsuario()));
        campos.addView(usuario);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.shared_custom_allocation_title).setView(campos)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(android.R.string.ok, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                Map<Long, Long> nuevasAsignaciones = new LinkedHashMap<>();
                List<Long> ids = new ArrayList<>();
                    try {
                        for (Map.Entry<Long, EditText> entrada : entradas.entrySet()) {
                        long valor = Long.parseLong(entrada.getValue().getText().toString().trim());
                        nuevasAsignaciones.put(entrada.getKey(), valor);
                        ids.add(entrada.getKey());
                        }
                    long montoUsuario = Long.parseLong(usuario.getText().toString().trim());
                    SharedExpenseRules.validarAsignacionesPersonalizadas(montoTotal, ids,
                            nuevasAsignaciones, montoUsuario);
                    asignacionesPersonalizadas.clear();
                    asignacionesPersonalizadas.putAll(nuevasAsignaciones);
                    montoUsuarioPersonalizado = montoUsuario;
                    actualizarEstiloDivision();
                    dialog.dismiss();
                } catch (NumberFormatException error) {
                    Toast.makeText(this, R.string.error_shared_custom_amount, Toast.LENGTH_LONG).show();
                } catch (IllegalArgumentException error) {
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                }
        }));
        dialog.show();
    }

    private static final class Distribucion {
        private final Map<Long, Long> montosContactos;
        private final long montoUsuario;

        private Distribucion(Map<Long, Long> montosContactos, long montoUsuario) {
            this.montosContactos = montosContactos;
            this.montoUsuario = montoUsuario;
        }
    }

    private static final class PendienteConfirmacion {
        private final String descripcion;
        private final long monto;
        private final List<String> items;
        private final Distribucion distribucion;

        private PendienteConfirmacion(String descripcion, long monto, List<String> items,
                                      Distribucion distribucion) {
            this.descripcion = descripcion;
            this.monto = monto;
            this.items = new ArrayList<>(items);
            this.distribucion = distribucion;
        }
    }
}

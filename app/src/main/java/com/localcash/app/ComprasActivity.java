package com.localcash.app;

import android.os.Bundle;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;

import com.localcash.app.data.ContactoRepository;
import com.localcash.app.data.ListaCompraRepository;
import com.localcash.app.model.Contacto;
import com.localcash.app.model.ListaCompra;
import com.localcash.app.security.ProtectedActivity;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Planificación local de compras. */
public final class ComprasActivity extends ProtectedActivity {
    private static final String TAG = "ShoppingLists";
    private ListaCompraRepository repository;
    private ContactoRepository contactoRepository;
    private LinearLayout lista;
    private TextView sectionTitle;
    private int tabActual;
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "CL"));
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        setContentView(R.layout.activity_compras);
        repository = new ListaCompraRepository(this); contactoRepository = new ContactoRepository(this);
        lista = findViewById(R.id.list_shopping); sectionTitle = findViewById(R.id.text_shopping_section);
        findViewById(R.id.button_new_shopping_list).setOnClickListener(view -> mostrarFormularioLista(null));
        TabLayout tabs = findViewById(R.id.tabs_shopping);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { tabActual = tab.getPosition(); cargar(); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        configurarNavegacion();
        cargar();
    }
    private void configurarNavegacion() {
        findViewById(R.id.button_shopping_nav_home).setOnClickListener(v -> abrir(MainActivity.class));
        findViewById(R.id.button_shopping_nav_history).setOnClickListener(v -> abrir(HistorialActivity.class));
        findViewById(R.id.button_shopping_nav_shopping).setOnClickListener(v -> { });
        findViewById(R.id.button_shopping_nav_more).setOnClickListener(v -> abrir(MasActivity.class));
    }

    private void abrir(Class<?> destination) {
        startActivity(new Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void cargar() {
        lista.removeAllViews();
        long ahora = System.currentTimeMillis();
        List<ListaCompra> listas;
        try {
            listas = tabActual == 0 ? repository.obtenerPendientes(ahora)
                    : tabActual == 1 ? repository.obtenerProgramadas(ahora) : repository.obtenerHistorial();
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudieron cargar las listas de compras.", error);
            Toast.makeText(this, R.string.error_shopping_load, Toast.LENGTH_LONG).show();
            return;
        }
        sectionTitle.setText(tabActual == 0 ? R.string.shopping_tab_pending
                : tabActual == 1 ? R.string.shopping_tab_scheduled : R.string.shopping_tab_history);
        for (ListaCompra compra : listas) agregarTarjeta(compra, ahora);
        if (listas.isEmpty()) { TextView vacio = new TextView(this); vacio.setText(R.string.shopping_empty_tab); vacio.setTextColor(getColor(R.color.lc_text_secondary)); lista.addView(vacio); }
    }

    private void agregarTarjeta(ListaCompra compra, long ahora) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface));
        card.setRadius(20f); card.setCardElevation(2f); card.setStrokeWidth(1);
        card.setStrokeColor(ContextCompat.getColor(this, R.color.lc_outline)); card.setUseCompatPadding(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(18, 15, 18, 15);
        TextView title = text(compra.getNombreLugar(), 17, R.color.lc_text_primary, true);
        if (compra.isCompletada()) {
            LinearLayout header = new LinearLayout(this);
            header.setGravity(android.view.Gravity.CENTER_VERTICAL);
            header.addView(title, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            MaterialButton deleteRecord = new MaterialButton(this);
            deleteRecord.setContentDescription(getString(R.string.content_description_delete_history_record));
            deleteRecord.setIconResource(R.drawable.ic_delete);
            deleteRecord.setIconTintResource(R.color.lc_expense);
            deleteRecord.setIconSize((int) (20 * getResources().getDisplayMetrics().density));
            deleteRecord.setMinWidth(0);
            deleteRecord.setMinimumWidth(0);
            deleteRecord.setInsetTop(0);
            deleteRecord.setInsetBottom(0);
            deleteRecord.setPadding(0, 0, 0, 0);
            deleteRecord.setStrokeWidth(0);
            deleteRecord.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            deleteRecord.setOnClickListener(v -> confirmarEliminarRegistroHistorico(compra));
            int touchSize = (int) (48 * getResources().getDisplayMetrics().density);
            header.addView(deleteRecord, new LinearLayout.LayoutParams(touchSize, touchSize));
            content.addView(header);
        } else {
            content.addView(title);
        }
        String fecha = compra.isCompletada() ? getString(R.string.shopping_completed_date, dateFormat.format(compra.getFechaCompletada()))
                : compra.isEnCompra() ? getString(R.string.shopping_in_progress_since,
                        dateFormat.format(compra.getFechaInicio() == null ? compra.getFechaCreacion() : compra.getFechaInicio()))
                : compra.getFechaProgramada() == null ? getString(R.string.shopping_pending_date)
                : compra.getFechaProgramada() <= ahora ? getString(R.string.shopping_overdue_date, dateFormat.format(compra.getFechaProgramada()))
                : getString(R.string.shopping_scheduled_date, dateFormat.format(compra.getFechaProgramada()));
        content.addView(text(fecha, 12, compra.getFechaProgramada() != null && !compra.isCompletada()
                && compra.getFechaProgramada() <= ahora ? R.color.lc_expense : R.color.lc_text_secondary, false));
        int progressText = compra.isCompletada() ? R.string.shopping_final_progress_format : R.string.shopping_progress_format;
        content.addView(text(getString(progressText, compra.getProductosComprados(), compra.getProductosTotales()),
                13, R.color.lc_text_secondary, false));
        String totals = compra.isCompletada()
                ? (compra.getTotalEstimado() > 0
                    ? getString(R.string.shopping_final_totals_comparison, currencyFormat.format(compra.getTotalReal()), currencyFormat.format(compra.getTotalEstimado()))
                    : getString(R.string.shopping_final_totals, currencyFormat.format(compra.getTotalReal())))
                : compra.isEnCompra()
                    ? (compra.getTotalReal() > 0
                        ? (compra.getTotalEstimado() > 0
                            ? getString(R.string.shopping_in_purchase_totals_comparison, currencyFormat.format(compra.getTotalReal()), currencyFormat.format(compra.getTotalEstimado()))
                            : getString(R.string.shopping_in_purchase_totals, currencyFormat.format(compra.getTotalReal())))
                        : getString(R.string.shopping_no_paid_prices))
                : (compra.getTotalEstimado() > 0
                    ? getString(R.string.shopping_estimated_total, currencyFormat.format(compra.getTotalEstimado()))
                    : getString(R.string.shopping_no_estimated_prices));
        content.addView(text(totals, 13, R.color.lc_green_dark, true));
        card.addView(content);
        card.setOnClickListener(v -> startActivity(new Intent(this, ListaCompraDetalleActivity.class)
                .putExtra(ListaCompraDetalleActivity.EXTRA_LISTA_ID, compra.getId())));
        if (!compra.isCompletada()) card.setOnLongClickListener(v -> { mostrarAcciones(compra); return true; });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT); params.topMargin = 8; lista.addView(card, params);
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sizeSp);
        view.setTextColor(ContextCompat.getColor(this, color));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void mostrarAcciones(ListaCompra compra) {
        new AlertDialog.Builder(this).setItems(new String[]{getString(R.string.action_edit), getString(R.string.action_delete)},
                (dialog, action) -> { if (action == 0) mostrarFormularioLista(compra); else confirmarEliminar(compra); }).show();
    }

    private void confirmarEliminar(ListaCompra compra) {
        new AlertDialog.Builder(this).setTitle(R.string.delete_shopping_title).setMessage(R.string.delete_shopping_list_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_delete,
                        (dialog, which) -> { repository.eliminarLista(compra.getId()); cargar(); }).show();
    }

    private void confirmarEliminarRegistroHistorico(ListaCompra compra) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_history_record_title)
                .setMessage(getString(R.string.delete_history_record_message, compra.getNombreLugar()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    try {
                        if (repository.eliminarRegistroFinalizado(compra.getId())) {
                            cargar();
                            Toast.makeText(this, R.string.delete_history_record_success,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.delete_history_record_error,
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (RuntimeException error) {
                        Toast.makeText(this, R.string.delete_history_record_error,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void mostrarFormularioLista(ListaCompra existente) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(54, 0, 54, 0);
        Spinner lugares = new Spinner(this); String[] labels = getResources().getStringArray(R.array.shopping_places);
        String[] tipos = {ListaCompra.LUGAR_FERIA, ListaCompra.LUGAR_SUPERMERCADO, ListaCompra.LUGAR_MALL,
                ListaCompra.LUGAR_FARMACIA, ListaCompra.LUGAR_OTRO};
        lugares.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        EditText otroLugar = new EditText(this); otroLugar.setHint(R.string.shopping_other_place_hint);
        fields.addView(lugares); fields.addView(otroLugar);
        MaterialButton dateButton = new MaterialButton(this); dateButton.setText(R.string.shopping_no_date);
        fields.addView(dateButton);
        MaterialButton clearDate = new MaterialButton(this); clearDate.setText(R.string.shopping_clear_date);
        fields.addView(clearDate);
        TextView contactsSummary = new TextView(this); contactsSummary.setPadding(0, 18, 0, 8); fields.addView(contactsSummary);
        MaterialButton selectContacts = new MaterialButton(this); selectContacts.setText(R.string.shopping_select_contacts); fields.addView(selectContacts);
        final Long[] selectedDate = {existente == null ? null : existente.getFechaProgramada()};
        List<Contacto> contacts = contactoRepository.obtenerTodos(); boolean[] selected = new boolean[contacts.size()];
        if (existente != null) { for (Contacto linked : repository.obtenerContactos(existente.getId())) for (int i = 0; i < contacts.size(); i++) if (contacts.get(i).getId() == linked.getId()) selected[i] = true; }
        int index = 0; if (existente != null) for (int i = 0; i < tipos.length; i++) if (tipos[i].equals(existente.getLugarTipo())) index = i;
        lugares.setSelection(index); otroLugar.setText(existente == null ? "" : existente.getLugarPersonalizado());
        otroLugar.setVisibility(index == 4 ? View.VISIBLE : View.GONE);
        lugares.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { otroLugar.setVisibility(position == 4 ? View.VISIBLE : View.GONE); } @Override public void onNothingSelected(AdapterView<?> p) { } });
        actualizarFechaBoton(dateButton, selectedDate[0]); clearDate.setVisibility(selectedDate[0] == null ? View.GONE : View.VISIBLE);
        actualizarResumenContactos(contactsSummary, contacts, selected);
        dateButton.setOnClickListener(v -> elegirFecha(selectedDate, dateButton, clearDate));
        clearDate.setOnClickListener(v -> { selectedDate[0] = null; actualizarFechaBoton(dateButton, null); clearDate.setVisibility(View.GONE); });
        selectContacts.setOnClickListener(v -> seleccionarContactos(contacts, selected, contactsSummary));
        new AlertDialog.Builder(this).setTitle(existente == null ? R.string.shopping_new_list_title : R.string.shopping_edit_list_title)
                .setView(fields).setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_save, (dialog, which) -> {
                    try {
                        String tipo = tipos[lugares.getSelectedItemPosition()]; List<Long> ids = new ArrayList<>();
                        for (int i = 0; i < selected.length; i++) if (selected[i]) ids.add(contacts.get(i).getId());
                        if (existente == null) repository.crearLista(tipo, otroLugar.getText().toString(), selectedDate[0], ids);
                        else repository.actualizarLista(existente.getId(), tipo, otroLugar.getText().toString(), selectedDate[0], ids);
                        cargar();
                    } catch (RuntimeException error) { Toast.makeText(this, R.string.error_shopping_list, Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void elegirFecha(Long[] selectedDate, MaterialButton button, MaterialButton clearButton) {
        Calendar calendar = Calendar.getInstance(); if (selectedDate[0] != null) calendar.setTimeInMillis(selectedDate[0]);
        new DatePickerDialog(this, (view, year, month, day) -> { calendar.set(year, month, day, 0, 0, 0); calendar.set(Calendar.MILLISECOND, 0); selectedDate[0] = calendar.getTimeInMillis(); actualizarFechaBoton(button, selectedDate[0]); clearButton.setVisibility(View.VISIBLE); },
                calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void seleccionarContactos(List<Contacto> contacts, boolean[] selected, TextView summary) {
        String[] names = new String[contacts.size()]; for (int i = 0; i < contacts.size(); i++) names[i] = contacts.get(i).getNombre();
        new AlertDialog.Builder(this).setTitle(R.string.shopping_select_contacts).setMultiChoiceItems(names, selected,
                (dialog, which, checked) -> selected[which] = checked).setPositiveButton(R.string.action_save,
                (dialog, which) -> actualizarResumenContactos(summary, contacts, selected)).setNegativeButton(R.string.action_cancel, null).show();
    }

    private void actualizarFechaBoton(MaterialButton button, Long date) { button.setText(date == null ? getString(R.string.shopping_no_date) : getString(R.string.shopping_date_button, dateFormat.format(date))); }
    private void actualizarResumenContactos(TextView summary, List<Contacto> contacts, boolean[] selected) {
        List<String> names = new ArrayList<>(); for (int i = 0; i < selected.length; i++) if (selected[i]) names.add(contacts.get(i).getNombre());
        summary.setText(names.isEmpty() ? getString(R.string.shopping_no_contacts) : getString(R.string.shopping_contacts_format, android.text.TextUtils.join(", ", names)));
    }
}

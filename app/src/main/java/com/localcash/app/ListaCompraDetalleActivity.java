package com.localcash.app;

import android.os.Bundle;
import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.localcash.app.data.ListaCompraRepository;
import com.localcash.app.model.Contacto;
import com.localcash.app.model.ListaCompra;
import com.localcash.app.model.ProductoCompra;
import com.localcash.app.security.ProtectedActivity;
import com.localcash.app.util.FinancialRules;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Calendar;
import java.util.Locale;

/** Detalle local de una lista: productos, precios y finalización sin crear movimientos financieros. */
public final class ListaCompraDetalleActivity extends ProtectedActivity {
    private static final String TAG = "ShoppingDetail";
    public static final String EXTRA_LISTA_ID = "lista_id";
    private ListaCompraRepository repository;
    private long listaId;
    private LinearLayout productsContainer;
    private TextView place, schedule, contacts, progress, totals;
    private MaterialButton addProduct, startList, completeList, editCompletedList, modifyDate, finishHistoryEdit;
    private TextView historyEditingNotice;
    private boolean editingHistory;
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "CL"));
    private final NumberFormat currency = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!requireAuthenticatedSession()) return;
        listaId = getIntent().getLongExtra(EXTRA_LISTA_ID, -1);
        if (listaId <= 0) { finish(); return; }
        setContentView(R.layout.activity_lista_compra_detalle);
        repository = new ListaCompraRepository(this);
        productsContainer = findViewById(R.id.list_products); place = findViewById(R.id.text_list_place);
        schedule = findViewById(R.id.text_list_schedule); contacts = findViewById(R.id.text_list_contacts);
        progress = findViewById(R.id.text_list_progress); totals = findViewById(R.id.text_list_totals);
        addProduct = findViewById(R.id.button_add_product); startList = findViewById(R.id.button_start_list);
        completeList = findViewById(R.id.button_complete_list); editCompletedList = findViewById(R.id.button_edit_completed_list);
        modifyDate = findViewById(R.id.button_modify_date); finishHistoryEdit = findViewById(R.id.button_finish_history_edit);
        historyEditingNotice = findViewById(R.id.text_history_editing_notice);
        findViewById(R.id.button_back_shopping_detail).setOnClickListener(v -> finish());
        addProduct.setOnClickListener(v -> mostrarFormularioProducto(null));
        startList.setOnClickListener(v -> iniciarCompraConConfirmacion());
        completeList.setOnClickListener(v -> confirmarCompletar());
        editCompletedList.setOnClickListener(v -> { editingHistory = true; cargar(); });
        finishHistoryEdit.setOnClickListener(v -> { editingHistory = false; cargar(); });
        modifyDate.setOnClickListener(v -> mostrarSelectorFecha());
    }

    @Override protected void onResume() { super.onResume(); if (requireAuthenticatedSession()) cargar(); }

    private void cargar() {
        ListaCompra lista;
        try {
            lista = repository.obtenerLista(listaId);
        } catch (RuntimeException error) {
            Log.e(TAG, "No se pudo cargar el detalle de compra.", error);
            Toast.makeText(this, R.string.error_shopping_load, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (lista == null) { finish(); return; }
        place.setText(lista.getNombreLugar());
        schedule.setText(lista.isCompletada() ? (lista.getFechaProgramada() == null
                ? getString(R.string.shopping_completed_date, dateFormat.format(lista.getFechaCompletada()))
                : getString(R.string.shopping_completed_with_schedule, dateFormat.format(lista.getFechaCompletada()), dateFormat.format(lista.getFechaProgramada())))
                : lista.isEnCompra() ? (lista.getFechaProgramada() == null
                    ? getString(R.string.shopping_started_date, dateFormat.format(fechaInicioParaMostrar(lista)))
                    : getString(R.string.shopping_started_with_schedule, dateFormat.format(fechaInicioParaMostrar(lista)), dateFormat.format(lista.getFechaProgramada())))
                : lista.getFechaProgramada() == null ? getString(R.string.shopping_pending_date)
                : getString(R.string.shopping_scheduled_date, dateFormat.format(lista.getFechaProgramada())));
        List<Contacto> linked = repository.obtenerContactos(listaId);
        StringBuilder names = new StringBuilder(); for (Contacto contacto : linked) { if (names.length() > 0) names.append(", "); names.append(contacto.getNombre()); }
        contacts.setText(names.length() == 0 ? getString(R.string.shopping_no_contacts) : getString(R.string.shopping_contacts_format, names));
        progress.setText(getString(R.string.shopping_products_bought_compact,
                lista.getProductosComprados(), lista.getProductosTotales()));
        actualizarResumen(lista);
        startList.setVisibility(!lista.isCompletada() && !lista.isEnCompra() ? View.VISIBLE : View.GONE);
        modifyDate.setVisibility(!lista.isCompletada() && !lista.isEnCompra() ? View.VISIBLE : View.GONE);
        addProduct.setVisibility(!lista.isCompletada() ? View.VISIBLE : View.GONE);
        completeList.setVisibility(lista.isEnCompra() ? View.VISIBLE : View.GONE);
        editCompletedList.setVisibility(lista.isCompletada() && !editingHistory ? View.VISIBLE : View.GONE);
        historyEditingNotice.setVisibility(lista.isCompletada() && editingHistory ? View.VISIBLE : View.GONE);
        finishHistoryEdit.setVisibility(lista.isCompletada() && editingHistory ? View.VISIBLE : View.GONE);
        productsContainer.removeAllViews();
        for (ProductoCompra producto : repository.obtenerProductos(listaId)) agregarProducto(producto, lista);
        if (productsContainer.getChildCount() == 0) { TextView empty = new TextView(this); empty.setText(R.string.shopping_empty_products); empty.setTextColor(getColor(R.color.lc_text_secondary)); productsContainer.addView(empty); }
    }

    private void agregarProducto(ProductoCompra producto, ListaCompra listaCompra) {
        MaterialCardView card = new MaterialCardView(this); card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.lc_surface));
        card.setRadius(dp(16)); card.setStrokeWidth(dp(1)); card.setStrokeColor(ContextCompat.getColor(this, R.color.lc_sage_outline));
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(14), dp(10), dp(14), dp(6));
        LinearLayout header = new LinearLayout(this); header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText(producto.getNombre()); title.setTextColor(getColor(R.color.lc_text_primary)); title.setTextSize(16); title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        if (listaCompra.isEnCompra() || listaCompra.isCompletada()) {
            MaterialButton state = crearBotonEstado(producto, listaCompra.isEnCompra());
            header.addView(state, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
        }
        boolean precioPagado = listaCompra.isEnCompra() || listaCompra.isCompletada();
        Long precio = precioPagado
                ? (producto.isComprado() ? producto.getPrecioReal() : null)
                : (producto.getPrecioEstimado() == 0 ? null : producto.getPrecioEstimado());
        content.addView(header);
        String quantity = cantidadVisible(producto.getCantidad());
        String detailText = precio == null ? (precioPagado
                ? (producto.isNoComprado()
                    ? getString(R.string.shopping_product_not_acquired_detail, quantity, producto.getUnidadVisible())
                    : getString(R.string.shopping_product_unreviewed_detail, quantity, producto.getUnidadVisible()))
                : getString(R.string.shopping_product_no_approx_detail, quantity, producto.getUnidadVisible()))
                : getString(R.string.shopping_product_price_line, quantity, producto.getUnidadVisible(), currency.format(precio));
        TextView detail = new TextView(this); detail.setText(detailText); detail.setTextColor(getColor(R.color.lc_text_secondary)); detail.setTextSize(13); content.addView(detail);
        if (precio != null) {
            TextView subtotal = new TextView(this);
            subtotal.setText(getString(R.string.shopping_product_total,
                    currency.format(FinancialRules.totalProducto(producto.getCantidad(), precio))));
            subtotal.setTextColor(getColor(R.color.lc_green_dark)); subtotal.setTextSize(14);
            subtotal.setTypeface(subtotal.getTypeface(), android.graphics.Typeface.BOLD);
            content.addView(subtotal);
        }
        if (!listaCompra.isCompletada() || editingHistory) {
            LinearLayout actions = new LinearLayout(this); actions.setGravity(android.view.Gravity.END); actions.setPadding(0, 5, 0, 0);
            MaterialButton edit = actionButton(R.string.action_edit, R.drawable.ic_edit); edit.setOnClickListener(v -> mostrarFormularioProducto(producto)); actions.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)));
            MaterialButton delete = actionButton(R.string.action_delete, R.drawable.ic_delete); delete.setOnClickListener(v -> confirmarEliminarProducto(producto)); LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(48)); deleteParams.setMarginStart(dp(4)); actions.addView(delete, deleteParams);
            content.addView(actions);
        }
        card.addView(content);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); params.topMargin = dp(8); productsContainer.addView(card, params);
    }

    private void confirmarCompletar() {
        List<ProductoCompra> products = repository.obtenerProductos(listaId);
        if (products.isEmpty()) { Toast.makeText(this, R.string.error_shopping_empty_list, Toast.LENGTH_LONG).show(); return; }
        long total = 0; int pendientes = 0; int sinRevisar = 0; int adquiridos = 0; StringBuilder detail = new StringBuilder();
        for (ProductoCompra product : products) {
            if (!product.isComprado() || product.getPrecioReal() == null) {
                pendientes++;
                if (product.isSinRevisar()) sinRevisar++;
                detail.append("• ").append(product.getNombre()).append(" · ")
                        .append(nombreEstadoProducto(product)).append("\n");
            } else {
                adquiridos++;
                long subtotal = FinancialRules.totalProducto(product.getCantidad(), product.getPrecioReal());
                total += subtotal;
                detail.append("• ").append(product.getNombre()).append(" · ")
                        .append(product.getCantidad()).append(" ").append(product.getUnidadVisible())
                        .append(" · ").append(currency.format(subtotal)).append("\n");
            }
        }
        if (!FinancialRules.puedeFinalizarCompra(products.size(), adquiridos)) {
            Toast.makeText(this, R.string.error_shopping_no_acquired_products, Toast.LENGTH_LONG).show();
            return;
        }
        detail.append(getString(R.string.shopping_final_total, currency.format(total)));
        if (pendientes > 0) detail.append("\n\n").append(getString(R.string.shopping_unpurchased_warning, pendientes));
        if (sinRevisar > 0) detail.append("\n").append(getString(R.string.shopping_unreviewed_warning, sinRevisar));
        new AlertDialog.Builder(this).setTitle(R.string.shopping_complete_title).setMessage(detail)
                .setNegativeButton(pendientes > 0 ? R.string.action_continue_editing : R.string.action_cancel, null).setPositiveButton(R.string.action_complete,
                        (dialog, which) -> { repository.completarLista(listaId); cargar(); }).show();
    }

    private void confirmarEliminarProducto(ProductoCompra product) {
        new AlertDialog.Builder(this).setTitle(R.string.delete_product_title).setMessage(R.string.delete_product_message)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_delete,
                        (dialog, which) -> { repository.eliminarProducto(product.getId()); cargar(); }).show();
    }

    private void mostrarFormularioProducto(ProductoCompra existing) {
        LinearLayout fields = new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(54, 0, 54, 0);
        EditText name = field(R.string.shopping_product_name_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText quantity = field(R.string.shopping_product_quantity_hint, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner units = new Spinner(this); units.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, getResources().getStringArray(R.array.shopping_units)));
        EditText customUnit = field(R.string.shopping_custom_unit_hint, InputType.TYPE_CLASS_TEXT);
        EditText price = field(R.string.shopping_estimated_unit_price_hint, InputType.TYPE_CLASS_NUMBER);
        fields.addView(name); fields.addView(quantity); fields.addView(units); fields.addView(customUnit); fields.addView(price);
        String[] codes = {"UNIDADES", "KG", "GRAMOS", "LITROS", "PARES", "PAQUETES", "OTRA"};
        int selected = 0;
        ListaCompra currentList = repository.obtenerLista(listaId); boolean paidMode = currentList != null && (currentList.isEnCompra() || currentList.isCompletada());
        if (existing != null) {
            name.setText(existing.getNombre());
            quantity.setText(String.valueOf(existing.getCantidad()));
            // No mezclar Long nullable con long en un ternario: un producto sin precio pagado
            // es válido durante EN_COMPRA y no debe provocar unboxing de null al editarlo.
            Long storedPrice = paidMode ? existing.getPrecioReal()
                    : Long.valueOf(existing.getPrecioEstimado());
            price.setText(storedPrice == null || storedPrice == 0 ? "" : String.valueOf(storedPrice));
            customUnit.setText(existing.getUnidadPersonalizada());
            for (int i = 0; i < codes.length; i++) {
                if (codes[i].equals(existing.getUnidad())) selected = i;
            }
        }
        units.setSelection(selected); customUnit.setVisibility(selected == 6 ? View.VISIBLE : View.GONE);
        units.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() { @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { customUnit.setVisibility(position == 6 ? View.VISIBLE : View.GONE); price.setHint(getString(paidMode ? R.string.shopping_paid_price_for_unit : R.string.shopping_approx_price_for_unit, getResources().getStringArray(R.array.shopping_units)[position])); } @Override public void onNothingSelected(AdapterView<?> p) { } });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(existing == null ? R.string.shopping_add_product_title : R.string.shopping_edit_product_title).setView(fields)
                .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.shopping_save_product, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            name.setError(null); quantity.setError(null); customUnit.setError(null); price.setError(null);
            if (name.getText().toString().trim().isEmpty()) { name.setError(getString(R.string.error_shopping_product_name)); return; }
            if (quantity.getText().toString().trim().isEmpty()) { quantity.setError(getString(R.string.error_shopping_product_quantity)); return; }
            if (units.getSelectedItemPosition() == 6 && customUnit.getText().toString().trim().isEmpty()) { customUnit.setError(getString(R.string.error_shopping_product_unit)); return; }
            double quantityValue;
            try { quantityValue = Double.parseDouble(quantity.getText().toString().trim().replace(',', '.')); }
            catch (NumberFormatException exception) { quantity.setError(getString(R.string.error_shopping_product_quantity)); return; }
            if (quantityValue <= 0) { quantity.setError(getString(R.string.error_shopping_product_quantity)); return; }
            boolean priceEntered = !price.getText().toString().trim().isEmpty();
            long priceValue = 0;
            if (priceEntered) try { priceValue = Long.parseLong(price.getText().toString().trim()); }
            catch (NumberFormatException exception) { price.setError(getString(R.string.error_shopping_product_price)); return; }
            if (priceValue < 0) { price.setError(getString(R.string.error_shopping_product_price)); return; }
            try {
                // No usar un ternario que combine long con Long: cuando el precio real es
                // opcional, Java intenta desempaquetar null a long antes de llegar a SQLite.
                long estimatedValue;
                Long realValue;
                if (paidMode) {
                    estimatedValue = existing == null ? 0L : existing.getPrecioEstimado();
                    realValue = priceEntered ? Long.valueOf(priceValue)
                            : (existing == null ? null : existing.getPrecioReal());
                } else {
                    estimatedValue = priceValue;
                    realValue = existing == null ? null : existing.getPrecioReal();
                }
                String productState = existing == null ? ProductoCompra.ESTADO_SIN_REVISAR : existing.getEstado();
                ProductoCompra product = new ProductoCompra(existing == null ? 0 : existing.getId(), listaId, name.getText().toString(), quantityValue,
                        codes[units.getSelectedItemPosition()], customUnit.getText().toString(), estimatedValue, realValue, productState);
                boolean saved = existing == null ? repository.agregarProducto(listaId, product) > 0
                        : repository.actualizarProducto(product);
                if (!saved) { Toast.makeText(this, R.string.error_shopping_product_save, Toast.LENGTH_LONG).show(); return; }
                dialog.dismiss(); cargar();
            } catch (IllegalStateException exception) { Toast.makeText(this, R.string.error_shopping_list_finalized, Toast.LENGTH_LONG).show();
            } catch (RuntimeException exception) { Log.e(TAG, "No se pudo guardar el producto.", exception); Toast.makeText(this, R.string.error_shopping_product_save, Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }

    private EditText field(int hint, int inputType) { EditText edit = new EditText(this); edit.setHint(hint); edit.setInputType(inputType); return edit; }
    private long parseOptionalMoney(EditText value, long fallback) { String text = value.getText().toString().trim(); return text.isEmpty() ? fallback : Long.parseLong(text); }

    private MaterialButton crearBotonEstado(ProductoCompra producto, boolean editable) {
        MaterialButton button = new MaterialButton(this);
        button.setText(producto.isComprado() ? getString(R.string.shopping_product_state_button_bought)
                : producto.isNoComprado() ? getString(R.string.shopping_product_state_button_not_bought)
                : getString(R.string.shopping_product_state_button_unreviewed));
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setTextColor(getColor(producto.isComprado() ? R.color.lc_income
                : producto.isNoComprado() ? R.color.lc_expense : R.color.lc_text_secondary));
        button.setContentDescription(getString(R.string.shopping_product_state_description,
                nombreEstadoProducto(producto)));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setStrokeWidth(0); button.setInsetTop(0); button.setInsetBottom(0);
        button.setMinWidth(0); button.setMinimumWidth(0); button.setMinHeight(0); button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        if (editable) button.setOnClickListener(v -> mostrarSelectorEstado(producto));
        return button;
    }

    private void mostrarSelectorEstado(ProductoCompra producto) {
        String[] options = {
                getString(R.string.shopping_product_state_unreviewed),
                getString(R.string.shopping_product_state_bought),
                getString(R.string.shopping_product_state_not_bought)
        };
        int selected = producto.isComprado() ? 1 : producto.isNoComprado() ? 2 : 0;
        new AlertDialog.Builder(this).setTitle(R.string.shopping_product_state_selector_title)
                .setSingleChoiceItems(options, selected, (dialog, choice) -> {
                    if (choice == 1 && producto.getPrecioReal() == null) {
                        dialog.dismiss();
                        solicitarPrecioAlMarcarComprado(producto);
                        return;
                    }
                    String state = choice == 1 ? ProductoCompra.ESTADO_COMPRADO
                            : choice == 2 ? ProductoCompra.ESTADO_NO_COMPRADO
                            : ProductoCompra.ESTADO_SIN_REVISAR;
                    if (repository.actualizarEstadoProducto(producto.getId(), state)) cargar();
                    dialog.dismiss();
                }).setNegativeButton(R.string.action_cancel, null).show();
    }

    private void solicitarPrecioAlMarcarComprado(ProductoCompra producto) {
        LinearLayout priceContent = new LinearLayout(this);
        priceContent.setOrientation(LinearLayout.VERTICAL);
        priceContent.setPadding(dp(24), 0, dp(24), 0);
        TextView label = new TextView(this);
        label.setText(R.string.shopping_paid_price_required_hint);
        label.setTextColor(getColor(R.color.lc_text_primary));
        label.setTextSize(14);
        EditText precio = new EditText(this);
        precio.setInputType(InputType.TYPE_CLASS_NUMBER);
        precio.setContentDescription(getString(R.string.shopping_paid_price_required_hint));
        priceContent.addView(label);
        priceContent.addView(precio);
        if (producto.getPrecioReal() != null) precio.setText(String.valueOf(producto.getPrecioReal()));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.shopping_confirm_product_title)
                .setMessage(R.string.shopping_mark_bought_message).setView(priceContent)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.shopping_confirm, null).create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_shopping_price_dialog));
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundResource(R.drawable.bg_shopping_dialog_confirm_button);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setPadding(dp(16), 0, dp(16), 0);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = precio.getText().toString().trim();
            long price;
            try { price = Long.parseLong(value); } catch (NumberFormatException error) {
                precio.setError(getString(R.string.error_shopping_product_price)); return;
            }
            if (price < 0) { precio.setError(getString(R.string.error_shopping_product_price)); return; }
            ProductoCompra comprado = new ProductoCompra(producto.getId(), producto.getListaId(), producto.getNombre(),
                    producto.getCantidad(), producto.getUnidad(), producto.getUnidadPersonalizada(), producto.getPrecioEstimado(),
                    price, ProductoCompra.ESTADO_COMPRADO);
            if (repository.actualizarProducto(comprado)) { dialog.dismiss(); cargar(); }
            });
        });
        dialog.show();
    }

    private String nombreEstadoProducto(ProductoCompra producto) {
        return getString(producto.isComprado() ? R.string.shopping_product_state_bought
                : producto.isNoComprado() ? R.string.shopping_product_state_not_bought
                : R.string.shopping_product_state_unreviewed);
    }

    private String cantidadVisible(double cantidad) {
        NumberFormat format = NumberFormat.getNumberInstance(new Locale("es", "CL"));
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(0);
        return format.format(cantidad);
    }

    private MaterialButton actionButton(int text, int icon) {
        MaterialButton button = new MaterialButton(this); button.setText(text); button.setTextSize(11); button.setAllCaps(false);
        button.setIconResource(icon); button.setIconSize(dp(16)); button.setIconPadding(dp(4));
        button.setIconTint(ColorStateList.valueOf(getColor(R.color.lc_green_dark)));
        button.setMinWidth(0); button.setMinimumWidth(0); button.setInsetTop(0); button.setInsetBottom(0);
        button.setStrokeWidth(0); button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setTextColor(getColor(R.color.lc_green_dark)); return button;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    private void iniciarCompraConConfirmacion() {
        ListaCompra lista = repository.obtenerLista(listaId);
        if (lista != null && lista.getFechaProgramada() != null && lista.getFechaProgramada() > System.currentTimeMillis()) {
            new AlertDialog.Builder(this).setMessage(getString(R.string.shopping_start_early_message, dateFormat.format(lista.getFechaProgramada())))
                    .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.action_start_now,
                            (dialog, which) -> { repository.iniciarCompra(listaId); cargar(); }).show();
        } else { repository.iniciarCompra(listaId); cargar(); }
    }

    private void mostrarSelectorFecha() {
        ListaCompra lista = repository.obtenerLista(listaId); if (lista == null) return;
        if (lista.getFechaProgramada() != null) {
            new AlertDialog.Builder(this).setItems(new String[]{getString(R.string.action_choose_date), getString(R.string.action_remove_date)},
                    (dialog, which) -> { if (which == 0) abrirCalendario(lista); else { repository.actualizarFechaProgramada(listaId, null); cargar(); } }).show();
            return;
        }
        abrirCalendario(lista);
    }

    private void abrirCalendario(ListaCompra lista) {
        Calendar calendar = Calendar.getInstance(); if (lista.getFechaProgramada() != null) calendar.setTimeInMillis(lista.getFechaProgramada());
        new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(year, month, day, 0, 0, 0); calendar.set(Calendar.MILLISECOND, 0);
            repository.actualizarFechaProgramada(listaId, calendar.getTimeInMillis()); cargar();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void actualizarResumen(ListaCompra lista) {
        if (lista.isCompletada()) {
            totals.setText(lista.getTotalEstimado() > 0
                    ? getString(R.string.shopping_final_totals_comparison, currency.format(lista.getTotalReal()), currency.format(lista.getTotalEstimado()))
                    : getString(R.string.shopping_final_totals, currency.format(lista.getTotalReal())));
        } else if (lista.isEnCompra()) {
            totals.setText(lista.getTotalReal() > 0
                    ? (lista.getTotalEstimado() > 0
                        ? getString(R.string.shopping_in_purchase_totals_comparison, currency.format(lista.getTotalReal()), currency.format(lista.getTotalEstimado()))
                        : getString(R.string.shopping_in_purchase_totals, currency.format(lista.getTotalReal())))
                    : getString(R.string.shopping_no_paid_prices));
        } else if (lista.getTotalEstimado() > 0) {
            totals.setText(getString(R.string.shopping_estimated_total, currency.format(lista.getTotalEstimado())));
        } else { totals.setText(R.string.shopping_no_estimated_prices); }
    }

    private long fechaInicioParaMostrar(ListaCompra lista) {
        return lista.getFechaInicio() == null ? lista.getFechaCreacion() : lista.getFechaInicio();
    }
}

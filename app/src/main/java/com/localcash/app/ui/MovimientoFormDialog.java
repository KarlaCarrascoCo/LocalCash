package com.localcash.app.ui;

import android.app.Dialog;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localcash.app.R;
import com.localcash.app.model.Cuenta;
import com.localcash.app.model.Movimiento;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Captura y valida un movimiento antes de delegar su guardado a la actividad. */
public final class MovimientoFormDialog extends Dialog {

    public interface OnMovimientoConfirmadoListener {
        void onMovimientoConfirmado(Movimiento movimiento);
    }

    public interface OnCrearCuentaSolicitadaListener {
        void onCrearCuentaSolicitada();
    }

    public interface OnPermisoUbicacionSolicitadoListener {
        void onPermisoUbicacionSolicitado();
    }

    public interface OnUbicacionManualSolicitadaListener {
        void onUbicacionManualSolicitada(Double latitude, Double longitude);
    }

    private final OnMovimientoConfirmadoListener listener;
    private final OnCrearCuentaSolicitadaListener crearCuentaListener;
    private final OnPermisoUbicacionSolicitadoListener permisoUbicacionListener;
    private final OnUbicacionManualSolicitadaListener ubicacionManualListener;
    private final List<Cuenta> cuentas;
    private TextInputLayout descriptionLayout;
    private TextInputLayout amountLayout;
    private TextInputLayout categoryLayout;
    private TextInputLayout accountLayout;
    private TextInputLayout paymentLayout;
    private TextInputLayout otherPaymentLayout;
    private View associatedAccountContainer;
    private View optionalSection;
    private TextInputEditText descriptionInput;
    private TextInputEditText amountInput;
    private TextInputEditText otherPaymentInput;
    private AutoCompleteTextView categoryInput;
    private AutoCompleteTextView accountInput;
    private AutoCompleteTextView paymentInput;
    private ArrayAdapter<Cuenta> accountAdapter;
    private final List<Cuenta> compatibleAccounts = new ArrayList<>();
    private MaterialButtonToggleGroup typeToggleGroup;
    private long selectedAccountId = Cuenta.ID_TODAS_LAS_CUENTAS;
    private String selectedPaymentMethod;
    private Double latitude;
    private Double longitude;
    private TextView locationStatus;

    public MovimientoFormDialog(@NonNull Context context,
                                @NonNull List<Cuenta> cuentas,
                                @NonNull OnMovimientoConfirmadoListener listener) {
        this(context, cuentas, listener, null, null, null);
    }

    public MovimientoFormDialog(@NonNull Context context,
                                @NonNull List<Cuenta> cuentas,
                                @NonNull OnMovimientoConfirmadoListener listener,
                                OnCrearCuentaSolicitadaListener crearCuentaListener,
                                OnPermisoUbicacionSolicitadoListener permisoUbicacionListener) {
        this(context, cuentas, listener, crearCuentaListener, permisoUbicacionListener, null);
    }

    public MovimientoFormDialog(@NonNull Context context,
                                @NonNull List<Cuenta> cuentas,
                                @NonNull OnMovimientoConfirmadoListener listener,
                                OnCrearCuentaSolicitadaListener crearCuentaListener,
                                OnPermisoUbicacionSolicitadoListener permisoUbicacionListener,
                                OnUbicacionManualSolicitadaListener ubicacionManualListener) {
        super(context);
        this.cuentas = cuentas;
        this.listener = listener;
        this.crearCuentaListener = crearCuentaListener;
        this.permisoUbicacionListener = permisoUbicacionListener;
        this.ubicacionManualListener = ubicacionManualListener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_registrar_movimiento);

        descriptionLayout = findViewById(R.id.input_layout_description);
        amountLayout = findViewById(R.id.input_layout_amount);
        categoryLayout = findViewById(R.id.input_layout_category);
        accountLayout = findViewById(R.id.input_layout_account);
        paymentLayout = findViewById(R.id.input_layout_payment);
        otherPaymentLayout = findViewById(R.id.input_layout_other_payment);
        associatedAccountContainer = findViewById(R.id.container_associated_account);
        optionalSection = findViewById(R.id.optional_movement_section);
        descriptionInput = findViewById(R.id.input_description);
        amountInput = findViewById(R.id.input_amount);
        otherPaymentInput = findViewById(R.id.input_other_payment);
        categoryInput = findViewById(R.id.input_category);
        accountInput = findViewById(R.id.input_account);
        paymentInput = findViewById(R.id.input_payment);
        locationStatus = findViewById(R.id.text_location_status);
        typeToggleGroup = findViewById(R.id.toggle_movement_type);
        configurarSelectorCategorias();
        configurarSelectorCuentas();
        configurarSelectorMedioPago();
        typeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                actualizarFormularioPorTipo();
            }
        });
        actualizarFormularioPorTipo();
        actualizarCuentasCompatibles(null);

        TextView dateValue = findViewById(R.id.text_form_date_value);
        dateValue.setText(DateFormat.getDateInstance(DateFormat.LONG, new Locale("es", "CL"))
                .format(new Date()));

        findViewById(R.id.button_cancel_movement).setOnClickListener(view -> dismiss());
        findViewById(R.id.button_save_movement).setOnClickListener(view -> validarYConfirmar());
        findViewById(R.id.button_capture_location).setOnClickListener(view -> capturarUbicacion());
        findViewById(R.id.button_clear_location).setOnClickListener(view -> limpiarUbicacion());
        findViewById(R.id.button_pick_location_map).setOnClickListener(view -> solicitarUbicacionManual());
        findViewById(R.id.button_toggle_optional).setOnClickListener(view -> alternarOpcionales());
        findViewById(R.id.button_create_account).setOnClickListener(view -> {
            if (crearCuentaListener != null) {
                crearCuentaListener.onCrearCuentaSolicitada();
            }
        });

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void configurarSelectorCategorias() {
        String[] categorias = getContext().getResources().getStringArray(R.array.movement_categories);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_list_item_1,
                categorias);
        categoryInput.setAdapter(adapter);
        categoryInput.setKeyListener(null);
        categoryInput.setOnClickListener(view -> categoryInput.showDropDown());
        categoryInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                categoryInput.showDropDown();
            }
        });
        categoryInput.setOnItemClickListener((parent, view, position, id) -> categoryLayout.setError(null));
    }

    private void configurarSelectorCuentas() {
        accountAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_list_item_1,
                compatibleAccounts);
        accountInput.setAdapter(accountAdapter);
        accountInput.setKeyListener(null);
        accountInput.setOnClickListener(view -> accountInput.showDropDown());
        accountInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                accountInput.showDropDown();
            }
        });
        accountInput.setOnItemClickListener((parent, view, position, id) -> {
            Cuenta cuenta = accountAdapter.getItem(position);
            selectedAccountId = cuenta == null ? Cuenta.ID_TODAS_LAS_CUENTAS : cuenta.getId();
            accountLayout.setError(null);
        });
    }

    private void configurarSelectorMedioPago() {
        String[] medios = getContext().getResources().getStringArray(R.array.payment_methods);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, medios);
        paymentInput.setAdapter(adapter);
        paymentInput.setKeyListener(null);
        paymentInput.setOnClickListener(view -> paymentInput.showDropDown());
        paymentInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                paymentInput.showDropDown();
            }
        });
        paymentInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedPaymentMethod = mapearMedioPago(position);
            paymentLayout.setError(null);
            actualizarCampoOtro();
            actualizarCuentasCompatibles(null);
        });
    }

    /** Actualiza el selector sin cerrar el formulario y deja seleccionada la cuenta recién creada. */
    public void actualizarCuentas(@NonNull List<Cuenta> cuentasActualizadas, @NonNull Cuenta cuentaCreada) {
        if (!isShowing() || accountAdapter == null) {
            return;
        }
        cuentas.clear();
        cuentas.addAll(cuentasActualizadas);
        actualizarCuentasCompatibles(cuentaCreada);
    }

    private void validarYConfirmar() {
        clearErrors();

        String descripcion = getText(descriptionInput).trim();
        boolean esGasto = typeToggleGroup.getCheckedButtonId() == R.id.button_expense;
        String categoria = esGasto ? getText(categoryInput).trim()
                : getContext().getString(R.string.income_internal_category);
        String montoTexto = getText(amountInput).replace(".", "").replace(" ", "").trim();
        boolean esValido = true;

        if (TextUtils.isEmpty(descripcion)) {
            descriptionLayout.setError(getContext().getString(R.string.error_description_required));
            esValido = false;
        }
        if (esGasto && TextUtils.isEmpty(categoria)) {
            categoryLayout.setError(getContext().getString(R.string.error_category_required));
            esValido = false;
        }
        if (TextUtils.isEmpty(selectedPaymentMethod)) {
            paymentLayout.setError(getContext().getString(R.string.error_payment_method_required));
            esValido = false;
        } else if (compatibleAccounts.isEmpty()) {
            accountLayout.setError(getContext().getString(R.string.error_no_compatible_account));
            esValido = false;
        } else if (selectedAccountId == Cuenta.ID_TODAS_LAS_CUENTAS) {
            accountLayout.setError(getContext().getString(R.string.error_account_required));
            esValido = false;
        } else if (!esCuentaCompatibleSeleccionada()) {
            accountLayout.setError(getContext().getString(R.string.error_account_incompatible));
            esValido = false;
        }
        if (Movimiento.MEDIO_OTRO.equals(selectedPaymentMethod)
                && TextUtils.isEmpty(getText(otherPaymentInput).trim())) {
            otherPaymentLayout.setError(getContext().getString(R.string.error_other_payment_required));
            esValido = false;
        }

        long monto = 0;
        try {
            monto = Long.parseLong(montoTexto);
            if (monto <= 0) {
                amountLayout.setError(getContext().getString(R.string.error_amount_positive));
                esValido = false;
            }
        } catch (NumberFormatException exception) {
            amountLayout.setError(getContext().getString(R.string.error_amount_valid));
            esValido = false;
        }

        if (!esValido) {
            return;
        }

        String tipo = esGasto
                ? Movimiento.TIPO_GASTO
                : Movimiento.TIPO_INGRESO;
        String medioPersistido = Movimiento.MEDIO_OTRO.equals(selectedPaymentMethod)
                ? Movimiento.MEDIO_OTRO + ": " + getText(otherPaymentInput).trim()
                : selectedPaymentMethod;
        listener.onMovimientoConfirmado(new Movimiento(
                0,
                selectedAccountId,
                tipo,
                descripcion,
                monto,
                categoria,
                System.currentTimeMillis(), null, latitude, longitude, null, medioPersistido));
        dismiss();
    }

    private void capturarUbicacion() {
        if (!tienePermisoUbicacion()) {
            if (permisoUbicacionListener != null) {
                locationStatus.setText(R.string.location_permission_requesting);
                permisoUbicacionListener.onPermisoUbicacionSolicitado();
            } else {
                locationStatus.setText(R.string.location_permission_required);
            }
            return;
        }
        LocationManager manager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                && !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))) {
            locationStatus.setText(R.string.location_services_disabled);
            return;
        }
        Location location;
        try {
            location = manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) : null;
            if (location == null && manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException exception) {
            locationStatus.setText(R.string.location_permission_required);
            return;
        }
        if (location == null) {
            locationStatus.setText(R.string.location_unavailable);
            return;
        }
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        mostrarCoordenadasGuardadas();
    }

    public void onResultadoPermisoUbicacion(boolean concedido) {
        if (!isShowing()) {
            return;
        }
        if (!concedido) {
            locationStatus.setText(R.string.location_permission_denied);
            return;
        }
        capturarUbicacion();
    }

    /** Recibe un punto confirmado por MapLibre sin sustituirlo por el GPS del dispositivo. */
    public void onUbicacionManualSeleccionada(double nuevaLatitud, double nuevaLongitud) {
        if (!isShowing()) {
            return;
        }
        latitude = nuevaLatitud;
        longitude = nuevaLongitud;
        mostrarCoordenadasGuardadas();
    }

    private void solicitarUbicacionManual() {
        if (ubicacionManualListener != null) {
            ubicacionManualListener.onUbicacionManualSolicitada(latitude, longitude);
        }
    }

    private void limpiarUbicacion() {
        latitude = null;
        longitude = null;
        locationStatus.setText(R.string.location_optional);
    }

    private boolean tienePermisoUbicacion() {
        return getContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || getContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void mostrarCoordenadasGuardadas() {
        locationStatus.setText(getContext().getString(
                R.string.location_saved_format, latitude, longitude));
    }

    private String getText(TextView view) {
        return view.getText() == null ? "" : view.getText().toString();
    }

    private void clearErrors() {
        descriptionLayout.setError(null);
        amountLayout.setError(null);
        categoryLayout.setError(null);
        accountLayout.setError(null);
        paymentLayout.setError(null);
        otherPaymentLayout.setError(null);
    }

    private void actualizarFormularioPorTipo() {
        boolean esGasto = typeToggleGroup.getCheckedButtonId() == R.id.button_expense;
        actualizarEtiquetaMedio();
        categoryLayout.setVisibility(esGasto ? View.VISIBLE : View.GONE);
        if (!esGasto) {
            categoryInput.setText("", false);
            categoryLayout.setError(null);
        }
    }

    private void actualizarCampoOtro() {
        boolean esOtro = Movimiento.MEDIO_OTRO.equals(selectedPaymentMethod);
        otherPaymentLayout.setVisibility(esOtro ? View.VISIBLE : View.GONE);
        if (!esOtro) {
            otherPaymentInput.setText("");
            otherPaymentLayout.setError(null);
        }
    }

    private void alternarOpcionales() {
        boolean mostrar = optionalSection.getVisibility() != View.VISIBLE;
        optionalSection.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        ((android.widget.TextView) findViewById(R.id.button_toggle_optional)).setText(
                mostrar ? R.string.action_hide_optional : R.string.action_show_optional);
    }

    private boolean esCuentaCompatibleSeleccionada() {
        for (Cuenta cuenta : cuentas) {
            if (cuenta.getId() == selectedAccountId) {
                return esCuentaCompatible(cuenta, selectedPaymentMethod);
            }
        }
        return false;
    }

    private void actualizarEtiquetaMedio() {
        boolean esGasto = typeToggleGroup.getCheckedButtonId() == R.id.button_expense;
        paymentLayout.setHint(esGasto ? R.string.form_payment_method_label
                : R.string.form_collection_method_label);
        paymentLayout.setHelperText(getContext().getString(esGasto
                ? R.string.form_payment_method_hint : R.string.form_collection_method_hint));
    }

    /** Muestra únicamente las cuentas que pueden respaldar el medio seleccionado. */
    private void actualizarCuentasCompatibles(Cuenta cuentaPreferida) {
        if (TextUtils.isEmpty(selectedPaymentMethod)) {
            associatedAccountContainer.setVisibility(View.GONE);
            selectedAccountId = Cuenta.ID_TODAS_LAS_CUENTAS;
            accountInput.setText("", false);
            return;
        }
        associatedAccountContainer.setVisibility(View.VISIBLE);
        boolean esEfectivo = Movimiento.MEDIO_EFECTIVO.equals(selectedPaymentMethod);
        accountLayout.setHint(esEfectivo ? R.string.form_cash_account_label
                : R.string.form_associated_account_label);
        accountLayout.setHelperText(getContext().getString(esEfectivo
                ? R.string.form_cash_account_hint : R.string.form_associated_account_hint));

        long cuentaAnterior = selectedAccountId;
        compatibleAccounts.clear();
        for (Cuenta cuenta : cuentas) {
            if (esCuentaCompatible(cuenta, selectedPaymentMethod)) {
                compatibleAccounts.add(cuenta);
            }
        }
        accountAdapter.notifyDataSetChanged();
        selectedAccountId = Cuenta.ID_TODAS_LAS_CUENTAS;
        accountInput.setText("", false);
        if (compatibleAccounts.isEmpty()) {
            accountLayout.setError(getContext().getString(R.string.error_no_compatible_account));
            return;
        }
        accountLayout.setError(null);
        Cuenta seleccionable = cuentaPreferida != null
                && esCuentaCompatible(cuentaPreferida, selectedPaymentMethod) ? cuentaPreferida : null;
        if (seleccionable == null) {
            for (Cuenta cuenta : compatibleAccounts) {
                if (cuenta.getId() == cuentaAnterior) {
                    seleccionable = cuenta;
                    break;
                }
            }
        }
        if (seleccionable == null && compatibleAccounts.size() == 1) {
            seleccionable = compatibleAccounts.get(0);
        }
        if (seleccionable != null) {
            selectedAccountId = seleccionable.getId();
            accountInput.setText(seleccionable.getNombre(), false);
        }
    }

    private static boolean esCuentaCompatible(Cuenta cuenta, String medio) {
        if (Movimiento.MEDIO_EFECTIVO.equals(medio)) {
            return Cuenta.TIPO_EFECTIVO.equals(cuenta.getTipo());
        }
        if (Movimiento.MEDIO_TARJETA_CREDITO.equals(medio)) {
            return Cuenta.TIPO_TARJETA_CREDITO.equals(cuenta.getTipo());
        }
        if (Movimiento.MEDIO_TARJETA_DEBITO.equals(medio)
                || Movimiento.MEDIO_TRANSFERENCIA.equals(medio)) {
            return Cuenta.TIPO_CUENTA_CORRIENTE.equals(cuenta.getTipo())
                    || Cuenta.TIPO_CUENTA_VISTA.equals(cuenta.getTipo())
                    || Cuenta.TIPO_AHORROS.equals(cuenta.getTipo())
                    || Cuenta.TIPO_CUENTA_DIGITAL.equals(cuenta.getTipo());
        }
        if (Movimiento.MEDIO_CHEQUE.equals(medio)) {
            return Cuenta.TIPO_CUENTA_CORRIENTE.equals(cuenta.getTipo());
        }
        if (Movimiento.MEDIO_CUENTA_DIGITAL.equals(medio)) {
            return Cuenta.TIPO_CUENTA_DIGITAL.equals(cuenta.getTipo());
        }
        return true;
    }

    private static String mapearMedioPago(int position) {
        switch (position) {
            case 0:
                return Movimiento.MEDIO_EFECTIVO;
            case 1:
                return Movimiento.MEDIO_TARJETA_DEBITO;
            case 2:
                return Movimiento.MEDIO_TARJETA_CREDITO;
            case 3:
                return Movimiento.MEDIO_TRANSFERENCIA;
            case 4:
                return Movimiento.MEDIO_CHEQUE;
            case 5:
                return Movimiento.MEDIO_CUENTA_DIGITAL;
            default:
                return Movimiento.MEDIO_OTRO;
        }
    }
}

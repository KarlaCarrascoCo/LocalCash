package com.localcash.app.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.localcash.app.R;
import com.localcash.app.model.Cuenta;

/** Permite crear una cuenta local sin tocar datos de otras cuentas. */
public final class CuentaFormDialog extends Dialog {

    public interface OnCuentaConfirmadaListener {
        void onCuentaConfirmada(Cuenta cuenta);
    }

    private final OnCuentaConfirmadaListener listener;
    private TextInputLayout nameLayout;
    private TextInputLayout typeLayout;
    private TextInputEditText nameInput;
    private TextInputEditText entityInput;
    private AutoCompleteTextView typeInput;
    private String selectedType;

    public CuentaFormDialog(@NonNull Context context, @NonNull OnCuentaConfirmadaListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_registrar_cuenta);

        nameLayout = findViewById(R.id.input_layout_account_name);
        typeLayout = findViewById(R.id.input_layout_account_type);
        nameInput = findViewById(R.id.input_account_name);
        entityInput = findViewById(R.id.input_account_entity);
        typeInput = findViewById(R.id.input_account_type);
        configurarTipos();

        findViewById(R.id.button_cancel_account).setOnClickListener(view -> dismiss());
        findViewById(R.id.button_save_account).setOnClickListener(view -> validarYConfirmar());

        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void configurarTipos() {
        String[] tipos = getContext().getResources().getStringArray(R.array.account_types);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_list_item_1, tipos);
        typeInput.setAdapter(adapter);
        typeInput.setKeyListener(null);
        typeInput.setOnClickListener(view -> typeInput.showDropDown());
        typeInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                typeInput.showDropDown();
            }
        });
        typeInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedType = mapearTipo(position);
            typeLayout.setError(null);
        });
    }

    private void validarYConfirmar() {
        String nombre = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
        nameLayout.setError(null);
        typeLayout.setError(null);
        boolean valido = true;
        if (TextUtils.isEmpty(nombre)) {
            nameLayout.setError(getContext().getString(R.string.error_account_name_required));
            valido = false;
        }
        if (selectedType == null) {
            typeLayout.setError(getContext().getString(R.string.error_account_type_required));
            valido = false;
        }
        if (valido) {
            String entidad = entityInput.getText() == null ? "" : entityInput.getText().toString().trim();
            listener.onCuentaConfirmada(new Cuenta(0, nombre, selectedType, entidad));
            dismiss();
        }
    }

    private String mapearTipo(int position) {
        switch (position) {
            case 0:
                return Cuenta.TIPO_EFECTIVO;
            case 1:
                return Cuenta.TIPO_CUENTA_CORRIENTE;
            case 2:
                return Cuenta.TIPO_CUENTA_VISTA;
            case 3:
                return Cuenta.TIPO_AHORROS;
            case 4:
                return Cuenta.TIPO_TARJETA_CREDITO;
            case 5:
                return Cuenta.TIPO_CUENTA_DIGITAL;
            default:
                return Cuenta.TIPO_OTRA;
        }
    }
}

package com.localcash.app;

import android.Manifest;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.localcash.app.data.CuentaRepository;
import com.localcash.app.data.DatabaseBackupManager;
import com.localcash.app.data.DatabaseHelper;
import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.model.Cuenta;
import com.localcash.app.model.Movimiento;
import com.localcash.app.model.ResumenFinanciero;
import com.localcash.app.ui.CuentaFormDialog;
import com.localcash.app.ui.MovimientoFormDialog;
import com.localcash.app.security.AuthSession;

import java.io.IOException;
import java.io.File;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_ACCOUNT_FORM = "open_account_form";
    private static final String TAG = "LocalCash";
    private static final int MAX_RECENT_MOVEMENTS = 8;
    private static final String DATABASE_STATE_PREFERENCES = "localcash_database_state";
    private static final String DATABASE_INITIALIZED_KEY = "database_initialized";

    private final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CL"));
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, new Locale("es", "CL"));

    private MovimientoRepository movimientoRepository;
    private CuentaRepository cuentaRepository;
    private TextView balanceTotal;
    private TextView incomeTotal;
    private TextView expenseTotal;
    private LinearLayout recentMovementsContainer;
    private MaterialAutoCompleteTextView accountFilterInput;
    private MaterialButton registerMovementButton;
    private ScrollView homeScroll;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private MovimientoFormDialog movimientoFormDialog;
    private List<Cuenta> cuentas = new ArrayList<>();
    private long selectedAccountId = Cuenta.ID_TODAS_LAS_CUENTAS;
    private boolean openAccountFormOnReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthSession.isAuthenticated()) {
            volverABienvenida();
            return;
        }
        openAccountFormOnReady = getIntent().getBooleanExtra(EXTRA_OPEN_ACCOUNT_FORM, false);
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                    boolean concedido = Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_FINE_LOCATION))
                            || Boolean.TRUE.equals(permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (movimientoFormDialog != null) {
                        movimientoFormDialog.onResultadoPermisoUbicacion(concedido);
                    }
                });
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        balanceTotal = findViewById(R.id.text_current_balance);
        incomeTotal = findViewById(R.id.text_income_total);
        expenseTotal = findViewById(R.id.text_expense_total);
        recentMovementsContainer = findViewById(R.id.recent_movements_container);
        accountFilterInput = findViewById(R.id.input_account_filter);
        registerMovementButton = findViewById(R.id.button_register_movement);
        homeScroll = findViewById(R.id.home_scroll);
        registerMovementButton.setEnabled(false);

        registerMovementButton.setOnClickListener(view -> abrirFormularioMovimiento());
        findViewById(R.id.button_settings).setOnClickListener(view -> abrirFormularioCuenta());
        findViewById(R.id.button_quick_accounts).setOnClickListener(view -> abrirFormularioCuenta());
        findViewById(R.id.button_quick_shopping).setOnClickListener(view -> abrirModulo(ComprasActivity.class));
        findViewById(R.id.button_quick_contacts).setOnClickListener(view -> abrirModulo(ContactosActivity.class));
        findViewById(R.id.button_quick_shared).setOnClickListener(view -> abrirModulo(GastosCompartidosActivity.class));
        findViewById(R.id.button_quick_map).setOnClickListener(view -> abrirModulo(MapActivity.class));
        findViewById(R.id.button_quick_history).setOnClickListener(view -> mostrarHistorial());
        configurarNavegacion();
        inicializarPersistencia();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AuthSession.isAuthenticated()) {
            volverABienvenida();
            return;
        }
        if (movimientoRepository != null) {
            cargarPanel();
        }
    }
    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !isFinishing()) {
            AuthSession.clear();
        }
    }

    private void inicializarPersistencia() {
        databaseExecutor.execute(() -> {
            try {
                comprobarBaseEsperada();
                DatabaseBackupManager.BackupResult backup = DatabaseBackupManager.createVerifiedBackup(this);
                if (backup.isBackupCreated()) {
                    Log.i(TAG, "Copia verificada: " + backup.getBackupFile().getAbsolutePath());
                }

                MovimientoRepository nuevoMovimientoRepository = new MovimientoRepository(this);
                CuentaRepository nuevoCuentaRepository = new CuentaRepository(this);
                List<Cuenta> cuentasDisponibles = nuevoCuentaRepository.obtenerTodas();
                ResumenFinanciero resumen = nuevoMovimientoRepository.obtenerResumen();
                List<Movimiento> movimientos = nuevoMovimientoRepository.obtenerRecientes(MAX_RECENT_MOVEMENTS);
                marcarBaseInicializada();

                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    movimientoRepository = nuevoMovimientoRepository;
                    cuentaRepository = nuevoCuentaRepository;
                    cuentas = cuentasDisponibles;
                    configurarSelectorDeCuentas();
                    registerMovementButton.setEnabled(true);
                    renderPanel(resumen, movimientos);
                    if (openAccountFormOnReady) {
                        openAccountFormOnReady = false;
                        abrirFormularioCuenta();
                    }
                });
            } catch (IOException | RuntimeException exception) {
                Log.e(TAG, "No fue posible inicializar la base local.", exception);
                mainHandler.post(this::mostrarErrorInicializacion);
            }
        });
    }

    private void configurarNavegacion() {
        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_home) {
                homeScroll.smoothScrollTo(0, 0);
            } else if (item.getItemId() == R.id.navigation_movements) {
                mostrarHistorial();
            } else if (item.getItemId() == R.id.navigation_shopping) {
                abrirModulo(ComprasActivity.class);
            } else if (item.getItemId() == R.id.navigation_more) {
                abrirModulo(MasActivity.class);
            }
            return true;
        });
    }

    private void abrirModulo(Class<?> activityClass) {
        startActivity(new Intent(this, activityClass));
    }

    private void mostrarHistorial() {
        View historial = findViewById(R.id.section_recent_movements);
        homeScroll.post(() -> homeScroll.smoothScrollTo(0, historial.getTop()));
    }

    private void volverABienvenida() {
        startActivity(new Intent(this, WelcomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void abrirFormularioMovimiento() {
        if (movimientoRepository == null || cuentas.isEmpty()) {
            return;
        }
        MovimientoFormDialog dialog = new MovimientoFormDialog(
                this,
                new ArrayList<>(cuentas),
                this::guardarMovimiento,
                this::abrirFormularioCuentaDesdeMovimiento,
                this::solicitarPermisoUbicacion);
        dialog.setOnDismissListener(ignored -> {
            if (movimientoFormDialog == dialog) {
                movimientoFormDialog = null;
            }
        });
        movimientoFormDialog = dialog;
        dialog.show();
    }

    private void abrirFormularioCuenta() {
        if (cuentaRepository != null) {
            new CuentaFormDialog(this, this::guardarCuenta).show();
        }
    }

    private void abrirFormularioCuentaDesdeMovimiento() {
        if (cuentaRepository != null) {
            new CuentaFormDialog(this, this::guardarCuentaDesdeMovimiento).show();
        }
    }

    private void solicitarPermisoUbicacion() {
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void guardarMovimiento(Movimiento movimiento) {
        databaseExecutor.execute(() -> {
            try {
                movimientoRepository.insertar(movimiento);
                mainHandler.post(this::cargarPanel);
            } catch (RuntimeException exception) {
                Log.e(TAG, "No fue posible guardar el movimiento.", exception);
                mainHandler.post(() -> mostrarErrorOperacion(R.string.error_movement_save));
            }
        });
    }

    private void guardarCuenta(Cuenta cuenta) {
        databaseExecutor.execute(() -> {
            try {
                cuentaRepository.crear(cuenta);
                mainHandler.post(this::recargarCuentasYPanel);
            } catch (RuntimeException exception) {
                Log.e(TAG, "No fue posible crear la cuenta.", exception);
                mainHandler.post(() -> Toast.makeText(this,
                        R.string.error_account_create, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void guardarCuentaDesdeMovimiento(Cuenta cuenta) {
        databaseExecutor.execute(() -> {
            try {
                long cuentaId = cuentaRepository.crear(cuenta);
                Cuenta cuentaCreada = new Cuenta(cuentaId, cuenta.getNombre(), cuenta.getTipo());
                List<Cuenta> cuentasActualizadas = cuentaRepository.obtenerTodas();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    cuentas = cuentasActualizadas;
                    configurarSelectorDeCuentas();
                    if (movimientoFormDialog != null) {
                        movimientoFormDialog.actualizarCuentas(cuentasActualizadas, cuentaCreada);
                    }
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "No fue posible crear la cuenta desde el formulario.", exception);
                mainHandler.post(() -> Toast.makeText(this,
                        R.string.error_account_create, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void eliminarMovimiento(Movimiento movimiento) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_movement_title)
                .setMessage(R.string.delete_movement_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> databaseExecutor.execute(() -> {
                    try {
                        movimientoRepository.eliminar(movimiento.getId());
                        mainHandler.post(this::cargarPanel);
                    } catch (RuntimeException exception) {
                        Log.e(TAG, "No fue posible eliminar el movimiento.", exception);
                        mainHandler.post(() -> mostrarErrorOperacion(R.string.error_movement_delete));
                    }
                }))
                .show();
    }

    private void recargarCuentasYPanel() {
        if (cuentaRepository == null || movimientoRepository == null) {
            return;
        }
        final long cuentaAnterior = selectedAccountId;
        databaseExecutor.execute(() -> {
            try {
                List<Cuenta> cuentasActualizadas = cuentaRepository.obtenerTodas();
                long cuentaActiva = contieneCuenta(cuentasActualizadas, cuentaAnterior)
                        ? cuentaAnterior : Cuenta.ID_TODAS_LAS_CUENTAS;
                ResumenFinanciero resumen = movimientoRepository.obtenerResumenPorCuenta(cuentaActiva);
                List<Movimiento> movimientos = movimientoRepository.obtenerRecientesPorCuenta(
                        cuentaActiva, MAX_RECENT_MOVEMENTS);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    cuentas = cuentasActualizadas;
                    selectedAccountId = cuentaActiva;
                    configurarSelectorDeCuentas();
                    renderPanel(resumen, movimientos);
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "No fue posible recargar las cuentas.", exception);
                mainHandler.post(() -> mostrarErrorOperacion(R.string.error_database_operation));
            }
        });
    }

    private boolean contieneCuenta(List<Cuenta> lista, long cuentaId) {
        if (cuentaId == Cuenta.ID_TODAS_LAS_CUENTAS) {
            return true;
        }
        for (Cuenta cuenta : lista) {
            if (cuenta.getId() == cuentaId) {
                return true;
            }
        }
        return false;
    }

    private void configurarSelectorDeCuentas() {
        List<String> etiquetas = new ArrayList<>();
        etiquetas.add(getString(R.string.account_filter_all));
        int posicionSeleccionada = 0;
        for (int index = 0; index < cuentas.size(); index++) {
            Cuenta cuenta = cuentas.get(index);
            etiquetas.add(cuenta.getNombre());
            if (cuenta.getId() == selectedAccountId) {
                posicionSeleccionada = index + 1;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, etiquetas);
        accountFilterInput.setAdapter(adapter);
        accountFilterInput.setKeyListener(null);
        accountFilterInput.setText(etiquetas.get(posicionSeleccionada), false);
        accountFilterInput.setOnClickListener(view -> accountFilterInput.showDropDown());
        accountFilterInput.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                accountFilterInput.showDropDown();
            }
        });
        accountFilterInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedAccountId = position == 0
                    ? Cuenta.ID_TODAS_LAS_CUENTAS
                    : cuentas.get(position - 1).getId();
            cargarPanel();
        });
    }

    private void cargarPanel() {
        if (movimientoRepository == null) {
            return;
        }
        final long cuentaId = selectedAccountId;
        databaseExecutor.execute(() -> {
            try {
                ResumenFinanciero resumen = movimientoRepository.obtenerResumenPorCuenta(cuentaId);
                List<Movimiento> recientes = movimientoRepository.obtenerRecientesPorCuenta(
                        cuentaId, MAX_RECENT_MOVEMENTS);
                mainHandler.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        renderPanel(resumen, recientes);
                    }
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "No fue posible cargar los movimientos.", exception);
                mainHandler.post(() -> mostrarErrorOperacion(R.string.error_database_operation));
            }
        });
    }

    private void renderPanel(ResumenFinanciero resumen, List<Movimiento> movimientos) {
        balanceTotal.setText(formatAmount(resumen.getSaldo()));
        incomeTotal.setText(formatAmount(resumen.getIngresos()));
        expenseTotal.setText(formatAmount(resumen.getGastos()));
        recentMovementsContainer.removeAllViews();

        if (movimientos.isEmpty()) {
            getLayoutInflater().inflate(R.layout.item_sin_movimientos, recentMovementsContainer, true);
            return;
        }

        LayoutInflater inflater = getLayoutInflater();
        for (Movimiento movimiento : movimientos) {
            View itemView = inflater.inflate(R.layout.item_movimiento, recentMovementsContainer, false);
            bindMovimiento(itemView, movimiento);
            recentMovementsContainer.addView(itemView);
        }
    }

    private void bindMovimiento(View itemView, Movimiento movimiento) {
        boolean esIngreso = Movimiento.TIPO_INGRESO.equals(movimiento.getTipo());
        ImageView icon = itemView.findViewById(R.id.image_movement_icon);
        TextView title = itemView.findViewById(R.id.text_movement_title);
        TextView detail = itemView.findViewById(R.id.text_movement_detail);
        TextView location = itemView.findViewById(R.id.text_movement_location);
        TextView amount = itemView.findViewById(R.id.text_movement_amount);

        title.setText(movimiento.getDescripcion());
        detail.setText(getString(R.string.movement_detail_with_account_format,
                movimiento.getCategoria(), dateFormat.format(movimiento.getFecha()), movimiento.getNombreCuenta()));
        if (movimiento.getDireccionAproximada() == null
                || movimiento.getDireccionAproximada().trim().isEmpty()) {
            location.setVisibility(View.GONE);
        } else {
            location.setText(getString(R.string.location_approximate_address_format,
                    movimiento.getDireccionAproximada()));
            location.setVisibility(View.VISIBLE);
        }
        amount.setText(getString(esIngreso ? R.string.amount_income_format : R.string.amount_expense_format,
                formatAmount(movimiento.getMonto())));
        icon.setImageResource(esIngreso ? R.drawable.ic_wallet : R.drawable.ic_arrow_downward);
        icon.setBackgroundResource(esIngreso ? R.drawable.bg_income_icon : R.drawable.bg_expense_icon);
        icon.setImageTintList(getColorStateList(esIngreso ? R.color.lc_income : R.color.lc_expense));
        amount.setTextColor(getColor(esIngreso ? R.color.lc_income : R.color.lc_expense));
        itemView.setOnLongClickListener(view -> {
            eliminarMovimiento(movimiento);
            return true;
        });
    }

    private String formatAmount(long amount) {
        return currencyFormat.format(amount);
    }

    private void mostrarErrorInicializacion() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.database_initialization_error_title)
                .setMessage(R.string.database_initialization_error_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void comprobarBaseEsperada() {
        File databaseFile = getDatabasePath(DatabaseHelper.DATABASE_NAME);
        SharedPreferences preferences = getSharedPreferences(
                DATABASE_STATE_PREFERENCES, MODE_PRIVATE);
        if (preferences.getBoolean(DATABASE_INITIALIZED_KEY, false) && !databaseFile.exists()) {
            throw new IllegalStateException("La base de datos local esperada no está disponible.");
        }
    }

    private void marcarBaseInicializada() {
        getSharedPreferences(DATABASE_STATE_PREFERENCES, MODE_PRIVATE)
                .edit()
                .putBoolean(DATABASE_INITIALIZED_KEY, true)
                .apply();
    }

    private void mostrarErrorOperacion(int messageResource) {
        if (!isFinishing() && !isDestroyed()) {
            Toast.makeText(this, messageResource, Toast.LENGTH_LONG).show();
        }
    }
}

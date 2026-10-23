package com.localcash.app.data;

import android.content.Context;
import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.localcash.app.model.Cuenta;
import com.localcash.app.model.Movimiento;

/** Crea y versiona la base de datos privada de LocalCash. */
public final class DatabaseHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "localcash.db";
    public static final int DATABASE_VERSION = 6;
    public static final String TABLE_CUENTAS = "cuentas";
    public static final String TABLE_MOVIMIENTOS = "movimientos";
    public static final String TABLE_COMPRAS = "compras";
    public static final String TABLE_CONTACTOS = "contactos";
    public static final String TABLE_GASTOS_COMPARTIDOS = "gastos_compartidos";

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_CUENTA_ID = "cuenta_id";
    public static final String COLUMN_CUENTA_NOMBRE = "nombre";
    public static final String COLUMN_CUENTA_TIPO = "tipo";
    public static final String COLUMN_CUENTA_ENTIDAD = "entidad_financiera";
    public static final String COLUMN_TIPO = "tipo";
    public static final String COLUMN_DESCRIPCION = "descripcion";
    public static final String COLUMN_MONTO = "monto";
    public static final String COLUMN_CATEGORIA = "categoria";
    public static final String COLUMN_FECHA = "fecha";
    public static final String COLUMN_LATITUD = "latitud";
    public static final String COLUMN_LONGITUD = "longitud";
    public static final String COLUMN_DIRECCION_APROXIMADA = "direccion_aproximada";
    public static final String COLUMN_MEDIO_PAGO = "medio_pago";
    public static final String COLUMN_PRECIO_ESTIMADO = "precio_estimado";
    public static final String COLUMN_PAGADO = "pagado";

    private static final String TABLE_MOVIMIENTOS_MIGRACION = "movimientos_v2";
    private static final String MOVEMENT_DATA_COLUMNS = COLUMN_ID + ", " + COLUMN_TIPO + ", "
            + COLUMN_DESCRIPCION + ", " + COLUMN_MONTO + ", " + COLUMN_CATEGORIA + ", "
            + COLUMN_FECHA;
    private static final String MOVEMENT_V3_DATA_COLUMNS = MOVEMENT_DATA_COLUMNS + ", "
            + COLUMN_LATITUD + ", " + COLUMN_LONGITUD + ", " + COLUMN_CUENTA_ID;

    private static String createCuentasTable(String tableName, boolean incluyeEntidadFinanciera) {
        return "CREATE TABLE " + tableName + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_CUENTA_NOMBRE + " TEXT NOT NULL COLLATE NOCASE UNIQUE, "
            + COLUMN_CUENTA_TIPO + " TEXT NOT NULL CHECK(" + COLUMN_CUENTA_TIPO + " IN ('"
            + Cuenta.TIPO_EFECTIVO + "', '" + Cuenta.TIPO_CUENTA_CORRIENTE + "', '"
            + Cuenta.TIPO_CUENTA_VISTA + "', '" + Cuenta.TIPO_AHORROS + "', '"
            + Cuenta.TIPO_TARJETA_CREDITO + "', '" + Cuenta.TIPO_CUENTA_DIGITAL + "', '"
            + Cuenta.TIPO_OTRA + "'))"
            + (incluyeEntidadFinanciera ? ", " + COLUMN_CUENTA_ENTIDAD + " TEXT" : "")
            + ")";
    }

    private static final String CREATE_CUENTAS_TABLE = createCuentasTable(TABLE_CUENTAS, true);

    private static String createMovimientosTable(String tableName, boolean incluyeUbicacion,
                                                  boolean incluyeDireccionAproximada,
                                                  boolean incluyeMedioPago) {
        return createMovimientosTable(tableName, incluyeUbicacion, incluyeDireccionAproximada,
                incluyeMedioPago, TABLE_CUENTAS);
    }

    private static String createMovimientosTable(String tableName, boolean incluyeUbicacion,
                                                  boolean incluyeDireccionAproximada,
                                                  boolean incluyeMedioPago,
                                                  String tablaCuentas) {
        return "CREATE TABLE " + tableName + " ("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + COLUMN_TIPO + " TEXT NOT NULL CHECK(" + COLUMN_TIPO + " IN ('INGRESO', 'GASTO')), "
            + COLUMN_DESCRIPCION + " TEXT NOT NULL, "
            + COLUMN_MONTO + " INTEGER NOT NULL CHECK(" + COLUMN_MONTO + " > 0), "
            + COLUMN_CATEGORIA + " TEXT NOT NULL, "
            + COLUMN_FECHA + " INTEGER NOT NULL, "
            + (incluyeUbicacion ? COLUMN_LATITUD + " REAL, " + COLUMN_LONGITUD + " REAL, " : "")
            + (incluyeDireccionAproximada ? COLUMN_DIRECCION_APROXIMADA + " TEXT, " : "")
            + (incluyeMedioPago ? COLUMN_MEDIO_PAGO + " TEXT NOT NULL, " : "")
            + COLUMN_CUENTA_ID + " INTEGER NOT NULL, "
            + "FOREIGN KEY(" + COLUMN_CUENTA_ID + ") REFERENCES " + tablaCuentas
            + "(" + COLUMN_ID + ") ON DELETE RESTRICT"
            + ")";
    }

    public DatabaseHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    /** Constructor con nombre alternativo para pruebas aisladas. */
    public DatabaseHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase database) {
        super.onConfigure(database);
        database.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(CREATE_CUENTAS_TABLE);
        crearCuenta(database, "Efectivo", Cuenta.TIPO_EFECTIVO, null, true);
        database.execSQL(createMovimientosTable(TABLE_MOVIMIENTOS, true, true, true));
        crearIndicesMovimientos(database);
        crearTablasComplementarias(database, true);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            migrarVersion1AVersion2(database);
        }
        if (oldVersion < 3) {
            migrarVersion2AVersion3(database);
        }
        if (oldVersion < 4) {
            migrarVersion3AVersion4(database);
        }
        if (oldVersion < 5) {
            migrarVersion4AVersion5(database);
        }
        if (oldVersion < 6) {
            migrarVersion5AVersion6(database);
        }
    }

    private void migrarVersion1AVersion2(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            database.execSQL(createCuentasTable(TABLE_CUENTAS, false));
            long cuentaHistoricaId = crearCuenta(database, "Cuenta inicial", Cuenta.TIPO_OTRA,
                    null, false);
            long cantidadOriginal = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + TABLE_MOVIMIENTOS, null);

            database.execSQL(createMovimientosTable(TABLE_MOVIMIENTOS_MIGRACION, false, false, false));
            database.execSQL("INSERT INTO " + TABLE_MOVIMIENTOS_MIGRACION + " ("
                            + COLUMN_ID + ", " + COLUMN_TIPO + ", " + COLUMN_DESCRIPCION + ", "
                            + COLUMN_MONTO + ", " + COLUMN_CATEGORIA + ", " + COLUMN_FECHA + ", "
                            + COLUMN_CUENTA_ID + ") SELECT "
                            + COLUMN_ID + ", " + COLUMN_TIPO + ", " + COLUMN_DESCRIPCION + ", "
                            + COLUMN_MONTO + ", " + COLUMN_CATEGORIA + ", " + COLUMN_FECHA + ", ? FROM "
                            + TABLE_MOVIMIENTOS,
                    new Object[]{cuentaHistoricaId});

            long cantidadMigrada = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + TABLE_MOVIMIENTOS_MIGRACION, null);
            if (cantidadOriginal != cantidadMigrada) {
                throw new IllegalStateException("La migración no conservó todos los movimientos.");
            }
            verificarDatosMigrados(database);

            database.execSQL("DROP TABLE " + TABLE_MOVIMIENTOS);
            database.execSQL("ALTER TABLE " + TABLE_MOVIMIENTOS_MIGRACION + " RENAME TO "
                    + TABLE_MOVIMIENTOS);
            crearIndicesMovimientos(database);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private void migrarVersion2AVersion3(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            database.execSQL("ALTER TABLE " + TABLE_MOVIMIENTOS + " ADD COLUMN "
                    + COLUMN_LATITUD + " REAL");
            database.execSQL("ALTER TABLE " + TABLE_MOVIMIENTOS + " ADD COLUMN "
                    + COLUMN_LONGITUD + " REAL");
            crearTablasComplementarias(database, false);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Conserva cuentas y movimientos al ampliar los tipos y añadir dirección opcional. */
    private void migrarVersion3AVersion4(SQLiteDatabase database) {
        final String tablaCuentasNueva = "cuentas_v4_nueva";
        final String tablaMovimientosNueva = "movimientos_v4_nueva";
        database.beginTransaction();
        try {
            long cuentasOriginales = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + TABLE_CUENTAS, null);
            long movimientosOriginales = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + TABLE_MOVIMIENTOS, null);
            database.execSQL(createCuentasTable(tablaCuentasNueva, false));
            database.execSQL("INSERT INTO " + tablaCuentasNueva + " (" + COLUMN_ID + ", "
                    + COLUMN_CUENTA_NOMBRE + ", " + COLUMN_CUENTA_TIPO + ") SELECT "
                    + COLUMN_ID + ", " + COLUMN_CUENTA_NOMBRE + ", " + COLUMN_CUENTA_TIPO
                    + " FROM " + TABLE_CUENTAS);
            long cuentasCopiadas = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + tablaCuentasNueva, null);
            if (cuentasOriginales != cuentasCopiadas) {
                throw new IllegalStateException("La migración no conservó todas las cuentas.");
            }
            verificarCuentasMigradas(database, tablaCuentasNueva);

            database.execSQL(createMovimientosTable(tablaMovimientosNueva, true, true, false,
                    tablaCuentasNueva));
            database.execSQL("INSERT INTO " + tablaMovimientosNueva + " ("
                    + COLUMN_ID + ", " + COLUMN_TIPO + ", " + COLUMN_DESCRIPCION + ", "
                    + COLUMN_MONTO + ", " + COLUMN_CATEGORIA + ", " + COLUMN_FECHA + ", "
                    + COLUMN_LATITUD + ", " + COLUMN_LONGITUD + ", " + COLUMN_CUENTA_ID + ") SELECT "
                    + MOVEMENT_V3_DATA_COLUMNS + " FROM " + TABLE_MOVIMIENTOS);
            long movimientosCopiados = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + tablaMovimientosNueva, null);
            if (movimientosOriginales != movimientosCopiados) {
                throw new IllegalStateException("La migración no conservó todos los movimientos.");
            }
            verificarMovimientosVersionTresMigrados(database, tablaMovimientosNueva);

            // Se elimina el hijo antes del padre; las tablas nuevas ya conservan la relación válida.
            database.execSQL("DROP TABLE " + TABLE_MOVIMIENTOS);
            database.execSQL("DROP TABLE " + TABLE_CUENTAS);
            database.execSQL("ALTER TABLE " + tablaCuentasNueva + " RENAME TO " + TABLE_CUENTAS);
            database.execSQL("ALTER TABLE " + tablaMovimientosNueva + " RENAME TO "
                    + TABLE_MOVIMIENTOS);
            verificarRelacionesDeCuentas(database);
            verificarClavesForaneas(database);
            crearIndicesMovimientos(database);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Añade campos opcionales de cuentas, pagos y compras sin alterar datos existentes. */
    private void migrarVersion4AVersion5(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            database.execSQL("ALTER TABLE " + TABLE_CUENTAS + " ADD COLUMN "
                    + COLUMN_CUENTA_ENTIDAD + " TEXT");
            database.execSQL("ALTER TABLE " + TABLE_MOVIMIENTOS + " ADD COLUMN "
                    + COLUMN_MEDIO_PAGO + " TEXT NOT NULL DEFAULT '" + Movimiento.MEDIO_EFECTIVO + "'");
            database.execSQL("ALTER TABLE " + TABLE_COMPRAS + " ADD COLUMN "
                    + COLUMN_PRECIO_ESTIMADO + " INTEGER NOT NULL DEFAULT 0");
            verificarClavesForaneas(database);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Conserva todas las deudas existentes como pendientes al añadir su estado explícito. */
    private void migrarVersion5AVersion6(SQLiteDatabase database) {
        database.beginTransaction();
        try {
            database.execSQL("ALTER TABLE " + TABLE_GASTOS_COMPARTIDOS + " ADD COLUMN "
                    + COLUMN_PAGADO + " INTEGER NOT NULL DEFAULT 0 CHECK(" + COLUMN_PAGADO
                    + " IN (0, 1))");
            verificarClavesForaneas(database);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /** Comprueba los campos heredados antes de eliminar la tabla de versión 1. */
    private static void verificarDatosMigrados(SQLiteDatabase database) {
        long ausentesEnMigracion = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM (SELECT " + MOVEMENT_DATA_COLUMNS + " FROM "
                        + TABLE_MOVIMIENTOS + " EXCEPT SELECT " + MOVEMENT_DATA_COLUMNS + " FROM "
                        + TABLE_MOVIMIENTOS_MIGRACION + ")", null);
        long inesperadosEnMigracion = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM (SELECT " + MOVEMENT_DATA_COLUMNS + " FROM "
                        + TABLE_MOVIMIENTOS_MIGRACION + " EXCEPT SELECT " + MOVEMENT_DATA_COLUMNS
                        + " FROM " + TABLE_MOVIMIENTOS + ")", null);
        if (ausentesEnMigracion != 0 || inesperadosEnMigracion != 0) {
            throw new IllegalStateException("La migración alteró datos de movimientos existentes.");
        }
    }

    private static void verificarCuentasMigradas(SQLiteDatabase database, String tablaNueva) {
        long ausentes = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM (SELECT " + COLUMN_ID + ", " + COLUMN_CUENTA_NOMBRE + ", "
                        + COLUMN_CUENTA_TIPO + " FROM " + TABLE_CUENTAS + " EXCEPT SELECT "
                        + COLUMN_ID + ", " + COLUMN_CUENTA_NOMBRE + ", " + COLUMN_CUENTA_TIPO
                        + " FROM " + tablaNueva + ")", null);
        long inesperadas = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM (SELECT " + COLUMN_ID + ", " + COLUMN_CUENTA_NOMBRE + ", "
                        + COLUMN_CUENTA_TIPO + " FROM " + tablaNueva + " EXCEPT SELECT "
                        + COLUMN_ID + ", " + COLUMN_CUENTA_NOMBRE + ", " + COLUMN_CUENTA_TIPO
                        + " FROM " + TABLE_CUENTAS + ")", null);
        if (ausentes != 0 || inesperadas != 0) {
            throw new IllegalStateException("La migración alteró cuentas existentes.");
        }
    }

    private static void verificarRelacionesDeCuentas(SQLiteDatabase database) {
        long relacionesInvalidas = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM " + TABLE_MOVIMIENTOS + " m LEFT JOIN " + TABLE_CUENTAS
                        + " c ON m." + COLUMN_CUENTA_ID + " = c." + COLUMN_ID
                        + " WHERE c." + COLUMN_ID + " IS NULL", null);
        if (relacionesInvalidas != 0) {
            throw new IllegalStateException("La migración dejó movimientos sin cuenta válida.");
        }
    }

    private static void verificarMovimientosVersionTresMigrados(SQLiteDatabase database,
                                                                  String tablaNueva) {
        long ausentes = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM (SELECT " + MOVEMENT_V3_DATA_COLUMNS + " FROM "
                        + TABLE_MOVIMIENTOS + " EXCEPT SELECT " + MOVEMENT_V3_DATA_COLUMNS
                        + " FROM " + tablaNueva + ")", null);
        long inesperados = DatabaseUtils.longForQuery(database,
                "SELECT COUNT(*) FROM (SELECT " + MOVEMENT_V3_DATA_COLUMNS + " FROM "
                        + tablaNueva + " EXCEPT SELECT " + MOVEMENT_V3_DATA_COLUMNS + " FROM "
                        + TABLE_MOVIMIENTOS + ")", null);
        if (ausentes != 0 || inesperados != 0) {
            throw new IllegalStateException("La migración alteró movimientos existentes.");
        }
    }

    private static void verificarClavesForaneas(SQLiteDatabase database) {
        try (android.database.Cursor cursor = database.rawQuery("PRAGMA foreign_key_check", null)) {
            if (cursor.moveToFirst()) {
                throw new IllegalStateException("La migración dejó relaciones de clave foránea inválidas.");
            }
        }
    }

    private static long crearCuenta(SQLiteDatabase database, String nombre, String tipo, String entidad,
                                    boolean incluyeEntidadFinanciera) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_CUENTA_NOMBRE, nombre);
        values.put(COLUMN_CUENTA_TIPO, tipo);
        if (incluyeEntidadFinanciera) {
            if (entidad == null || entidad.trim().isEmpty()) {
                values.putNull(COLUMN_CUENTA_ENTIDAD);
            } else {
                values.put(COLUMN_CUENTA_ENTIDAD, entidad.trim());
            }
        }
        return database.insertOrThrow(TABLE_CUENTAS, null, values);
    }

    private static void crearIndicesMovimientos(SQLiteDatabase database) {
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movimientos_cuenta_fecha ON "
                + TABLE_MOVIMIENTOS + "(" + COLUMN_CUENTA_ID + ", " + COLUMN_FECHA + " DESC)");
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_movimientos_cuenta_tipo ON "
                + TABLE_MOVIMIENTOS + "(" + COLUMN_CUENTA_ID + ", " + COLUMN_TIPO + ")");
    }

    private static void crearTablasComplementarias(SQLiteDatabase database, boolean incluyePrecioEstimado) {
        database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_COMPRAS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, "
                + "cantidad INTEGER NOT NULL CHECK(cantidad > 0), completada INTEGER NOT NULL DEFAULT 0, "
                + COLUMN_FECHA + " INTEGER NOT NULL"
                + (incluyePrecioEstimado ? ", " + COLUMN_PRECIO_ESTIMADO
                + " INTEGER NOT NULL DEFAULT 0 CHECK(" + COLUMN_PRECIO_ESTIMADO + " >= 0)" : "")
                + ")");
        database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CONTACTOS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, nombre TEXT NOT NULL, telefono TEXT)");
        database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_GASTOS_COMPARTIDOS + " ("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, contacto_id INTEGER NOT NULL, "
                + COLUMN_DESCRIPCION + " TEXT NOT NULL, monto_total INTEGER NOT NULL CHECK(monto_total > 0), "
                + "participantes INTEGER NOT NULL CHECK(participantes > 1), monto_deuda INTEGER NOT NULL CHECK(monto_deuda > 0), "
                + COLUMN_PAGADO + " INTEGER NOT NULL DEFAULT 0 CHECK(" + COLUMN_PAGADO + " IN (0, 1)), "
                + COLUMN_FECHA + " INTEGER NOT NULL, FOREIGN KEY(contacto_id) REFERENCES "
                + TABLE_CONTACTOS + "(" + COLUMN_ID + ") ON DELETE RESTRICT)");
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_gastos_compartidos_contacto ON "
                + TABLE_GASTOS_COMPARTIDOS + "(contacto_id)");
    }
}

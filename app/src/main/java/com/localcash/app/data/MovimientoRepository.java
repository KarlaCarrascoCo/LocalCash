package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localcash.app.model.Movimiento;
import com.localcash.app.model.ResumenFinanciero;
import com.localcash.app.model.Cuenta;

import java.util.ArrayList;
import java.util.List;

/** Encapsula todas las consultas y escrituras de movimientos. */
public final class MovimientoRepository {

    private static final String[] MOVIMIENTO_COLUMNS = {
            "m." + DatabaseHelper.COLUMN_ID,
            "m." + DatabaseHelper.COLUMN_CUENTA_ID,
            "m." + DatabaseHelper.COLUMN_TIPO,
            "m." + DatabaseHelper.COLUMN_DESCRIPCION,
            "m." + DatabaseHelper.COLUMN_MONTO,
            "m." + DatabaseHelper.COLUMN_CATEGORIA,
            "m." + DatabaseHelper.COLUMN_FECHA,
            "m." + DatabaseHelper.COLUMN_LATITUD,
            "m." + DatabaseHelper.COLUMN_LONGITUD,
            "m." + DatabaseHelper.COLUMN_DIRECCION_APROXIMADA,
            "m." + DatabaseHelper.COLUMN_MEDIO_PAGO,
            "c." + DatabaseHelper.COLUMN_CUENTA_NOMBRE + " AS nombre_cuenta"
    };

    private final DatabaseHelper databaseHelper;

    public MovimientoRepository(Context context) {
        this(context, DatabaseHelper.DATABASE_NAME);
    }

    /** Constructor con nombre alternativo para pruebas aisladas. */
    public MovimientoRepository(Context context, String databaseName) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext(), databaseName);
    }

    public long insertar(Movimiento movimiento) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_CUENTA_ID, movimiento.getCuentaId());
        values.put(DatabaseHelper.COLUMN_TIPO, movimiento.getTipo());
        values.put(DatabaseHelper.COLUMN_DESCRIPCION, movimiento.getDescripcion());
        values.put(DatabaseHelper.COLUMN_MONTO, movimiento.getMonto());
        values.put(DatabaseHelper.COLUMN_CATEGORIA, movimiento.getCategoria());
        values.put(DatabaseHelper.COLUMN_MEDIO_PAGO, movimiento.getMedioPago());
        values.put(DatabaseHelper.COLUMN_FECHA, movimiento.getFecha());
        if (movimiento.getLatitud() == null || movimiento.getLongitud() == null) {
            values.putNull(DatabaseHelper.COLUMN_LATITUD);
            values.putNull(DatabaseHelper.COLUMN_LONGITUD);
            values.putNull(DatabaseHelper.COLUMN_DIRECCION_APROXIMADA);
        } else {
            values.put(DatabaseHelper.COLUMN_LATITUD, movimiento.getLatitud());
            values.put(DatabaseHelper.COLUMN_LONGITUD, movimiento.getLongitud());
            if (movimiento.getDireccionAproximada() == null) {
                values.putNull(DatabaseHelper.COLUMN_DIRECCION_APROXIMADA);
            } else {
                values.put(DatabaseHelper.COLUMN_DIRECCION_APROXIMADA,
                        movimiento.getDireccionAproximada());
            }
        }

        database.beginTransaction();
        try {
            long id = database.insertOrThrow(DatabaseHelper.TABLE_MOVIMIENTOS, null, values);
            database.setTransactionSuccessful();
            return id;
        } finally {
            database.endTransaction();
        }
    }

    public List<Movimiento> obtenerRecientes(int maximo) {
        return obtenerRecientesPorCuenta(Cuenta.ID_TODAS_LAS_CUENTAS, maximo);
    }

    public List<Movimiento> obtenerRecientesPorCuenta(long cuentaId, int maximo) {
        int limiteSeguro = Math.max(1, Math.min(maximo, 100));
        List<Movimiento> movimientos = new ArrayList<>();
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        String selection = cuentaId == Cuenta.ID_TODAS_LAS_CUENTAS
                ? null
                : "m." + DatabaseHelper.COLUMN_CUENTA_ID + " = ?";
        String[] selectionArgs = cuentaId == Cuenta.ID_TODAS_LAS_CUENTAS
                ? null
                : new String[]{String.valueOf(cuentaId)};

        try (Cursor cursor = database.query(
                DatabaseHelper.TABLE_MOVIMIENTOS + " m INNER JOIN " + DatabaseHelper.TABLE_CUENTAS
                        + " c ON m." + DatabaseHelper.COLUMN_CUENTA_ID + " = c."
                        + DatabaseHelper.COLUMN_ID,
                MOVIMIENTO_COLUMNS,
                selection,
                selectionArgs,
                null,
                null,
                "m." + DatabaseHelper.COLUMN_FECHA + " DESC",
                String.valueOf(limiteSeguro))) {
            while (cursor.moveToNext()) {
                movimientos.add(leerMovimiento(cursor));
            }
        }
        return movimientos;
    }

    /** Devuelve todo el historial disponible, filtrable sin interpolar valores en SQL. */
    public List<Movimiento> obtenerHistorial(long cuentaId, String tipo) {
        List<String> condiciones = new ArrayList<>();
        List<String> argumentos = new ArrayList<>();
        if (cuentaId != Cuenta.ID_TODAS_LAS_CUENTAS) {
            condiciones.add("m." + DatabaseHelper.COLUMN_CUENTA_ID + " = ?");
            argumentos.add(String.valueOf(cuentaId));
        }
        if (Movimiento.TIPO_INGRESO.equals(tipo) || Movimiento.TIPO_GASTO.equals(tipo)) {
            condiciones.add("m." + DatabaseHelper.COLUMN_TIPO + " = ?");
            argumentos.add(tipo);
        }
        String selection = condiciones.isEmpty() ? null : android.text.TextUtils.join(" AND ", condiciones);
        List<Movimiento> movimientos = new ArrayList<>();
        try (Cursor cursor = databaseHelper.getReadableDatabase().query(
                DatabaseHelper.TABLE_MOVIMIENTOS + " m INNER JOIN " + DatabaseHelper.TABLE_CUENTAS
                        + " c ON m." + DatabaseHelper.COLUMN_CUENTA_ID + " = c."
                        + DatabaseHelper.COLUMN_ID,
                MOVIMIENTO_COLUMNS, selection,
                argumentos.isEmpty() ? null : argumentos.toArray(new String[0]), null, null,
                "m." + DatabaseHelper.COLUMN_FECHA + " DESC")) {
            while (cursor.moveToNext()) {
                movimientos.add(leerMovimiento(cursor));
            }
        }
        return movimientos;
    }

    public ResumenFinanciero obtenerResumen() {
        return obtenerResumenPorCuenta(Cuenta.ID_TODAS_LAS_CUENTAS);
    }

    public ResumenFinanciero obtenerResumenPorCuenta(long cuentaId) {
        String sql = "SELECT "
                + "COALESCE(SUM(CASE WHEN " + DatabaseHelper.COLUMN_TIPO + " = ? THEN "
                + DatabaseHelper.COLUMN_MONTO + " ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN " + DatabaseHelper.COLUMN_TIPO + " = ? THEN "
                + DatabaseHelper.COLUMN_MONTO + " ELSE 0 END), 0) "
                + "FROM " + DatabaseHelper.TABLE_MOVIMIENTOS
                + (cuentaId == Cuenta.ID_TODAS_LAS_CUENTAS ? "" : " WHERE "
                + DatabaseHelper.COLUMN_CUENTA_ID + " = ?");

        String[] selectionArgs = cuentaId == Cuenta.ID_TODAS_LAS_CUENTAS
                ? new String[]{Movimiento.TIPO_INGRESO, Movimiento.TIPO_GASTO}
                : new String[]{Movimiento.TIPO_INGRESO, Movimiento.TIPO_GASTO, String.valueOf(cuentaId)};

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        try (Cursor cursor = database.rawQuery(sql, selectionArgs)) {
            if (cursor.moveToFirst()) {
                return new ResumenFinanciero(cursor.getLong(0), cursor.getLong(1));
            }
        }
        return new ResumenFinanciero(0, 0);
    }

    public boolean eliminar(long id) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            boolean eliminado = database.delete(
                    DatabaseHelper.TABLE_MOVIMIENTOS,
                    DatabaseHelper.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(id)}) > 0;
            database.setTransactionSuccessful();
            return eliminado;
        } finally {
            database.endTransaction();
        }
    }

    private static Movimiento leerMovimiento(Cursor cursor) {
        return new Movimiento(
                cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CUENTA_ID)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_TIPO)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DESCRIPCION)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MONTO)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CATEGORIA)),
                cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FECHA)),
                cursor.getString(cursor.getColumnIndexOrThrow("nombre_cuenta")),
                cursor.isNull(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUD)) ? null
                        : cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LATITUD)),
                cursor.isNull(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUD)) ? null
                        : cursor.getDouble(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_LONGITUD)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_DIRECCION_APROXIMADA)),
                cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MEDIO_PAGO)));
    }
}

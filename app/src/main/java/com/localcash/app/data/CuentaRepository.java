package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.localcash.app.model.Cuenta;

import java.util.ArrayList;
import java.util.List;

/** Administra las cuentas sin almacenar saldos derivados. */
public final class CuentaRepository {

    private final DatabaseHelper databaseHelper;

    public CuentaRepository(Context context) {
        this(context, DatabaseHelper.DATABASE_NAME);
    }

    /** Constructor con nombre alternativo para pruebas aisladas. */
    public CuentaRepository(Context context, String databaseName) {
        databaseHelper = new DatabaseHelper(context.getApplicationContext(), databaseName);
    }

    public long crear(Cuenta cuenta) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_CUENTA_NOMBRE, cuenta.getNombre());
        values.put(DatabaseHelper.COLUMN_CUENTA_TIPO, cuenta.getTipo());
        if (cuenta.getEntidadFinanciera() == null || cuenta.getEntidadFinanciera().trim().isEmpty()) {
            values.putNull(DatabaseHelper.COLUMN_CUENTA_ENTIDAD);
        } else {
            values.put(DatabaseHelper.COLUMN_CUENTA_ENTIDAD, cuenta.getEntidadFinanciera().trim());
        }
        return database.insertOrThrow(DatabaseHelper.TABLE_CUENTAS, null, values);
    }

    public List<Cuenta> obtenerTodas() {
        List<Cuenta> cuentas = new ArrayList<>();
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        try (Cursor cursor = database.query(
                DatabaseHelper.TABLE_CUENTAS,
                new String[]{
                        DatabaseHelper.COLUMN_ID,
                        DatabaseHelper.COLUMN_CUENTA_NOMBRE,
                        DatabaseHelper.COLUMN_CUENTA_TIPO,
                        DatabaseHelper.COLUMN_CUENTA_ENTIDAD
                },
                null,
                null,
                null,
                null,
                DatabaseHelper.COLUMN_CUENTA_NOMBRE + " COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                cuentas.add(new Cuenta(
                        cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CUENTA_NOMBRE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CUENTA_TIPO)),
                        cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_CUENTA_ENTIDAD))));
            }
        }
        return cuentas;
    }

    public boolean actualizar(Cuenta cuenta) {
        if (cuenta == null || cuenta.getId() <= 0 || cuenta.getNombre() == null
                || cuenta.getNombre().trim().isEmpty() || cuenta.getTipo() == null) {
            throw new IllegalArgumentException("Cuenta inválida.");
        }
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_CUENTA_NOMBRE, cuenta.getNombre().trim());
        values.put(DatabaseHelper.COLUMN_CUENTA_TIPO, cuenta.getTipo());
        if (cuenta.getEntidadFinanciera() == null || cuenta.getEntidadFinanciera().trim().isEmpty()) {
            values.putNull(DatabaseHelper.COLUMN_CUENTA_ENTIDAD);
        } else {
            values.put(DatabaseHelper.COLUMN_CUENTA_ENTIDAD, cuenta.getEntidadFinanciera().trim());
        }
        return databaseHelper.getWritableDatabase().update(DatabaseHelper.TABLE_CUENTAS, values,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(cuenta.getId())}) > 0;
    }

    /** Solo elimina cuentas sin movimientos y conserva siempre al menos una cuenta. */
    public boolean eliminarSiNoTieneMovimientos(long cuentaId) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            long cantidadCuentas = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CUENTAS, null);
            long cantidadMovimientos = DatabaseUtils.longForQuery(
                    database, "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_MOVIMIENTOS
                            + " WHERE " + DatabaseHelper.COLUMN_CUENTA_ID + " = ?",
                    new String[]{String.valueOf(cuentaId)});
            if (cantidadCuentas <= 1 || cantidadMovimientos > 0) {
                return false;
            }

            boolean eliminada = database.delete(
                    DatabaseHelper.TABLE_CUENTAS,
                    DatabaseHelper.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(cuentaId)}) > 0;
            database.setTransactionSuccessful();
            return eliminada;
        } finally {
            database.endTransaction();
        }
    }
}

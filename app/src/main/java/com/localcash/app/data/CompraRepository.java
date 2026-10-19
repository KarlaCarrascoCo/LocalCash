package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localcash.app.model.Compra;

import java.util.ArrayList;
import java.util.List;

/** Acceso local a la planificación de compras. */
public final class CompraRepository {
    private final DatabaseHelper helper;
    public CompraRepository(Context context) { helper = new DatabaseHelper(context.getApplicationContext()); }

    public long agregar(String nombre, int cantidad) {
        return agregar(nombre, cantidad, 0);
    }

    public long agregar(String nombre, int cantidad, long precioEstimado) {
        if (nombre == null || nombre.trim().isEmpty() || cantidad <= 0 || precioEstimado < 0) {
            throw new IllegalArgumentException("Datos de compra inválidos.");
        }
        ContentValues values = new ContentValues();
        values.put("nombre", nombre.trim()); values.put("cantidad", cantidad);
        values.put(DatabaseHelper.COLUMN_PRECIO_ESTIMADO, precioEstimado);
        values.put("completada", 0); values.put(DatabaseHelper.COLUMN_FECHA, System.currentTimeMillis());
        return helper.getWritableDatabase().insertOrThrow(DatabaseHelper.TABLE_COMPRAS, null, values);
    }

    public List<Compra> obtenerTodas() {
        List<Compra> compras = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_COMPRAS,
                new String[]{DatabaseHelper.COLUMN_ID, "nombre", "cantidad", "completada",
                        DatabaseHelper.COLUMN_PRECIO_ESTIMADO}, null, null,
                null, null, "completada ASC, " + DatabaseHelper.COLUMN_FECHA + " DESC")) {
            while (cursor.moveToNext()) compras.add(new Compra(cursor.getLong(0), cursor.getString(1),
                    cursor.getInt(2), cursor.getInt(3) == 1, cursor.getLong(4)));
        }
        return compras;
    }

    public void marcarCompletada(long id, boolean completada) {
        ContentValues values = new ContentValues(); values.put("completada", completada ? 1 : 0);
        helper.getWritableDatabase().update(DatabaseHelper.TABLE_COMPRAS, values,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
    }
}

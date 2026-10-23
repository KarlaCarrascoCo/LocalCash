package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localcash.app.model.GastoCompartido;
import com.localcash.app.model.Contacto;
import com.localcash.app.util.FinancialRules;

import java.util.ArrayList;
import java.util.List;

/** Divide un gasto y conserva localmente la deuda de cada contacto seleccionado. */
public final class GastoCompartidoRepository {
    private final DatabaseHelper helper;
    public GastoCompartidoRepository(Context context) { helper = new DatabaseHelper(context.getApplicationContext()); }

    public long crear(long contactoId, String descripcion, long montoTotal, int participantes) {
        long deuda = FinancialRules.deudaPorContacto(montoTotal, participantes);
        if (contactoId <= 0) throw new IllegalArgumentException("Datos de división inválidos.");
        ContentValues values = new ContentValues(); values.put("contacto_id", contactoId);
        values.put(DatabaseHelper.COLUMN_DESCRIPCION, descripcion.trim()); values.put("monto_total", montoTotal);
        values.put("participantes", participantes); values.put("monto_deuda", deuda);
        values.put(DatabaseHelper.COLUMN_FECHA, System.currentTimeMillis());
        return helper.getWritableDatabase().insertOrThrow(DatabaseHelper.TABLE_GASTOS_COMPARTIDOS, null, values);
    }

    /** Registra una deuda por cada contacto; el redondeo restante corresponde al pagador. */
    public void crearParaContactos(List<Contacto> contactos, String descripcion, long montoTotal) {
        if (contactos == null || contactos.isEmpty() || descripcion == null
                || descripcion.trim().isEmpty() || montoTotal <= 0) {
            throw new IllegalArgumentException("Datos de división inválidos.");
        }
        int participantes = contactos.size() + 1;
        long deuda = FinancialRules.deudaPorContacto(montoTotal, participantes);
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        try {
            for (Contacto contacto : contactos) {
                ContentValues values = new ContentValues();
                values.put("contacto_id", contacto.getId());
                values.put(DatabaseHelper.COLUMN_DESCRIPCION, descripcion.trim());
                values.put("monto_total", montoTotal);
                values.put("participantes", participantes);
                values.put("monto_deuda", deuda);
                values.put(DatabaseHelper.COLUMN_FECHA, System.currentTimeMillis());
                database.insertOrThrow(DatabaseHelper.TABLE_GASTOS_COMPARTIDOS, null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public List<GastoCompartido> obtenerTodos() {
        List<GastoCompartido> gastos = new ArrayList<>();
        String sql = "SELECT g.id, c.nombre, g.descripcion, g.monto_total, g.participantes, g.monto_deuda, g."
                + DatabaseHelper.COLUMN_PAGADO + " "
                + "FROM " + DatabaseHelper.TABLE_GASTOS_COMPARTIDOS + " g INNER JOIN "
                + DatabaseHelper.TABLE_CONTACTOS + " c ON g.contacto_id = c.id ORDER BY g.fecha DESC";
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) gastos.add(new GastoCompartido(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getLong(3), cursor.getInt(4), cursor.getLong(5),
                    cursor.getInt(6) == 1));
        }
        return gastos;
    }

    public boolean marcarPagado(long id, boolean pagado) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_PAGADO, pagado ? 1 : 0);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_GASTOS_COMPARTIDOS, values,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }
}

package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localcash.app.model.GastoCompartido;
import com.localcash.app.model.Contacto;
import com.localcash.app.util.FinancialRules;

import java.util.ArrayList;
import java.util.Map;
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
        String detalladas = "SELECT a.id, c.nombre, p.descripcion, p.monto, "
                + "(SELECT COUNT(*) + 1 FROM " + DatabaseHelper.TABLE_ASIGNACIONES_COMPARTIDAS
                + " ax WHERE ax.compra_id = p.id), a.monto_deuda, a.pagado, "
                + "(SELECT GROUP_CONCAT(i.descripcion, ' · ') FROM " + DatabaseHelper.TABLE_ITEMS_COMPARTIDOS
                + " i WHERE i.compra_id = p.id) FROM " + DatabaseHelper.TABLE_ASIGNACIONES_COMPARTIDAS
                + " a INNER JOIN " + DatabaseHelper.TABLE_COMPRAS_COMPARTIDAS + " p ON a.compra_id = p.id "
                + "INNER JOIN " + DatabaseHelper.TABLE_CONTACTOS + " c ON a.contacto_id = c.id ORDER BY p.fecha DESC";
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(detalladas, null)) {
            while (cursor.moveToNext()) gastos.add(new GastoCompartido(cursor.getLong(0), cursor.getString(1),
                    cursor.getString(2), cursor.getLong(3), cursor.getInt(4), cursor.getLong(5),
                    cursor.getInt(6) == 1, cursor.getString(7), true));
        }
        return gastos;
    }

    public boolean marcarPagado(long id, boolean pagado) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_PAGADO, pagado ? 1 : 0);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_GASTOS_COMPARTIDOS, values,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(id)}) > 0;
    }

    public boolean marcarPagado(GastoCompartido gasto, boolean pagado) {
        if (gasto.getDetalleCompra() == null) return marcarPagado(gasto.getId(), pagado);
        ContentValues values = new ContentValues(); values.put(DatabaseHelper.COLUMN_PAGADO, pagado ? 1 : 0);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_ASIGNACIONES_COMPARTIDAS, values,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(gasto.getId())}) > 0;
    }

    /** Recupera los participantes de un gasto para mostrarlo como un grupo navegable. */
    public List<GastoCompartido> obtenerPorGrupo(String descripcion, long montoTotal, int participantes) {
        List<GastoCompartido> resultado = new ArrayList<>();
        for (GastoCompartido gasto : obtenerTodos()) {
            if (gasto.getDescripcion().equals(descripcion) && gasto.getMontoTotal() == montoTotal
                    && gasto.getParticipantes() == participantes) {
                resultado.add(gasto);
            }
        }
        return resultado;
    }

    /** Registra una compra grupal con productos y montos asignados de forma equitativa o personalizada. */
    public void crearCompraDetallada(List<Contacto> contactos, String descripcion, long montoTotal,
                                     List<String> lineasDetalle, Map<Long, Long> asignaciones) {
        if (contactos == null || contactos.isEmpty() || descripcion == null || descripcion.trim().isEmpty()
                || montoTotal <= 0 || lineasDetalle == null || lineasDetalle.isEmpty()) {
            throw new IllegalArgumentException("Compra compartida inválida.");
        }
        SQLiteDatabase database = helper.getWritableDatabase(); database.beginTransaction();
        try {
            ContentValues compra = new ContentValues(); compra.put(DatabaseHelper.COLUMN_DESCRIPCION, descripcion.trim());
            compra.put(DatabaseHelper.COLUMN_MONTO, montoTotal); compra.put(DatabaseHelper.COLUMN_FECHA, System.currentTimeMillis());
            long compraId = database.insertOrThrow(DatabaseHelper.TABLE_COMPRAS_COMPARTIDAS, null, compra);
            long totalItems = 0;
            for (String linea : lineasDetalle) {
                String[] partes = linea.split("\\|", 2);
                if (partes.length != 2 || partes[0].trim().isEmpty()) throw new IllegalArgumentException("Detalle inválido.");
                long montoItem = Long.parseLong(partes[1].trim());
                if (montoItem <= 0) throw new IllegalArgumentException("Detalle inválido.");
                totalItems += montoItem;
                ContentValues item = new ContentValues(); item.put("compra_id", compraId);
                item.put(DatabaseHelper.COLUMN_DESCRIPCION, partes[0].trim()); item.put(DatabaseHelper.COLUMN_MONTO, montoItem);
                database.insertOrThrow(DatabaseHelper.TABLE_ITEMS_COMPARTIDOS, null, item);
            }
            if (totalItems != montoTotal) throw new IllegalArgumentException("El detalle no coincide con el total.");
            long deudaEquitativa = FinancialRules.deudaPorContacto(montoTotal, contactos.size() + 1);
            long totalAsignado = 0;
            for (Contacto contacto : contactos) {
                long deuda = asignaciones == null || asignaciones.isEmpty() ? deudaEquitativa
                        : asignaciones.containsKey(contacto.getId()) ? asignaciones.get(contacto.getId()) : 0;
                if (deuda <= 0) throw new IllegalArgumentException("Asignación inválida.");
                totalAsignado += deuda;
                ContentValues asignacion = new ContentValues(); asignacion.put("compra_id", compraId);
                asignacion.put("contacto_id", contacto.getId()); asignacion.put("monto_deuda", deuda);
                database.insertOrThrow(DatabaseHelper.TABLE_ASIGNACIONES_COMPARTIDAS, null, asignacion);
            }
            if (totalAsignado > montoTotal) throw new IllegalArgumentException("Las deudas superan el total.");
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }
}

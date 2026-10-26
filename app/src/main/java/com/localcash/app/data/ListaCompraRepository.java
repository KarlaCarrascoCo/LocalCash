package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.localcash.app.model.Contacto;
import com.localcash.app.model.ListaCompra;
import com.localcash.app.model.ProductoCompra;
import com.localcash.app.util.FinancialRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persistencia local de listas de compras, productos y contactos asociados. */
public final class ListaCompraRepository {
    private final DatabaseHelper helper;

    public ListaCompraRepository(Context context) {
        this(context, DatabaseHelper.DATABASE_NAME);
    }

    /** Constructor con base alternativa para pruebas aisladas; nunca apunta a la base real por defecto. */
    public ListaCompraRepository(Context context, String databaseName) {
        helper = new DatabaseHelper(context.getApplicationContext(), databaseName);
    }

    public long crearLista(String lugarTipo, String lugarPersonalizado, Long fechaProgramada,
                           List<Long> contactoIds) {
        validarLugar(lugarTipo, lugarPersonalizado);
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues values = valoresLista(lugarTipo, lugarPersonalizado, fechaProgramada);
            values.put(DatabaseHelper.COLUMN_FECHA, System.currentTimeMillis());
            values.put(DatabaseHelper.COLUMN_ESTADO, ListaCompra.ESTADO_PENDIENTE);
            long id = database.insertOrThrow(DatabaseHelper.TABLE_LISTAS_COMPRAS, null, values);
            guardarContactos(database, id, contactoIds);
            database.setTransactionSuccessful();
            return id;
        } finally {
            database.endTransaction();
        }
    }

    public boolean actualizarLista(long listaId, String lugarTipo, String lugarPersonalizado,
                                   Long fechaProgramada, List<Long> contactoIds) {
        validarLugar(lugarTipo, lugarPersonalizado);
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        try {
            ListaCompra actual = obtenerLista(listaId);
            if (actual == null || actual.isCompletada()) return false;
            ContentValues values = valoresLista(lugarTipo, lugarPersonalizado, fechaProgramada);
            if (!actual.isEnCompra()) values.put(DatabaseHelper.COLUMN_ESTADO, ListaCompra.ESTADO_PENDIENTE);
            boolean actualizada = database.update(DatabaseHelper.TABLE_LISTAS_COMPRAS, values,
                    DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " != ?",
                    new String[]{String.valueOf(listaId), ListaCompra.ESTADO_FINALIZADA}) > 0;
            if (!actualizada) return false;
            database.delete(DatabaseHelper.TABLE_LISTAS_CONTACTOS, "lista_id = ?",
                    new String[]{String.valueOf(listaId)});
            guardarContactos(database, listaId, contactoIds);
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    public boolean eliminarLista(long listaId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_LISTAS_COMPRAS,
                DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " != ?",
                new String[]{String.valueOf(listaId), ListaCompra.ESTADO_FINALIZADA}) > 0;
    }

    /**
     * Elimina exclusivamente un registro finalizado. Sus productos y asociaciones de contactos
     * se eliminan por las claves foráneas ON DELETE CASCADE; los contactos y datos financieros
     * no tienen relación con una lista de compras y no se modifican.
     */
    public boolean eliminarRegistroFinalizado(long listaId) {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        try {
            int eliminadas = database.delete(DatabaseHelper.TABLE_LISTAS_COMPRAS,
                    DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " = ?",
                    new String[]{String.valueOf(listaId), ListaCompra.ESTADO_FINALIZADA});
            if (eliminadas != 1) {
                return false;
            }
            try (Cursor relaciones = database.rawQuery("PRAGMA foreign_key_check", null)) {
                if (relaciones.moveToFirst()) {
                    throw new IllegalStateException("La eliminación dejó relaciones inválidas.");
                }
            }
            database.setTransactionSuccessful();
            return true;
        } finally {
            database.endTransaction();
        }
    }

    public boolean completarLista(long listaId) {
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_ESTADO, ListaCompra.ESTADO_FINALIZADA);
        values.put(DatabaseHelper.COLUMN_FECHA_COMPLETADA, System.currentTimeMillis());
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_LISTAS_COMPRAS, values,
                DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " != ?",
                new String[]{String.valueOf(listaId), ListaCompra.ESTADO_FINALIZADA}) > 0;
    }

    public boolean iniciarCompra(long listaId) {
        ContentValues values = new ContentValues(); values.put(DatabaseHelper.COLUMN_ESTADO, ListaCompra.ESTADO_EN_COMPRA);
        values.put(DatabaseHelper.COLUMN_FECHA_INICIO, System.currentTimeMillis());
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_LISTAS_COMPRAS, values,
                DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " IN (?, ?)",
                new String[]{String.valueOf(listaId), ListaCompra.ESTADO_PENDIENTE, ListaCompra.ESTADO_PROGRAMADA}) > 0;
    }

    public boolean actualizarFechaProgramada(long listaId, Long fechaProgramada) {
        ListaCompra actual = obtenerLista(listaId);
        if (actual == null || actual.isCompletada()) return false;
        ContentValues values = new ContentValues();
        if (fechaProgramada == null) values.putNull(DatabaseHelper.COLUMN_FECHA_PROGRAMADA);
        else values.put(DatabaseHelper.COLUMN_FECHA_PROGRAMADA, fechaProgramada);
        // La programación es un atributo de una lista pendiente, no un estado distinto.
        // ESTADO_PROGRAMADA solo se tolera en iniciarCompra para instalaciones antiguas.
        if (!actual.isEnCompra()) {
            values.put(DatabaseHelper.COLUMN_ESTADO, ListaCompra.ESTADO_PENDIENTE);
        }
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_LISTAS_COMPRAS, values,
                DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " != ?",
                new String[]{String.valueOf(listaId), ListaCompra.ESTADO_FINALIZADA}) > 0;
    }

    public List<ListaCompra> obtenerPendientes(long ahora) {
        return consultarListas("l." + DatabaseHelper.COLUMN_ESTADO + " IN ('" + ListaCompra.ESTADO_PENDIENTE + "', '"
                        + ListaCompra.ESTADO_EN_COMPRA + "')", null,
                "CASE WHEN l.fecha_programada IS NULL THEN 1 ELSE 0 END, l.fecha_programada ASC");
    }

    public List<ListaCompra> obtenerProgramadas(long ahora) {
        return consultarListas("l." + DatabaseHelper.COLUMN_ESTADO + " = '" + ListaCompra.ESTADO_PENDIENTE + "' AND l.fecha_programada > ?",
                new String[]{String.valueOf(ahora)}, "l.fecha_programada ASC");
    }

    public List<ListaCompra> obtenerHistorial() {
        return consultarListas("l." + DatabaseHelper.COLUMN_ESTADO + " = '" + ListaCompra.ESTADO_FINALIZADA + "'", null, "l.fecha_completada DESC, l.fecha DESC");
    }

    public ListaCompra obtenerLista(long listaId) {
        List<ListaCompra> listas = consultarListas("l.id = ?", new String[]{String.valueOf(listaId)}, null);
        return listas.isEmpty() ? null : listas.get(0);
    }

    public List<Contacto> obtenerContactos(long listaId) {
        List<Contacto> contactos = new ArrayList<>();
        String sql = "SELECT c.id, c.nombre, c.telefono FROM " + DatabaseHelper.TABLE_CONTACTOS + " c "
                + "INNER JOIN " + DatabaseHelper.TABLE_LISTAS_CONTACTOS + " lc ON lc.contacto_id = c.id "
                + "WHERE lc.lista_id = ? ORDER BY c.nombre COLLATE NOCASE";
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(listaId)})) {
            while (cursor.moveToNext()) contactos.add(new Contacto(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
        }
        return contactos;
    }

    public List<ProductoCompra> obtenerProductos(long listaId) {
        List<ProductoCompra> productos = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_PRODUCTOS_COMPRA,
                new String[]{DatabaseHelper.COLUMN_ID, "lista_id", "nombre", "cantidad", "unidad",
                        "unidad_personalizada", DatabaseHelper.COLUMN_PRECIO_ESTIMADO, "precio_real", "adquirido", DatabaseHelper.COLUMN_ESTADO_PRODUCTO},
                "lista_id = ?", new String[]{String.valueOf(listaId)}, null, null, DatabaseHelper.COLUMN_ESTADO_PRODUCTO + " ASC, nombre COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) {
                String estado = cursor.isNull(9) ? (cursor.getInt(8) == 1
                        ? ProductoCompra.ESTADO_COMPRADO : ProductoCompra.ESTADO_SIN_REVISAR)
                        : cursor.getString(9);
                productos.add(new ProductoCompra(cursor.getLong(0), cursor.getLong(1), cursor.getString(2),
                        cursor.getDouble(3), cursor.getString(4), cursor.getString(5), cursor.getLong(6),
                        cursor.isNull(7) ? null : cursor.getLong(7), estado));
            }
        }
        return productos;
    }

    public long agregarProducto(long listaId, ProductoCompra producto) {
        validarProducto(producto);
        if (!listaEstaAbierta(listaId)) throw new IllegalStateException("La lista ya está finalizada.");
        ContentValues values = valoresProducto(producto);
        values.put("lista_id", listaId);
        return helper.getWritableDatabase().insertOrThrow(DatabaseHelper.TABLE_PRODUCTOS_COMPRA, null, values);
    }

    public boolean actualizarProducto(ProductoCompra producto) {
        validarProducto(producto);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_PRODUCTOS_COMPRA, valoresProducto(producto),
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(producto.getId())}) > 0;
    }

    public boolean marcarProductoComprado(long productoId, boolean comprado) {
        return actualizarEstadoProducto(productoId, comprado ? ProductoCompra.ESTADO_COMPRADO
                : ProductoCompra.ESTADO_SIN_REVISAR);
    }

    public boolean actualizarEstadoProducto(long productoId, String estado) {
        if (!ProductoCompra.esEstadoValido(estado)) {
            throw new IllegalArgumentException("Estado de producto inválido.");
        }
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_ESTADO_PRODUCTO, estado);
        values.put("adquirido", ProductoCompra.ESTADO_COMPRADO.equals(estado) ? 1 : 0);
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_PRODUCTOS_COMPRA, values,
                DatabaseHelper.COLUMN_ID + " = ? AND lista_id IN (SELECT id FROM " + DatabaseHelper.TABLE_LISTAS_COMPRAS
                + " WHERE " + DatabaseHelper.COLUMN_ESTADO + " != '" + ListaCompra.ESTADO_FINALIZADA + "')", new String[]{String.valueOf(productoId)}) > 0;
    }

    public boolean eliminarProducto(long productoId) {
        return helper.getWritableDatabase().delete(DatabaseHelper.TABLE_PRODUCTOS_COMPRA,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(productoId)}) > 0;
    }

    private List<ListaCompra> consultarListas(String where, String[] args, String orderBy) {
        List<ListaCompra> listas = new ArrayList<>();
        String sql = "SELECT l.id, l.lugar_tipo, l.lugar_personalizado, l.fecha, l.fecha_programada, l." + DatabaseHelper.COLUMN_FECHA_INICIO + ", l." + DatabaseHelper.COLUMN_ESTADO + ", l.fecha_completada, "
                + "COUNT(p.id), COALESCE(SUM(CASE WHEN p." + DatabaseHelper.COLUMN_ESTADO_PRODUCTO + " = 'COMPRADO' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(ROUND(p.cantidad * p.precio_estimado)), 0), "
                + "COALESCE(SUM(CASE WHEN p." + DatabaseHelper.COLUMN_ESTADO_PRODUCTO + " = 'COMPRADO' AND p.precio_real IS NOT NULL THEN ROUND(p.cantidad * p.precio_real) ELSE 0 END), 0) "
                + "FROM " + DatabaseHelper.TABLE_LISTAS_COMPRAS + " l LEFT JOIN " + DatabaseHelper.TABLE_PRODUCTOS_COMPRA
                + " p ON p.lista_id = l.id WHERE " + where + " GROUP BY l.id "
                + (orderBy == null ? "" : " ORDER BY " + orderBy);
        try (Cursor cursor = helper.getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                listas.add(new ListaCompra(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3),
                        cursor.isNull(4) ? null : cursor.getLong(4), cursor.isNull(5) ? null : cursor.getLong(5), cursor.getString(6),
                        cursor.isNull(7) ? null : cursor.getLong(7), cursor.getInt(8), cursor.getInt(9),
                        cursor.getLong(10), cursor.getLong(11)));
            }
        }
        return listas;
    }

    private ContentValues valoresLista(String tipo, String personalizado, Long fechaProgramada) {
        ContentValues values = new ContentValues(); values.put("lugar_tipo", tipo);
        if (ListaCompra.LUGAR_OTRO.equals(tipo)) values.put("lugar_personalizado", personalizado.trim());
        else values.putNull("lugar_personalizado");
        if (fechaProgramada == null) values.putNull(DatabaseHelper.COLUMN_FECHA_PROGRAMADA);
        else values.put(DatabaseHelper.COLUMN_FECHA_PROGRAMADA, fechaProgramada);
        return values;
    }

    private void guardarContactos(SQLiteDatabase database, long listaId, List<Long> ids) {
        for (Long id : ids == null ? Collections.<Long>emptyList() : ids) {
            if (id == null || id <= 0) continue;
            ContentValues association = new ContentValues(); association.put("lista_id", listaId);
            association.put("contacto_id", id);
            database.insertOrThrow(DatabaseHelper.TABLE_LISTAS_CONTACTOS, null, association);
        }
    }

    private ContentValues valoresProducto(ProductoCompra producto) {
        ContentValues values = new ContentValues(); values.put("nombre", producto.getNombre().trim());
        values.put("cantidad", producto.getCantidad()); values.put("unidad", producto.getUnidad());
        if ("OTRA".equals(producto.getUnidad())) values.put("unidad_personalizada", producto.getUnidadPersonalizada().trim());
        else values.putNull("unidad_personalizada");
        values.put(DatabaseHelper.COLUMN_PRECIO_ESTIMADO, producto.getPrecioEstimado());
        if (producto.getPrecioReal() == null) values.putNull("precio_real"); else values.put("precio_real", producto.getPrecioReal());
        values.put("adquirido", producto.isComprado() ? 1 : 0);
        values.put(DatabaseHelper.COLUMN_ESTADO_PRODUCTO, producto.getEstado());
        return values;
    }

    private boolean listaEstaAbierta(long listaId) {
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_LISTAS_COMPRAS,
                new String[]{DatabaseHelper.COLUMN_ID}, DatabaseHelper.COLUMN_ID + " = ? AND " + DatabaseHelper.COLUMN_ESTADO + " != ?",
                new String[]{String.valueOf(listaId), ListaCompra.ESTADO_FINALIZADA}, null, null, null)) { return cursor.moveToFirst(); }
    }

    private static void validarLugar(String tipo, String personalizado) {
        if (tipo == null || tipo.trim().isEmpty() || (ListaCompra.LUGAR_OTRO.equals(tipo)
                && (personalizado == null || personalizado.trim().isEmpty()))) {
            throw new IllegalArgumentException("Lugar de compra inválido.");
        }
    }

    private static void validarProducto(ProductoCompra producto) {
        if (producto == null || !FinancialRules.esProductoCompraValido(producto.getNombre(), producto.getCantidad(),
                producto.getUnidad(), producto.getUnidadPersonalizada(), producto.getPrecioEstimado(), producto.getPrecioReal())
                || !ProductoCompra.esEstadoValido(producto.getEstado())) {
            throw new IllegalArgumentException("Producto de compra inválido.");
        }
    }
}

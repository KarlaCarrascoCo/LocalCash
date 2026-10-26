package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;
import android.database.DatabaseUtils;

import com.localcash.app.model.Contacto;

import java.util.ArrayList;
import java.util.List;

/** Agenda local de contactos; no consulta la agenda del dispositivo. */
public final class ContactoRepository {
    private final DatabaseHelper helper;
    public ContactoRepository(Context context) { helper = new DatabaseHelper(context.getApplicationContext()); }

    public long crear(String nombre, String telefono) {
        ContentValues values = new ContentValues(); values.put("nombre", nombre.trim());
        values.put("telefono", telefono.trim());
        return helper.getWritableDatabase().insertOrThrow(DatabaseHelper.TABLE_CONTACTOS, null, values);
    }

    public List<Contacto> obtenerTodos() {
        List<Contacto> contactos = new ArrayList<>();
        try (Cursor cursor = helper.getReadableDatabase().query(DatabaseHelper.TABLE_CONTACTOS,
                new String[]{DatabaseHelper.COLUMN_ID, "nombre", "telefono"}, null, null, null, null,
                "nombre COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) contactos.add(new Contacto(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
        }
        return contactos;
    }

    public boolean actualizar(Contacto contacto) {
        if (contacto == null || contacto.getId() <= 0 || contacto.getNombre() == null
                || contacto.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("Contacto inválido.");
        }
        ContentValues values = new ContentValues();
        values.put("nombre", contacto.getNombre().trim());
        values.put("telefono", contacto.getTelefono() == null ? "" : contacto.getTelefono().trim());
        return helper.getWritableDatabase().update(DatabaseHelper.TABLE_CONTACTOS, values,
                DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(contacto.getId())}) > 0;
    }

    /** Impide eliminar contactos que todavía tengan deudas, compras o listas asociadas. */
    public boolean eliminarSiNoTieneDeudas(long contactoId) {
        if (contactoId <= 0) return false;
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        try {
            long deudas = DatabaseUtils.longForQuery(database,
                    "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_GASTOS_COMPARTIDOS
                            + " WHERE contacto_id = ?", new String[]{String.valueOf(contactoId)});
            if (deudas > 0) return false;
            long asignaciones = DatabaseUtils.longForQuery(database,
                    "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ASIGNACIONES_COMPARTIDAS
                            + " WHERE contacto_id = ?", new String[]{String.valueOf(contactoId)});
            if (asignaciones > 0) return false;
            long listas = DatabaseUtils.longForQuery(database,
                    "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_LISTAS_CONTACTOS
                            + " WHERE contacto_id = ?", new String[]{String.valueOf(contactoId)});
            if (listas > 0) return false;
            boolean eliminado = database.delete(DatabaseHelper.TABLE_CONTACTOS,
                    DatabaseHelper.COLUMN_ID + " = ?", new String[]{String.valueOf(contactoId)}) > 0;
            database.setTransactionSuccessful();
            return eliminado;
        } catch (SQLiteConstraintException exception) {
            // Protección adicional si una relación nueva o heredada impide el borrado.
            return false;
        } finally {
            database.endTransaction();
        }
    }
}

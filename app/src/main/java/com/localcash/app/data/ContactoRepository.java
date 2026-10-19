package com.localcash.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
}

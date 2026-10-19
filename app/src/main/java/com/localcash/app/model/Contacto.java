package com.localcash.app.model;

/** Contacto local disponible para gastos compartidos. */
public final class Contacto {
    private final long id;
    private final String nombre;
    private final String telefono;

    public Contacto(long id, String nombre, String telefono) {
        this.id = id;
        this.nombre = nombre;
        this.telefono = telefono;
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTelefono() { return telefono; }

    @Override public String toString() { return nombre; }
}

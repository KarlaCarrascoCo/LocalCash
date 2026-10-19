package com.localcash.app.model;

/** Elemento de planificación de compras guardado sin conexión. */
public final class Compra {
    private final long id;
    private final String nombre;
    private final int cantidad;
    private final boolean completada;
    private final long precioEstimado;

    public Compra(long id, String nombre, int cantidad, boolean completada) {
        this(id, nombre, cantidad, completada, 0);
    }
    public Compra(long id, String nombre, int cantidad, boolean completada, long precioEstimado) {
        this.id = id; this.nombre = nombre; this.cantidad = cantidad; this.completada = completada;
        this.precioEstimado = precioEstimado;
    }
    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public int getCantidad() { return cantidad; }
    public boolean isCompletada() { return completada; }
    public long getPrecioEstimado() { return precioEstimado; }
}

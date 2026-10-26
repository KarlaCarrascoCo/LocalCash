package com.localcash.app.model;

/** Producto de una lista de compras local. Los precios representan valor unitario en CLP. */
public final class ProductoCompra {
    public static final String ESTADO_SIN_REVISAR = "SIN_REVISAR";
    public static final String ESTADO_COMPRADO = "COMPRADO";
    public static final String ESTADO_NO_COMPRADO = "NO_COMPRADO";
    private final long id;
    private final long listaId;
    private final String nombre;
    private final double cantidad;
    private final String unidad;
    private final String unidadPersonalizada;
    private final long precioEstimado;
    private final Long precioReal;
    private final String estado;

    public ProductoCompra(long id, long listaId, String nombre, double cantidad, String unidad,
                          String unidadPersonalizada, long precioEstimado, Long precioReal,
                          boolean adquirido) {
        this(id, listaId, nombre, cantidad, unidad, unidadPersonalizada, precioEstimado, precioReal,
                adquirido ? ESTADO_COMPRADO : ESTADO_SIN_REVISAR);
    }

    public ProductoCompra(long id, long listaId, String nombre, double cantidad, String unidad,
                          String unidadPersonalizada, long precioEstimado, Long precioReal,
                          String estado) {
        if (!esEstadoValido(estado)) {
            throw new IllegalArgumentException("Estado de producto inválido.");
        }
        this.id = id; this.listaId = listaId; this.nombre = nombre; this.cantidad = cantidad;
        this.unidad = unidad; this.unidadPersonalizada = unidadPersonalizada;
        this.precioEstimado = precioEstimado; this.precioReal = precioReal; this.estado = estado;
    }

    public long getId() { return id; }
    public long getListaId() { return listaId; }
    public String getNombre() { return nombre; }
    public double getCantidad() { return cantidad; }
    public String getUnidad() { return unidad; }
    public String getUnidadPersonalizada() { return unidadPersonalizada; }
    public long getPrecioEstimado() { return precioEstimado; }
    public Long getPrecioReal() { return precioReal; }
    public String getEstado() { return estado; }
    public boolean isComprado() { return ESTADO_COMPRADO.equals(estado); }
    public boolean isNoComprado() { return ESTADO_NO_COMPRADO.equals(estado); }
    public boolean isSinRevisar() { return ESTADO_SIN_REVISAR.equals(estado); }
    public static boolean esEstadoValido(String estado) {
        return ESTADO_SIN_REVISAR.equals(estado) || ESTADO_COMPRADO.equals(estado)
                || ESTADO_NO_COMPRADO.equals(estado);
    }
    public String getUnidadVisible() {
        return "OTRA".equals(unidad) && unidadPersonalizada != null ? unidadPersonalizada : unidad;
    }
}

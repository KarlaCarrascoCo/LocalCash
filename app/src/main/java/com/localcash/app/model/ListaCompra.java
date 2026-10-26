package com.localcash.app.model;

/** Resumen local de una lista de compras y su progreso. */
public final class ListaCompra {
    public static final String LUGAR_FERIA = "FERIA";
    public static final String LUGAR_SUPERMERCADO = "SUPERMERCADO";
    public static final String LUGAR_MALL = "MALL";
    public static final String LUGAR_FARMACIA = "FARMACIA";
    public static final String LUGAR_OTRO = "OTRO";
    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_PROGRAMADA = "PROGRAMADA";
    public static final String ESTADO_EN_COMPRA = "EN_COMPRA";
    public static final String ESTADO_FINALIZADA = "FINALIZADA";

    private final long id;
    private final String lugarTipo;
    private final String lugarPersonalizado;
    private final long fechaCreacion;
    private final Long fechaProgramada;
    private final Long fechaInicio;
    private final String estado;
    private final Long fechaCompletada;
    private final int productosTotales;
    private final int productosComprados;
    private final long totalEstimado;
    private final long totalReal;

    public ListaCompra(long id, String lugarTipo, String lugarPersonalizado, long fechaCreacion,
                       Long fechaProgramada, Long fechaInicio, String estado, Long fechaCompletada,
                       int productosTotales, int productosComprados, long totalEstimado, long totalReal) {
        this.id = id;
        this.lugarTipo = lugarTipo;
        this.lugarPersonalizado = lugarPersonalizado;
        this.fechaCreacion = fechaCreacion;
        this.fechaProgramada = fechaProgramada;
        this.fechaInicio = fechaInicio;
        this.estado = estado;
        this.fechaCompletada = fechaCompletada;
        this.productosTotales = productosTotales;
        this.productosComprados = productosComprados;
        this.totalEstimado = totalEstimado;
        this.totalReal = totalReal;
    }

    public long getId() { return id; }
    public String getLugarTipo() { return lugarTipo; }
    public String getLugarPersonalizado() { return lugarPersonalizado; }
    public long getFechaCreacion() { return fechaCreacion; }
    public Long getFechaProgramada() { return fechaProgramada; }
    public Long getFechaInicio() { return fechaInicio; }
    public String getEstado() { return estado; }
    public boolean isCompletada() { return ESTADO_FINALIZADA.equals(estado); }
    public boolean isEnCompra() { return ESTADO_EN_COMPRA.equals(estado); }
    public Long getFechaCompletada() { return fechaCompletada; }
    public int getProductosTotales() { return productosTotales; }
    public int getProductosComprados() { return productosComprados; }
    public long getTotalEstimado() { return totalEstimado; }
    public long getTotalReal() { return totalReal; }

    public String getNombreLugar() {
        if (LUGAR_OTRO.equals(lugarTipo) && lugarPersonalizado != null && !lugarPersonalizado.trim().isEmpty()) {
            return lugarPersonalizado;
        }
        if (LUGAR_FERIA.equals(lugarTipo)) return "Feria";
        if (LUGAR_SUPERMERCADO.equals(lugarTipo)) return "Supermercado";
        if (LUGAR_MALL.equals(lugarTipo)) return "Mall";
        if (LUGAR_FARMACIA.equals(lugarTipo)) return "Farmacia";
        return lugarTipo;
    }
}

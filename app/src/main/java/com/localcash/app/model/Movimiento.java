package com.localcash.app.model;

/** Representa un ingreso o gasto almacenado en la base de datos local. */
public final class Movimiento {

    public static final String TIPO_INGRESO = "INGRESO";
    public static final String TIPO_GASTO = "GASTO";
    public static final String MEDIO_EFECTIVO = "EFECTIVO";
    public static final String MEDIO_TARJETA_DEBITO = "TARJETA_DEBITO";
    public static final String MEDIO_TARJETA_CREDITO = "TARJETA_CREDITO";
    public static final String MEDIO_TRANSFERENCIA = "TRANSFERENCIA";
    public static final String MEDIO_CHEQUE = "CHEQUE";
    public static final String MEDIO_CUENTA_DIGITAL = "CUENTA_DIGITAL";
    public static final String MEDIO_OTRO = "OTRO";

    private final long id;
    private final long cuentaId;
    private final String tipo;
    private final String descripcion;
    private final long monto;
    private final String categoria;
    private final long fecha;
    private final String nombreCuenta;
    private final Double latitud;
    private final Double longitud;
    private final String direccionAproximada;
    private final String medioPago;

    public Movimiento(long id, long cuentaId, String tipo, String descripcion, long monto,
                      String categoria, long fecha) {
        this(id, cuentaId, tipo, descripcion, monto, categoria, fecha, null, null, null);
    }

    public Movimiento(long id, long cuentaId, String tipo, String descripcion, long monto,
                      String categoria, long fecha, String nombreCuenta) {
        this(id, cuentaId, tipo, descripcion, monto, categoria, fecha, nombreCuenta, null, null);
    }

    public Movimiento(long id, long cuentaId, String tipo, String descripcion, long monto,
                      String categoria, long fecha, String nombreCuenta, Double latitud, Double longitud) {
        this(id, cuentaId, tipo, descripcion, monto, categoria, fecha, nombreCuenta, latitud, longitud, null);
    }

    public Movimiento(long id, long cuentaId, String tipo, String descripcion, long monto,
                      String categoria, long fecha, String nombreCuenta, Double latitud, Double longitud,
                      String direccionAproximada) {
        this(id, cuentaId, tipo, descripcion, monto, categoria, fecha, nombreCuenta, latitud, longitud,
                direccionAproximada, MEDIO_EFECTIVO);
    }

    public Movimiento(long id, long cuentaId, String tipo, String descripcion, long monto,
                      String categoria, long fecha, String nombreCuenta, Double latitud, Double longitud,
                      String direccionAproximada, String medioPago) {
        this.id = id;
        this.cuentaId = cuentaId;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.monto = monto;
        this.categoria = categoria;
        this.fecha = fecha;
        this.nombreCuenta = nombreCuenta;
        this.latitud = latitud;
        this.longitud = longitud;
        this.direccionAproximada = direccionAproximada;
        this.medioPago = medioPago == null ? MEDIO_EFECTIVO : medioPago;
    }

    public long getId() {
        return id;
    }

    public long getCuentaId() {
        return cuentaId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public long getMonto() {
        return monto;
    }

    public String getCategoria() {
        return categoria;
    }

    public long getFecha() {
        return fecha;
    }

    public String getNombreCuenta() {
        return nombreCuenta;
    }

    public Double getLatitud() {
        return latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public String getDireccionAproximada() {
        return direccionAproximada;
    }

    public String getMedioPago() {
        return medioPago;
    }
}

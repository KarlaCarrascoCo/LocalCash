package com.localcash.app.model;

/** Cuenta financiera a la que se asocian los movimientos. */
public final class Cuenta {

    public static final long ID_TODAS_LAS_CUENTAS = -1L;
    public static final String TIPO_EFECTIVO = "EFECTIVO";
    public static final String TIPO_CUENTA_CORRIENTE = "CUENTA_CORRIENTE";
    public static final String TIPO_CUENTA_VISTA = "CUENTA_VISTA";
    public static final String TIPO_AHORROS = "AHORROS";
    public static final String TIPO_TARJETA_CREDITO = "TARJETA_CREDITO";
    public static final String TIPO_CUENTA_DIGITAL = "CUENTA_DIGITAL";
    public static final String TIPO_OTRA = "OTRA";

    private final long id;
    private final String nombre;
    private final String tipo;
    private final String entidadFinanciera;

    public Cuenta(long id, String nombre, String tipo) {
        this(id, nombre, tipo, null);
    }

    public Cuenta(long id, String nombre, String tipo, String entidadFinanciera) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.entidadFinanciera = entidadFinanciera;
    }

    public long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public String getEntidadFinanciera() {
        return entidadFinanciera;
    }

    @Override
    public String toString() {
        return nombre;
    }
}

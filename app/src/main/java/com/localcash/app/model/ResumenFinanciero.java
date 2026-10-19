package com.localcash.app.model;

/** Totales calculados desde los movimientos almacenados. */
public final class ResumenFinanciero {

    private final long ingresos;
    private final long gastos;

    public ResumenFinanciero(long ingresos, long gastos) {
        this.ingresos = ingresos;
        this.gastos = gastos;
    }

    public long getIngresos() {
        return ingresos;
    }

    public long getGastos() {
        return gastos;
    }

    public long getSaldo() {
        return ingresos - gastos;
    }
}

package com.localcash.app.model;

/** Registra cuánto debe un contacto al dividir un gasto grupal. */
public final class GastoCompartido {
    private final long id;
    private final String contacto;
    private final String descripcion;
    private final long montoTotal;
    private final int participantes;
    private final long montoDeuda;

    public GastoCompartido(long id, String contacto, String descripcion, long montoTotal,
                           int participantes, long montoDeuda) {
        this.id = id; this.contacto = contacto; this.descripcion = descripcion;
        this.montoTotal = montoTotal; this.participantes = participantes; this.montoDeuda = montoDeuda;
    }
    public long getId() { return id; }
    public String getContacto() { return contacto; }
    public String getDescripcion() { return descripcion; }
    public long getMontoTotal() { return montoTotal; }
    public int getParticipantes() { return participantes; }
    public long getMontoDeuda() { return montoDeuda; }
}

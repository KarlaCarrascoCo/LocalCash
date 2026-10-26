package com.localcash.app.map;

/** Configuración única del estilo MapLibre usado por el mapa y el selector de ubicaciones. */
public final class MapStyleConfig {
    /** Estilo Liberty de OpenFreeMap, basado en OpenStreetMap/OpenMapTiles. */
    public static final String STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
    public static final double DEFAULT_LATITUDE = -33.4489d;
    public static final double DEFAULT_LONGITUDE = -70.6693d;
    public static final double DEFAULT_ZOOM = 12d;

    private MapStyleConfig() { }
}

# LocalCash

Aplicación financiera Android nativa escrita en Java y XML. Funciona sin conexión y conserva sus datos únicamente en SQLite local.

## Módulos implementados

- Pantalla de acceso con autenticación biométrica nativa antes de abrir los datos financieros.
- Cuentas financieras con nombre, tipo y entidad financiera opcional. Los saldos se calculan desde movimientos.
- Ingresos y gastos con categorías, fecha, cuenta, medio de pago y coordenadas opcionales.
- Compras planificadas con cantidad, precio estimado e historial de elementos completados.
- Agenda de contactos local y división de gastos entre varios contactos, con deuda por participante.
- Mapa local sin conexión para visualizar movimientos que ya tengan coordenadas.

## Persistencia y compatibilidad

`localcash.db` usa la versión 5. Las migraciones v1→v5 se ejecutan en transacciones y preservan identificadores y relaciones. Para una base existente, la apertura protegida crea y verifica una copia SHA-256 antes de permitir la migración. Las direcciones aproximadas guardadas en versiones anteriores se conservan; los movimientos nuevos almacenan solo coordenadas opcionales.

Las consultas de las pruebas instrumentadas usan únicamente `localcash_repository_test.db` y `localcash_migration_test.db`; no deben abrir ni modificar `localcash.db`.

> **Protección de datos:** no ejecutes `connectedDebugAndroidTest` en un emulador que contenga datos reales de LocalCash. Aunque las pruebas usan bases aisladas, el ciclo de despliegue de Android puede reemplazar o desinstalar el paquete objetivo y eliminar su almacenamiento privado. Usa un AVD desechable para pruebas instrumentadas y conserva una exportación externa antes de actualizar la aplicación.

## Verificación

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

El APK de depuración se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Publicación

Este directorio no tiene un remoto Git configurado. La creación y publicación en GitHub debe realizarse desde una cuenta autorizada.

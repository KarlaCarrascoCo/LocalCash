# LocalCash

Aplicación financiera Android nativa desarrollada con **Java** y **XML**. El package es `com.localcash.app`, el mínimo es API 26 y los datos financieros se almacenan localmente en SQLite. Las funciones financieras no requieren una cuenta ni una conexión a Internet.

## Funcionalidades incluidas

- Acceso inicial con `BiometricPrompt` y una sesión de autenticación antes de mostrar el panel financiero.
- Cuentas múltiples, ingresos y gastos, categorías, fechas, montos enteros en pesos chilenos, medios de cobro o pago, filtros, saldos e historial.
- Coordenadas opcionales por movimiento, sin exigir permiso de ubicación para consultar el mapa.
- Compras planificadas, contactos locales, gastos compartidos y deudas por participante.
- Migraciones SQLite v1 a v5, claves foráneas y respaldo con SHA-256 antes de una migración protegida.

## Mapa de movimientos

`MapActivity` usa [MapLibre Native Android](https://maplibre.org/maplibre-native/android/examples/getting-started/) 13.7.1 para mostrar en un mapa los movimientos que ya tienen latitud y longitud. Un marcador presenta descripción, tipo, monto, fecha y cuenta. Los movimientos sin coordenadas no se inventan ni aparecen en el mapa.

El estilo de muestra se obtiene desde `https://demotiles.maplibre.org/style.json`. Por eso la vista de mapa en línea necesita conexión y los permisos `INTERNET` y `ACCESS_NETWORK_STATE`. El resto de la aplicación sigue disponible sin red.

El botón **Descargar región sin conexión** inicia de forma explícita una descarga limitada alrededor de una ubicación guardada (zoom 10 a 14, ±0,015°). MapLibre conserva esos recursos en su almacenamiento de mapa. Si no hay red, no hay movimientos con coordenadas o el servidor del estilo falla, se informa el problema y los datos SQLite no se modifican. La descarga no se ejecuta automáticamente ni usa claves privadas. Su ejecución en un dispositivo real queda pendiente de validación manual.

La API de regiones sin conexión utilizada es la oficial de [MapLibre](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/-offline-manager/create-offline-region.html).

## Arquitectura

| Capa | Ubicación |
|---|---|
| Actividades y diálogos | `app/src/main/java/com/localcash/app` y `ui` |
| Modelos | `app/src/main/java/com/localcash/app/model` |
| SQLite, repositorios y respaldos | `app/src/main/java/com/localcash/app/data` |
| Interfaces y recursos | `app/src/main/res` |
| Pruebas unitarias | `app/src/test` |
| Pruebas instrumentadas aisladas | `app/src/androidTest` |

La base principal se llama `localcash.db`. Las pruebas instrumentadas deben usar exclusivamente `localcash_repository_test.db` y `localcash_migration_test.db`; nunca deben ejecutarse en un emulador que tenga datos reales, porque el despliegue del paquete puede reemplazar el almacenamiento privado de la aplicación.

## Compilación y verificación

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Para pruebas instrumentadas, usar un AVD desechable y ejecutar:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

No ejecutar esa tarea en un emulador con datos de LocalCash. No usar `pm clear`, desinstalación, Wipe Data ni restauraciones automáticas para validar una migración.

## Limitaciones conocidas

- La disponibilidad de teselas, estilo y descarga offline depende del servicio público de demostración de MapLibre y de la conectividad del dispositivo.
- La geocodificación y la ubicación son opcionales; un movimiento se guarda aun cuando no haya ubicación.
- El repositorio Git está preparado de forma local. Para publicar en GitHub se debe crear o configurar un remoto autorizado y ejecutar el `push` desde la cuenta del proyecto.
## Estructura SQLite

`DatabaseHelper` administra la versión 5 de `localcash.db`. Sus tablas principales son `cuentas`, `movimientos`, `compras`, `contactos`, `gastos_compartidos` y sus relaciones de participantes o deudas. Los saldos se obtienen desde los movimientos asociados, por lo que no existe un saldo financiero duplicado como fuente de verdad. Las migraciones v1→v5 usan transacciones, claves foráneas y controles de integridad.

## Resultado de la última verificación

El 22 de octubre de 2026 se ejecutaron correctamente `testDebugUnitTest` (1 prueba, 0 fallos), `assembleDebug` y `assembleDebugAndroidTest`. No se ejecutó `connectedDebugAndroidTest` ni se instaló o abrió la aplicación durante esta verificación, para preservar los datos del emulador. La integración de MapLibre y su descarga offline compilaron, pero requieren validación manual en un AVD desechable antes de presentarlas como verificadas en ejecución.

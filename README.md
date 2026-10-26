# LocalCash

LocalCash es una aplicación financiera móvil nativa para Android. Está desarrollada en Java y XML, y guarda los datos en SQLite local para que las operaciones financieras funcionen sin conexión a Internet.

## Tecnologías

- Android Studio
- Java 11 y XML
- SQLite local (`localcash.db`, esquema v13)
- AndroidX y Material Components
- BiometricPrompt de Android
- MapLibre Native Android 13.7.1
- SDK mínimo: API 26

## Funcionalidades

- Acceso protegido mediante autenticación biométrica.
- Cuentas financieras múltiples, ingresos, gastos, categorías, medios de pago, saldos e historial de movimientos.
- Registro opcional de coordenadas y consulta visual de movimientos geolocalizados en el mapa.
- Exportación explícita de movimientos geolocalizados a CSV.
- Agenda local de contactos para compras y gastos compartidos.
- Planificación de compras por lugar, productos, cantidades, precios, fechas y estados de compra.
- Gastos compartidos con selección de participantes, división equitativa o personalizada y deudas por contacto.
- Tema claro y oscuro persistente.

## Mapa de movimientos

`MapActivity` usa [MapLibre Native Android](https://maplibre.org/maplibre-native/android/) para mostrar los movimientos que tienen latitud y longitud. Los marcadores muestran descripción, tipo, monto, fecha y cuenta. Los movimientos sin coordenadas no se inventan ni aparecen en el mapa.

El estilo cartográfico se obtiene desde OpenFreeMap. La cartografía se carga o descarga únicamente cuando se abre el mapa o se solicita una región; las operaciones financieras siguen siendo locales y no requieren Internet.

## Arquitectura

| Capa | Ubicación |
|---|---|
| Actividades y navegación | `app/src/main/java/com/localcash/app` |
| Diálogos y componentes UI | `app/src/main/java/com/localcash/app/ui` |
| Modelos | `app/src/main/java/com/localcash/app/model` |
| SQLite, repositorios y respaldos | `app/src/main/java/com/localcash/app/data` |
| Reglas de negocio | `app/src/main/java/com/localcash/app/util` |
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
## Persistencia local

`DatabaseHelper` administra el esquema SQLite v13 y habilita claves foráneas. Las tablas principales son:

- `cuentas` y `movimientos`
- `contactos`
- `listas_compras`, `productos_compra` y `listas_contactos`
- `gastos_compartidos`
- `compras_compartidas`, `items_compartidos` y `asignaciones_compartidas`

Los saldos financieros se calculan desde los movimientos registrados; no existe un saldo duplicado.

## Validación

La aplicación fue utilizada manualmente en un Xiaomi Redmi 10C con Android 13 para verificar autenticación, cuentas, movimientos, historial, compras, contactos, gastos compartidos y navegación principal. Las pruebas unitarias y la compilación se ejecutan desde Gradle; sus resultados se conservan como reportes locales de construcción.

## Privacidad

La información financiera se almacena en SQLite dentro del almacenamiento privado de la aplicación. El repositorio excluye bases de datos, respaldos, configuraciones locales, claves de firma y artefactos de compilación. El mapa usa recursos cartográficos externos solo cuando se solicita cartografía.

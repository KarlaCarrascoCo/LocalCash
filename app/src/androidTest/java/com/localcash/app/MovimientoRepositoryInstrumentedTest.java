package com.localcash.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.localcash.app.data.CuentaRepository;
import com.localcash.app.data.DatabaseBackupManager;
import com.localcash.app.data.DatabaseHelper;
import com.localcash.app.data.MovimientoRepository;
import com.localcash.app.data.ListaCompraRepository;
import com.localcash.app.model.Cuenta;
import com.localcash.app.model.Movimiento;
import com.localcash.app.model.ResumenFinanciero;
import com.localcash.app.model.ListaCompra;
import com.localcash.app.model.ProductoCompra;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class MovimientoRepositoryInstrumentedTest {

    private static final String REPOSITORY_TEST_DATABASE = "localcash_repository_test.db";
    private static final String MIGRATION_TEST_DATABASE = "localcash_migration_test.db";

    @Test
    public void cuentasMovimientosSaldosFiltrosYRestriccionesUsanBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(REPOSITORY_TEST_DATABASE);

        try {
            CuentaRepository cuentas = new CuentaRepository(context, REPOSITORY_TEST_DATABASE);
            List<Cuenta> iniciales = cuentas.obtenerTodas();
            assertEquals(1, iniciales.size());
            long efectivoId = iniciales.get(0).getId();
            long ahorroId = cuentas.crear(new Cuenta(0, "Ahorros de prueba", Cuenta.TIPO_AHORROS));
            long cuentaVaciaId = cuentas.crear(new Cuenta(0, "Cuenta vacía", Cuenta.TIPO_OTRA));

            MovimientoRepository movimientos = new MovimientoRepository(context, REPOSITORY_TEST_DATABASE);
            long ingresoId = movimientos.insertar(new Movimiento(
                    0, efectivoId, Movimiento.TIPO_INGRESO, "Ingreso de prueba", 125000,
                    "Sueldo", 1000L));
            long gastoId = movimientos.insertar(new Movimiento(
                    0, ahorroId, Movimiento.TIPO_GASTO, "Gasto de prueba", 35000,
                    "Alimentación", 2000L));

            assertTrue(ingresoId > 0);
            assertTrue(gastoId > 0);
            assertEquals(3, cuentas.obtenerTodas().size());
            assertTrue(cuentas.eliminarSiNoTieneMovimientos(cuentaVaciaId));
            assertEquals(2, cuentas.obtenerTodas().size());
            assertFalse(cuentas.eliminarSiNoTieneMovimientos(efectivoId));

            ResumenFinanciero global = movimientos.obtenerResumen();
            assertEquals(125000, global.getIngresos());
            assertEquals(35000, global.getGastos());
            assertEquals(90000, global.getSaldo());

            ResumenFinanciero efectivo = movimientos.obtenerResumenPorCuenta(efectivoId);
            assertEquals(125000, efectivo.getSaldo());
            ResumenFinanciero ahorros = movimientos.obtenerResumenPorCuenta(ahorroId);
            assertEquals(-35000, ahorros.getSaldo());

            List<Movimiento> recientesAhorros = movimientos.obtenerRecientesPorCuenta(ahorroId, 10);
            assertEquals(1, recientesAhorros.size());
            assertEquals(gastoId, recientesAhorros.get(0).getId());
            assertEquals("Ahorros de prueba", recientesAhorros.get(0).getNombreCuenta());

            assertTrue(movimientos.eliminar(gastoId));
            assertEquals(125000, movimientos.obtenerResumen().getSaldo());

            MovimientoRepository repositorioReabierto = new MovimientoRepository(
                    context, REPOSITORY_TEST_DATABASE);
            List<Movimiento> persistidos = repositorioReabierto.obtenerRecientes(10);
            assertEquals(1, persistidos.size());
            assertEquals(ingresoId, persistidos.get(0).getId());
        } finally {
            context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        }
    }

    @Test
    public void productosDeCompraAceptanPrecioOpcionalDecimalYPersistenEnBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        try {
            ListaCompraRepository compras = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            long listaId = compras.crearLista(ListaCompra.LUGAR_FERIA, null, null, null);
            long sinPrecioId = compras.agregarProducto(listaId, new ProductoCompra(0, listaId,
                    "Producto sin precio", 1d, "UNIDADES", null, 0L, null, false));
            long naranjasId = compras.agregarProducto(listaId, new ProductoCompra(0, listaId,
                    "Naranjas", 2d, "KG", null, 700L, null, false));
            long decimalId = compras.agregarProducto(listaId, new ProductoCompra(0, listaId,
                    "Verduras", 1.5d, "KG", null, 4000L, null, false));
            assertTrue(sinPrecioId > 0); assertTrue(naranjasId > 0); assertTrue(decimalId > 0);
            assertEquals(7400L, compras.obtenerLista(listaId).getTotalEstimado());

            assertTrue(compras.iniciarCompra(listaId));
            assertTrue(compras.actualizarProducto(new ProductoCompra(naranjasId, listaId,
                    "Naranjas editadas", 2d, "KG", null, 700L, 700L, true)));
            ListaCompraRepository trasReabrir = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            assertEquals(ListaCompra.ESTADO_EN_COMPRA, trasReabrir.obtenerLista(listaId).getEstado());
            assertTrue(trasReabrir.obtenerLista(listaId).getFechaInicio() > 0);
            assertEquals(3, trasReabrir.obtenerProductos(listaId).size());
            assertEquals(700L, trasReabrir.obtenerProductos(listaId).get(2).getPrecioReal().longValue());
        } finally {
            context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        }
    }

    @Test
    public void listaProgramadaComparteRegistroEntrePendientesYProgramadasEnBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        try {
            ListaCompraRepository compras = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            long ahora = System.currentTimeMillis();
            long fechaFutura = ahora + 48L * 60L * 60L * 1000L;
            long listaId = compras.crearLista(ListaCompra.LUGAR_FERIA, null, fechaFutura, null);

            assertEquals(1, compras.obtenerPendientes(ahora).size());
            assertEquals(listaId, compras.obtenerPendientes(ahora).get(0).getId());
            assertEquals(1, compras.obtenerProgramadas(ahora).size());
            assertEquals(listaId, compras.obtenerProgramadas(ahora).get(0).getId());
            assertEquals(ListaCompra.ESTADO_PENDIENTE, compras.obtenerLista(listaId).getEstado());

            assertTrue(compras.iniciarCompra(listaId));
            ListaCompra iniciada = compras.obtenerLista(listaId);
            assertEquals(ListaCompra.ESTADO_EN_COMPRA, iniciada.getEstado());
            assertTrue(iniciada.getFechaInicio() >= ahora);
            assertEquals(fechaFutura, iniciada.getFechaProgramada().longValue());
            assertEquals(1, compras.obtenerPendientes(ahora).size());
            assertTrue(compras.obtenerProgramadas(ahora).isEmpty());
        } finally {
            context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        }
    }

    @Test
    public void eliminarHistorialEnBaseAisladaConservaOtrasListasYEliminaSusProductos() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        try {
            ListaCompraRepository compras = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            long historialId = compras.crearLista(ListaCompra.LUGAR_FERIA, null, null, null);
            long pendienteId = compras.crearLista(ListaCompra.LUGAR_FARMACIA, null, null, null);
            compras.agregarProducto(historialId, new ProductoCompra(0, historialId,
                    "Producto adquirido", 1d, "UNIDADES", null, 1000L, 1200L, true));
            assertTrue(compras.completarLista(historialId));

            assertTrue(compras.eliminarRegistroFinalizado(historialId));
            ListaCompraRepository reabierto = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            assertEquals(0, reabierto.obtenerHistorial().size());
            assertEquals(1, reabierto.obtenerPendientes(System.currentTimeMillis()).size());
            assertEquals(pendienteId, reabierto.obtenerPendientes(System.currentTimeMillis()).get(0).getId());
            assertTrue(reabierto.obtenerProductos(historialId).isEmpty());
            assertFalse(reabierto.eliminarRegistroFinalizado(pendienteId));
        } finally {
            context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        }
    }

    @Test
    public void estadosDeProductoYTotalPagadoPersistenEnBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        try {
            ListaCompraRepository compras = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            long listaId = compras.crearLista(ListaCompra.LUGAR_FERIA, null, null, null);
            long productoId = compras.agregarProducto(listaId, new ProductoCompra(0, listaId,
                    "Naranjas", 2d, "KG", null, 700L, null, ProductoCompra.ESTADO_SIN_REVISAR));
            assertTrue(compras.iniciarCompra(listaId));
            assertEquals(ProductoCompra.ESTADO_SIN_REVISAR,
                    compras.obtenerProductos(listaId).get(0).getEstado());

            assertTrue(compras.actualizarProducto(new ProductoCompra(productoId, listaId,
                    "Naranjas", 2d, "KG", null, 700L, 900L, ProductoCompra.ESTADO_COMPRADO)));
            assertEquals(1800L, compras.obtenerLista(listaId).getTotalReal());
            assertTrue(compras.actualizarEstadoProducto(productoId, ProductoCompra.ESTADO_NO_COMPRADO));
            assertEquals(0L, compras.obtenerLista(listaId).getTotalReal());
            assertTrue(compras.actualizarEstadoProducto(productoId, ProductoCompra.ESTADO_SIN_REVISAR));

            ListaCompraRepository reabierto = new ListaCompraRepository(context, REPOSITORY_TEST_DATABASE);
            assertEquals(ProductoCompra.ESTADO_SIN_REVISAR,
                    reabierto.obtenerProductos(listaId).get(0).getEstado());
            assertEquals(0L, reabierto.obtenerLista(listaId).getTotalReal());
        } finally {
            context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        }
    }

    @Test
    public void migracionVersionDiezConvierteProgramadaAPendienteSinPerderFechas() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        try {
            SQLiteDatabase versionDiez = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            versionDiez.execSQL("CREATE TABLE listas_compras (id INTEGER PRIMARY KEY, lugar_tipo TEXT NOT NULL, "
                    + "lugar_personalizado TEXT, fecha INTEGER NOT NULL, fecha_programada INTEGER, "
                    + "fecha_inicio INTEGER, estado TEXT NOT NULL, fecha_completada INTEGER)");
            versionDiez.execSQL("CREATE TABLE productos_compra (id INTEGER PRIMARY KEY, lista_id INTEGER NOT NULL, "
                    + "nombre TEXT NOT NULL, cantidad REAL NOT NULL, unidad TEXT NOT NULL, unidad_personalizada TEXT, "
                    + "precio_estimado INTEGER NOT NULL, precio_real INTEGER, adquirido INTEGER NOT NULL)");
            versionDiez.execSQL("INSERT INTO listas_compras (id, lugar_tipo, fecha, fecha_programada, estado) "
                    + "VALUES (?, ?, ?, ?, ?)", new Object[]{41L, "FERIA", 100L, 200L, "PROGRAMADA"});
            versionDiez.execSQL("INSERT INTO productos_compra (id, lista_id, nombre, cantidad, unidad, precio_estimado, precio_real, adquirido) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", new Object[]{51L, 41L, "Producto histórico", 1d, "UNIDADES", 500L, 600L, 1L});
            versionDiez.setVersion(10);
            versionDiez.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase versionOnce = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionOnce.getVersion());
            try (Cursor cursor = versionOnce.rawQuery("SELECT id, fecha, fecha_programada, estado "
                    + "FROM listas_compras WHERE id = ?", new String[]{"41"})) {
                assertTrue(cursor.moveToFirst());
                assertEquals(41L, cursor.getLong(0));
                assertEquals(100L, cursor.getLong(1));
                assertEquals(200L, cursor.getLong(2));
                assertEquals(ListaCompra.ESTADO_PENDIENTE, cursor.getString(3));
            }
            try (Cursor cursor = versionOnce.rawQuery("SELECT id, estado_producto FROM productos_compra WHERE id = ?", new String[]{"51"})) {
                assertTrue(cursor.moveToFirst());
                assertEquals(51L, cursor.getLong(0));
                assertEquals(ProductoCompra.ESTADO_COMPRADO, cursor.getString(1));
            }
            helper.close();
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    /** Las coordenadas de cada movimiento se guardan y recuperan de forma independiente. */
    @Test
    public void movimientosConCoordenadasIndependientesUsanSoloBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        try {
            CuentaRepository cuentas = new CuentaRepository(context, REPOSITORY_TEST_DATABASE);
            long cuentaId = cuentas.obtenerTodas().get(0).getId();
            MovimientoRepository movimientos = new MovimientoRepository(context, REPOSITORY_TEST_DATABASE);

            long primeroId = movimientos.insertar(new Movimiento(0, cuentaId,
                    Movimiento.TIPO_GASTO, "Ubicación uno", 1000L, "Otros", 1000L,
                    null, -33.4489d, -70.6693d, null, Movimiento.MEDIO_EFECTIVO));
            long segundoId = movimientos.insertar(new Movimiento(0, cuentaId,
                    Movimiento.TIPO_GASTO, "Ubicación dos", 2000L, "Otros", 2000L,
                    null, -33.4500d, -70.6500d, null, Movimiento.MEDIO_EFECTIVO));

            Movimiento primero = null;
            Movimiento segundo = null;
            for (Movimiento movimiento : movimientos.obtenerRecientes(10)) {
                if (movimiento.getId() == primeroId) primero = movimiento;
                if (movimiento.getId() == segundoId) segundo = movimiento;
            }
            assertTrue(primero != null);
            assertTrue(segundo != null);
            assertEquals(-33.4489d, primero.getLatitud(), 0.000001d);
            assertEquals(-70.6693d, primero.getLongitud(), 0.000001d);
            assertEquals(-33.4500d, segundo.getLatitud(), 0.000001d);
            assertEquals(-70.6500d, segundo.getLongitud(), 0.000001d);
        } finally {
            context.deleteDatabase(REPOSITORY_TEST_DATABASE);
        }
    }

    @Test
    public void falloDeRespaldoEnBaseAisladaSePropagaSinMigrar() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        File archivoEnLugarDeDirectorio = new File(context.getCacheDir(),
                "localcash-backup-blocked-" + System.nanoTime());

        try {
            SQLiteDatabase database = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            database.execSQL("CREATE TABLE movimientos (id INTEGER PRIMARY KEY, descripcion TEXT NOT NULL)");
            database.execSQL("INSERT INTO movimientos (id, descripcion) VALUES (1, 'Conservar')");
            database.setVersion(1);
            database.close();

            try (FileOutputStream output = new FileOutputStream(archivoEnLugarDeDirectorio)) {
                output.write(1);
            }
            Context contextoConDirectorioInvalido = new ContextWrapper(context) {
                @Override
                public File getFilesDir() {
                    return archivoEnLugarDeDirectorio;
                }
            };

            try {
                DatabaseBackupManager.createVerifiedBackup(
                        contextoConDirectorioInvalido, MIGRATION_TEST_DATABASE);
                fail("Un respaldo no verificable debe detener el proceso.");
            } catch (IOException expected) {
                // El llamador puede impedir que se abra o migre la base.
            }

            SQLiteDatabase sinMigrar = SQLiteDatabase.openDatabase(
                    context.getDatabasePath(MIGRATION_TEST_DATABASE).getPath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            try (Cursor cursor = sinMigrar.rawQuery(
                    "SELECT id, descripcion FROM movimientos", null)) {
                assertEquals(1, sinMigrar.getVersion());
                assertTrue(cursor.moveToFirst());
                assertEquals(1L, cursor.getLong(0));
                assertEquals("Conservar", cursor.getString(1));
            } finally {
                sinMigrar.close();
            }
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
            archivoEnLugarDeDirectorio.delete();
        }
    }

    @Test
    public void falloDeMigracionRevierteLaBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);

        try {
            SQLiteDatabase legacyDatabase = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            legacyDatabase.execSQL("CREATE TABLE movimientos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, tipo TEXT NOT NULL, "
                    + "descripcion TEXT NOT NULL, monto INTEGER NOT NULL, categoria TEXT NOT NULL, "
                    + "fecha INTEGER NOT NULL)");
            legacyDatabase.execSQL("INSERT INTO movimientos (id, tipo, descripcion, monto, categoria, fecha) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{31L, "TIPO_INVALIDO", "Debe conservarse", 77L, "Otros", 3333L});
            legacyDatabase.setVersion(1);
            legacyDatabase.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            try {
                helper.getWritableDatabase();
                fail("La fila incompatible debe hacer fallar la migración.");
            } catch (SQLiteConstraintException expected) {
                // La transacción debe revertir la creación y copia parcial.
            } finally {
                helper.close();
            }

            SQLiteDatabase original = SQLiteDatabase.openDatabase(
                    context.getDatabasePath(MIGRATION_TEST_DATABASE).getPath(), null,
                    SQLiteDatabase.OPEN_READONLY);
            try {
                assertEquals(1, original.getVersion());
                try (Cursor cursor = original.rawQuery(
                        "SELECT id, tipo, descripcion, monto, categoria, fecha FROM movimientos", null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals(31L, cursor.getLong(0));
                    assertEquals("TIPO_INVALIDO", cursor.getString(1));
                    assertEquals("Debe conservarse", cursor.getString(2));
                    assertEquals(77L, cursor.getLong(3));
                    assertEquals("Otros", cursor.getString(4));
                    assertEquals(3333L, cursor.getLong(5));
                }
                try (Cursor cursor = original.rawQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'cuentas'", null)) {
                    assertTrue(cursor.moveToFirst());
                    assertEquals(0L, cursor.getLong(0));
                }
            } finally {
                original.close();
            }
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    @Test
    public void migracionVersionUnoConservaDatosYCopiaEnBaseAislada() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        File backupDirectory = null;

        try {
            SQLiteDatabase legacyDatabase = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            legacyDatabase.execSQL("CREATE TABLE movimientos ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "tipo TEXT NOT NULL, descripcion TEXT NOT NULL, monto INTEGER NOT NULL, "
                    + "categoria TEXT NOT NULL, fecha INTEGER NOT NULL)");
            legacyDatabase.execSQL("INSERT INTO movimientos (id, tipo, descripcion, monto, categoria, fecha) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{7L, Movimiento.TIPO_INGRESO, "Ingreso histórico", 88000L,
                            "Sueldo", 1111L});
            legacyDatabase.execSQL("INSERT INTO movimientos (id, tipo, descripcion, monto, categoria, fecha) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{11L, Movimiento.TIPO_GASTO, "Gasto histórico", 21000L,
                            "Transporte", 2222L});
            legacyDatabase.setVersion(1);
            legacyDatabase.close();

            DatabaseBackupManager.BackupResult backup = DatabaseBackupManager.createVerifiedBackup(
                    context, MIGRATION_TEST_DATABASE);
            assertTrue(backup.isBackupCreated());
            assertTrue(backup.getBackupFile().exists());
            backupDirectory = backup.getBackupFile().getParentFile();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase migratedDatabase = helper.getWritableDatabase();
            try (Cursor foreignKeys = migratedDatabase.rawQuery("PRAGMA foreign_keys", null)) {
                assertTrue(foreignKeys.moveToFirst());
                assertEquals(1, foreignKeys.getInt(0));
            }
            try (Cursor cursor = migratedDatabase.rawQuery(
                    "SELECT m.id, m.tipo, m.descripcion, m.monto, m.categoria, m.fecha, "
                            + "m.cuenta_id, c.nombre "
                            + "FROM movimientos m INNER JOIN cuentas c ON m.cuenta_id = c.id "
                            + "ORDER BY m.id", null)) {
                assertEquals(2, cursor.getCount());
                assertTrue(cursor.moveToFirst());
                assertEquals(7L, cursor.getLong(0));
                assertEquals(Movimiento.TIPO_INGRESO, cursor.getString(1));
                assertEquals("Ingreso histórico", cursor.getString(2));
                assertEquals(88000L, cursor.getLong(3));
                assertEquals("Sueldo", cursor.getString(4));
                assertEquals(1111L, cursor.getLong(5));
                assertTrue(cursor.getLong(6) > 0);
                assertEquals("Cuenta inicial", cursor.getString(7));
                assertTrue(cursor.moveToNext());
                assertEquals(11L, cursor.getLong(0));
                assertEquals(Movimiento.TIPO_GASTO, cursor.getString(1));
                assertEquals("Gasto histórico", cursor.getString(2));
                assertEquals(21000L, cursor.getLong(3));
                assertEquals("Transporte", cursor.getString(4));
                assertEquals(2222L, cursor.getLong(5));
                assertTrue(cursor.getLong(6) > 0);
                assertEquals("Cuenta inicial", cursor.getString(7));
            }
            try {
                migratedDatabase.execSQL("INSERT INTO movimientos (tipo, descripcion, monto, categoria, fecha, cuenta_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                        new Object[]{Movimiento.TIPO_INGRESO, "Inválido", 1L, "Otros", 1L, 999999L});
                fail("La clave foránea debe impedir cuentas inexistentes.");
            } catch (SQLiteConstraintException expected) {
                // Relación protegida correctamente.
            }
            helper.close();
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
            deleteRecursively(backupDirectory);
        }
    }

    @Test
    public void migracionVersionDosConservaMovimientosYCreaModulosOffline() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        try {
            SQLiteDatabase versionDos = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            versionDos.execSQL("CREATE TABLE cuentas (id INTEGER PRIMARY KEY, nombre TEXT NOT NULL, tipo TEXT NOT NULL)");
            versionDos.execSQL("CREATE TABLE movimientos (id INTEGER PRIMARY KEY, tipo TEXT NOT NULL, "
                    + "descripcion TEXT NOT NULL, monto INTEGER NOT NULL, categoria TEXT NOT NULL, "
                    + "fecha INTEGER NOT NULL, cuenta_id INTEGER NOT NULL)");
            versionDos.execSQL("INSERT INTO cuentas (id, nombre, tipo) VALUES (?, ?, ?)",
                    new Object[]{4L, "Efectivo", "EFECTIVO"});
            versionDos.execSQL("INSERT INTO movimientos "
                            + "(id, tipo, descripcion, monto, categoria, fecha, cuenta_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{9L, Movimiento.TIPO_INGRESO, "Migración v2", 300L,
                            "Sueldo", 777L, 4L});
            versionDos.setVersion(2);
            versionDos.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase versionCinco = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionCinco.getVersion());
            try (Cursor cursor = versionCinco.rawQuery("SELECT id, descripcion, monto, latitud, longitud, "
                    + "direccion_aproximada, medio_pago FROM movimientos", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(9L, cursor.getLong(0));
                assertEquals("Migración v2", cursor.getString(1));
                assertEquals(300L, cursor.getLong(2));
                assertTrue(cursor.isNull(3));
                assertTrue(cursor.isNull(4));
                assertTrue(cursor.isNull(5));
                assertEquals(Movimiento.MEDIO_EFECTIVO, cursor.getString(6));
            }
            try (Cursor cursor = versionCinco.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                    + "AND name IN ('listas_compras', 'productos_compra', 'listas_contactos')", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(3L, cursor.getLong(0));
            }
            helper.close();
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    @Test
    public void migracionVersionTresConservaCuentasYMovimientosYPermiteNuevosTipos() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        try {
            SQLiteDatabase versionTres = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            versionTres.execSQL("CREATE TABLE cuentas (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT NOT NULL COLLATE NOCASE UNIQUE, tipo TEXT NOT NULL CHECK(tipo IN "
                    + "('EFECTIVO', 'CUENTA_CORRIENTE', 'CUENTA_VISTA', 'AHORROS', 'OTRA')))" );
            versionTres.execSQL("CREATE TABLE movimientos (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "tipo TEXT NOT NULL, descripcion TEXT NOT NULL, monto INTEGER NOT NULL, "
                    + "categoria TEXT NOT NULL, fecha INTEGER NOT NULL, latitud REAL, longitud REAL, "
                    + "cuenta_id INTEGER NOT NULL, FOREIGN KEY(cuenta_id) REFERENCES cuentas(id) ON DELETE RESTRICT)");
            versionTres.execSQL("CREATE TABLE compras (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT NOT NULL, cantidad INTEGER NOT NULL, completada INTEGER NOT NULL DEFAULT 0, "
                    + "fecha INTEGER NOT NULL)");
            versionTres.execSQL("CREATE TABLE contactos (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT NOT NULL, telefono TEXT)");
            versionTres.execSQL("CREATE TABLE gastos_compartidos (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "contacto_id INTEGER NOT NULL, descripcion TEXT NOT NULL, monto_total INTEGER NOT NULL, "
                    + "participantes INTEGER NOT NULL, monto_deuda INTEGER NOT NULL, fecha INTEGER NOT NULL, "
                    + "FOREIGN KEY(contacto_id) REFERENCES contactos(id) ON DELETE RESTRICT)");
            versionTres.execSQL("INSERT INTO cuentas (id, nombre, tipo) VALUES (?, ?, ?)",
                    new Object[]{4L, "BancoEstado", Cuenta.TIPO_CUENTA_VISTA});
            versionTres.execSQL("INSERT INTO movimientos "
                            + "(id, tipo, descripcion, monto, categoria, fecha, latitud, longitud, cuenta_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{9L, Movimiento.TIPO_GASTO, "Compra histórica", 300L,
                            "Alimentación", 777L, -33.45d, -70.66d, 4L});
            versionTres.execSQL("INSERT INTO compras (id, nombre, cantidad, completada, fecha) VALUES (?, ?, ?, ?, ?)",
                    new Object[]{12L, "Pan", 2L, 0L, 888L});
            versionTres.execSQL("INSERT INTO contactos (id, nombre, telefono) VALUES (?, ?, ?)",
                    new Object[]{5L, "Ana", "999999999"});
            versionTres.execSQL("INSERT INTO gastos_compartidos "
                            + "(id, contacto_id, descripcion, monto_total, participantes, monto_deuda, fecha) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{13L, 5L, "Cena", 10000L, 2L, 5000L, 999L});
            versionTres.setVersion(3);
            versionTres.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase versionCinco = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionCinco.getVersion());
            try (Cursor cursor = versionCinco.rawQuery("SELECT m.id, m.descripcion, m.monto, m.fecha, "
                    + "m.latitud, m.longitud, m.direccion_aproximada, m.medio_pago, m.cuenta_id, c.nombre, c.tipo, c.entidad_financiera "
                    + "FROM movimientos m INNER JOIN cuentas c ON c.id = m.cuenta_id", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(9L, cursor.getLong(0));
                assertEquals("Compra histórica", cursor.getString(1));
                assertEquals(300L, cursor.getLong(2));
                assertEquals(777L, cursor.getLong(3));
                assertEquals(-33.45d, cursor.getDouble(4), 0.00001d);
                assertEquals(-70.66d, cursor.getDouble(5), 0.00001d);
                assertTrue(cursor.isNull(6));
                assertEquals(Movimiento.MEDIO_EFECTIVO, cursor.getString(7));
                assertEquals(4L, cursor.getLong(8));
                assertEquals("BancoEstado", cursor.getString(9));
                assertEquals(Cuenta.TIPO_CUENTA_VISTA, cursor.getString(10));
                assertTrue(cursor.isNull(11));
            }
            try (Cursor foreignKeyCheck = versionCinco.rawQuery("PRAGMA foreign_key_check", null)) {
                assertEquals(0, foreignKeyCheck.getCount());
            }
            try (Cursor cursor = versionCinco.rawQuery("SELECT g.id, c.id, c.nombre, g.descripcion, "
                    + "g.monto_total, g.participantes, g.monto_deuda, g.fecha "
                    + "FROM gastos_compartidos g INNER JOIN contactos c ON c.id = g.contacto_id", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(13L, cursor.getLong(0));
                assertEquals(5L, cursor.getLong(1));
                assertEquals("Ana", cursor.getString(2));
                assertEquals("Cena", cursor.getString(3));
                assertEquals(10000L, cursor.getLong(4));
                assertEquals(2L, cursor.getLong(5));
                assertEquals(5000L, cursor.getLong(6));
                assertEquals(999L, cursor.getLong(7));
            }
            helper.close();

            CuentaRepository cuentas = new CuentaRepository(context, MIGRATION_TEST_DATABASE);
            long cuentaNuevaId = cuentas.crear(new Cuenta(0, "Tarjeta Local", Cuenta.TIPO_TARJETA_CREDITO));
            assertTrue(cuentaNuevaId > 0);
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    @Test
    public void migracionVersionCuatroConservaCamposYAgregaDatosDeCuentaYPago() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        try {
            SQLiteDatabase versionCuatro = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            versionCuatro.setForeignKeyConstraintsEnabled(true);
            versionCuatro.execSQL("CREATE TABLE cuentas (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT NOT NULL COLLATE NOCASE UNIQUE, tipo TEXT NOT NULL)");
            versionCuatro.execSQL("CREATE TABLE movimientos (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "tipo TEXT NOT NULL, descripcion TEXT NOT NULL, monto INTEGER NOT NULL, "
                    + "categoria TEXT NOT NULL, fecha INTEGER NOT NULL, latitud REAL, longitud REAL, "
                    + "direccion_aproximada TEXT, cuenta_id INTEGER NOT NULL, "
                    + "FOREIGN KEY(cuenta_id) REFERENCES cuentas(id) ON DELETE RESTRICT)");
            versionCuatro.execSQL("CREATE TABLE compras (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT NOT NULL, cantidad INTEGER NOT NULL, completada INTEGER NOT NULL DEFAULT 0, "
                    + "fecha INTEGER NOT NULL)");
            versionCuatro.execSQL("CREATE TABLE contactos (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "nombre TEXT NOT NULL, telefono TEXT)");
            versionCuatro.execSQL("CREATE TABLE gastos_compartidos (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "contacto_id INTEGER NOT NULL, descripcion TEXT NOT NULL, monto_total INTEGER NOT NULL, "
                    + "participantes INTEGER NOT NULL, monto_deuda INTEGER NOT NULL, fecha INTEGER NOT NULL, "
                    + "FOREIGN KEY(contacto_id) REFERENCES contactos(id) ON DELETE RESTRICT)");
            versionCuatro.execSQL("INSERT INTO cuentas (id, nombre, tipo) VALUES (?, ?, ?)",
                    new Object[]{17L, "Tarjeta prueba", Cuenta.TIPO_TARJETA_CREDITO});
            versionCuatro.execSQL("INSERT INTO movimientos (id, tipo, descripcion, monto, categoria, fecha, latitud, longitud, direccion_aproximada, cuenta_id) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new Object[]{21L, Movimiento.TIPO_GASTO, "Compra tarjeta", 45000L,
                            "Alimentación", 12345L, -33.45d, -70.66d, "Dirección histórica", 17L});
            versionCuatro.execSQL("INSERT INTO compras (id, nombre, cantidad, completada, fecha) VALUES (?, ?, ?, ?, ?)",
                    new Object[]{8L, "Leche", 2L, 1L, 3456L});
            versionCuatro.setVersion(4);
            versionCuatro.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase versionCinco = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionCinco.getVersion());
            try (Cursor cursor = versionCinco.rawQuery("SELECT m.id, m.descripcion, m.monto, m.fecha, "
                    + "m.latitud, m.longitud, m.direccion_aproximada, m.medio_pago, m.cuenta_id, "
                    + "c.nombre, c.tipo, c.entidad_financiera FROM movimientos m "
                    + "INNER JOIN cuentas c ON c.id = m.cuenta_id", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(21L, cursor.getLong(0));
                assertEquals("Compra tarjeta", cursor.getString(1));
                assertEquals(45000L, cursor.getLong(2));
                assertEquals(12345L, cursor.getLong(3));
                assertEquals(-33.45d, cursor.getDouble(4), 0.00001d);
                assertEquals(-70.66d, cursor.getDouble(5), 0.00001d);
                assertEquals("Dirección histórica", cursor.getString(6));
                assertEquals(Movimiento.MEDIO_EFECTIVO, cursor.getString(7));
                assertEquals(17L, cursor.getLong(8));
                assertEquals("Tarjeta prueba", cursor.getString(9));
                assertEquals(Cuenta.TIPO_TARJETA_CREDITO, cursor.getString(10));
                assertTrue(cursor.isNull(11));
            }
            try (Cursor foreignKeyCheck = versionCinco.rawQuery("PRAGMA foreign_key_check", null)) {
                assertEquals(0, foreignKeyCheck.getCount());
            }
            helper.close();
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    @Test
    public void migracionVersionSieteReemplazaSoloComprasPorListasRelacionadasEnBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        try {
            SQLiteDatabase versionSiete = context.openOrCreateDatabase(
                    MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            versionSiete.execSQL("CREATE TABLE contactos (id INTEGER PRIMARY KEY, nombre TEXT NOT NULL, telefono TEXT)");
            versionSiete.execSQL("CREATE TABLE compras (id INTEGER PRIMARY KEY, nombre TEXT NOT NULL, cantidad INTEGER NOT NULL, completada INTEGER NOT NULL, fecha INTEGER NOT NULL, precio_estimado INTEGER NOT NULL DEFAULT 0)");
            versionSiete.execSQL("INSERT INTO contactos (id, nombre, telefono) VALUES (?, ?, ?)",
                    new Object[]{44L, "Contacto de prueba", "900000000"});
            versionSiete.execSQL("INSERT INTO compras (id, nombre, cantidad, completada, fecha, precio_estimado) VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{17L, "Dato de prueba reemplazable", 1L, 0L, 100L, 200L});
            versionSiete.setVersion(7);
            versionSiete.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase versionOcho = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionOcho.getVersion());
            try (Cursor cursor = versionOcho.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'compras'", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals(0L, cursor.getLong(0));
            }
            versionOcho.execSQL("INSERT INTO listas_compras (id, lugar_tipo, fecha, estado) VALUES (?, ?, ?, ?)",
                    new Object[]{70L, "FERIA", 200L, "PENDIENTE"});
            versionOcho.execSQL("INSERT INTO productos_compra (lista_id, nombre, cantidad, unidad, precio_estimado, adquirido) VALUES (?, ?, ?, ?, ?, ?)",
                    new Object[]{70L, "Manzanas", 2d, "KG", 1000L, 0L});
            versionOcho.execSQL("INSERT INTO listas_contactos (lista_id, contacto_id) VALUES (?, ?)", new Object[]{70L, 44L});
            try (Cursor cursor = versionOcho.rawQuery("PRAGMA foreign_key_check", null)) {
                assertEquals(0, cursor.getCount());
            }
            try {
                versionOcho.delete("contactos", "id = ?", new String[]{"44"});
                fail("El contacto asociado a una lista no debe eliminarse.");
            } catch (SQLiteConstraintException expected) {
                // La relación con listas se protege correctamente.
            }
            helper.close();
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    @Test
    public void migracionVersionOchoRecreaSoloTablasDeComprasConEstadosEnBaseAislada() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(MIGRATION_TEST_DATABASE);
        try {
            SQLiteDatabase versionOcho = context.openOrCreateDatabase(MIGRATION_TEST_DATABASE, Context.MODE_PRIVATE, null);
            versionOcho.execSQL("CREATE TABLE contactos (id INTEGER PRIMARY KEY, nombre TEXT NOT NULL, telefono TEXT)");
            versionOcho.execSQL("CREATE TABLE listas_compras (id INTEGER PRIMARY KEY, lugar_tipo TEXT NOT NULL, lugar_personalizado TEXT, fecha INTEGER NOT NULL, fecha_programada INTEGER, completada INTEGER NOT NULL, fecha_completada INTEGER)");
            versionOcho.execSQL("CREATE TABLE productos_compra (id INTEGER PRIMARY KEY, lista_id INTEGER NOT NULL, nombre TEXT NOT NULL, cantidad REAL NOT NULL, unidad TEXT NOT NULL, unidad_personalizada TEXT, precio_estimado INTEGER NOT NULL, precio_real INTEGER, comprado INTEGER NOT NULL)");
            versionOcho.execSQL("CREATE TABLE listas_contactos (lista_id INTEGER NOT NULL, contacto_id INTEGER NOT NULL)");
            versionOcho.execSQL("INSERT INTO contactos (id, nombre) VALUES (?, ?)", new Object[]{9L, "Contacto conservado"});
            versionOcho.execSQL("INSERT INTO listas_compras (id, lugar_tipo, fecha, completada) VALUES (?, ?, ?, ?)", new Object[]{1L, "FERIA", 100L, 0L});
            versionOcho.setVersion(8);
            versionOcho.close();

            DatabaseHelper helper = new DatabaseHelper(context, MIGRATION_TEST_DATABASE);
            SQLiteDatabase versionNueve = helper.getWritableDatabase();
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionNueve.getVersion());
            try (Cursor cursor = versionNueve.rawQuery("SELECT nombre FROM contactos WHERE id = 9", null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("Contacto conservado", cursor.getString(0));
            }
            try (Cursor cursor = versionNueve.rawQuery("PRAGMA table_info(listas_compras)", null)) {
                boolean hasEstado = false; while (cursor.moveToNext()) if ("estado".equals(cursor.getString(1))) hasEstado = true;
                assertTrue(hasEstado);
            }
            try (Cursor cursor = versionNueve.rawQuery("PRAGMA foreign_key_check", null)) { assertEquals(0, cursor.getCount()); }
            helper.close();
        } finally {
            context.deleteDatabase(MIGRATION_TEST_DATABASE);
        }
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

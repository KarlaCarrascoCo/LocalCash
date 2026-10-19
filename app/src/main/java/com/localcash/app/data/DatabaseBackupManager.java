package com.localcash.app.data;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Crea una copia verificada de una base existente antes de abrirla con un esquema nuevo. */
public final class DatabaseBackupManager {

    private static final String BACKUP_DIRECTORY = "database_backups";
    private static final int BUFFER_SIZE = 8192;

    private DatabaseBackupManager() {
    }

    public static BackupResult createVerifiedBackup(Context context) throws IOException {
        return createVerifiedBackup(context, DatabaseHelper.DATABASE_NAME);
    }

    /** Permite usar una base aislada al probar copias y migraciones. */
    public static BackupResult createVerifiedBackup(Context context, String databaseName) throws IOException {
        File databaseFile = context.getDatabasePath(databaseName);
        if (!databaseFile.exists()) {
            return BackupResult.noDatabase();
        }

        File backupsRoot = new File(context.getFilesDir(), BACKUP_DIRECTORY);
        if (!backupsRoot.exists() && !backupsRoot.mkdirs()) {
            throw new IOException("No fue posible crear el directorio de copias de seguridad.");
        }

        File backupDirectory = new File(backupsRoot, databaseName + "-" + UUID.randomUUID());
        if (!backupDirectory.mkdir()) {
            throw new IOException("No fue posible reservar una copia de seguridad única.");
        }

        File backupDatabase = new File(backupDirectory, databaseName);
        copyAndVerify(databaseFile, backupDatabase);
        copySidecarIfPresent(databaseFile, backupDirectory, "-journal");
        copySidecarIfPresent(databaseFile, backupDirectory, "-wal");
        copySidecarIfPresent(databaseFile, backupDirectory, "-shm");
        return BackupResult.created(backupDatabase);
    }

    private static void copySidecarIfPresent(File databaseFile, File backupDirectory, String suffix)
            throws IOException {
        File source = new File(databaseFile.getPath() + suffix);
        if (source.exists()) {
            copyAndVerify(source, new File(backupDirectory, databaseFile.getName() + suffix));
        }
    }

    private static void copyAndVerify(File source, File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
        }

        if (source.length() != destination.length() || !sameDigest(source, destination)) {
            throw new IOException("La copia de seguridad no pudo verificarse.");
        }
    }

    private static boolean sameDigest(File first, File second) throws IOException {
        try {
            return MessageDigest.isEqual(calculateDigest(first), calculateDigest(second));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 no está disponible para verificar la copia.", exception);
        }
    }

    private static byte[] calculateDigest(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    public static final class BackupResult {
        private final boolean backupCreated;
        private final File backupFile;

        private BackupResult(boolean backupCreated, File backupFile) {
            this.backupCreated = backupCreated;
            this.backupFile = backupFile;
        }

        static BackupResult noDatabase() {
            return new BackupResult(false, null);
        }

        static BackupResult created(File backupFile) {
            return new BackupResult(true, backupFile);
        }

        public boolean isBackupCreated() {
            return backupCreated;
        }

        public File getBackupFile() {
            return backupFile;
        }
    }
}

package com.visionmapping.excel;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * V10 (SEC-04-06): pre-import backup snapshots hold a person's whole vision map,
 * so they are kept only as long as they are useful for undoing a bad import:
 * {@code app.excel.backup-retention-days} (30 by default), then deleted daily.
 *
 * <p>Only files this app wrote ({@link #FILE_PREFIX}…{@link #FILE_SUFFIX}) directly
 * in the backup folder are touched; anything else there is left alone.
 */
@Slf4j
@Component
public class ExcelBackupCleanupJob {

    static final String FILE_PREFIX = "vision-mapping-backup-";
    static final String FILE_SUFFIX = ".xlsx";

    private final Path directory;
    private final Duration retention;
    private final Clock clock;

    public ExcelBackupCleanupJob(
            @Value("${app.excel.backup-dir:data/backup}") String directory,
            @Value("${app.excel.backup-retention-days:30}") long retentionDays,
            Clock clock) {
        if (retentionDays < 1) {
            throw new IllegalStateException("EXCEL_BACKUP_RETENTION_DAYS must be at least 1.");
        }
        this.directory = Path.of(directory);
        this.retention = Duration.ofDays(retentionDays);
        this.clock = clock;
    }

    /** Daily at 03:45 server time. */
    @Scheduled(cron = "${app.excel.backup-cleanup-cron:0 45 3 * * *}")
    public void scheduledCleanup() {
        deleteOldBackups();
    }

    /** @return how many snapshots were deleted */
    public int deleteOldBackups() {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(retention);
        int deleted = 0;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, FILE_PREFIX + "*" + FILE_SUFFIX)) {
            for (Path file : files) {
                if (isOlderThan(file, cutoff) && delete(file)) {
                    deleted++;
                }
            }
        } catch (IOException exception) {
            log.warn("Could not list the Excel backup folder: {}", exception.toString());
        }
        if (deleted > 0) {
            log.info("Deleted {} Excel backup snapshots older than {} days", deleted, retention.toDays());
        }
        return deleted;
    }

    private static boolean isOlderThan(Path file, Instant cutoff) {
        try {
            return Files.isRegularFile(file) && Files.getLastModifiedTime(file).toInstant().isBefore(cutoff);
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Could not delete an old Excel backup: {}", exception.toString());
            return false;
        }
    }
}

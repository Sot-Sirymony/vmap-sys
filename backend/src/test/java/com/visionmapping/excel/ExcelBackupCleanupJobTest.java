package com.visionmapping.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** V10 (SEC-04-06): pre-import snapshots are deleted after the retention period. */
class ExcelBackupCleanupJobTest {

    private static final Instant NOW = Instant.parse("2026-11-22T03:45:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path folder;

    @Test
    void deletesSnapshotsOlderThanTheRetentionAndKeepsNewerOnes() throws IOException {
        Path old = snapshot("vision-mapping-backup-20261001-101010-aaaa0001.xlsx", Duration.ofDays(31));
        Path recent = snapshot("vision-mapping-backup-20261120-101010-aaaa0002.xlsx", Duration.ofDays(2));

        int deleted = job(30).deleteOldBackups();

        assertThat(deleted).isEqualTo(1);
        assertThat(old).doesNotExist();
        assertThat(recent).exists();
    }

    @Test
    void leavesFilesItDidNotWriteAlone() throws IOException {
        Path other = snapshot("notes.xlsx", Duration.ofDays(90));
        Path original = snapshot("Vision_Mapping_System_Advanced.xlsx", Duration.ofDays(90));
        Files.createDirectory(folder.resolve("vision-mapping-backup-folder.xlsx"));

        assertThat(job(30).deleteOldBackups()).isZero();
        assertThat(other).exists();
        assertThat(original).exists();
    }

    @Test
    void aMissingFolderIsNotAnError() {
        ExcelBackupCleanupJob job = new ExcelBackupCleanupJob(folder.resolve("nope").toString(), 30, clock);

        assertThat(job.deleteOldBackups()).isZero();
    }

    @Test
    void refusesARetentionUnderOneDay() {
        assertThatThrownBy(() -> new ExcelBackupCleanupJob(folder.toString(), 0, clock))
                .isInstanceOf(IllegalStateException.class);
    }

    private ExcelBackupCleanupJob job(long days) {
        return new ExcelBackupCleanupJob(folder.toString(), days, clock);
    }

    private Path snapshot(String name, Duration age) throws IOException {
        Path file = Files.writeString(folder.resolve(name), "x");
        Files.setLastModifiedTime(file, FileTime.from(NOW.minus(age)));
        return file;
    }
}

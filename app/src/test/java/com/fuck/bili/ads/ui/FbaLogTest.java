package com.fuck.bili.ads.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FbaLogTest {

    @Test
    public void budgetIsOneHundredKilobytes() {
        assertEquals(100 * 1024L, FbaLog.MAX_BYTES);
    }

    @Test
    public void trimmingOverwritesOldDataAndKeepsNewestTail() throws Exception {
        File file = File.createTempFile("fba-log", ".txt");
        try {
            byte[] bytes = new byte[5000];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) ('a' + (i % 26));
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(bytes);
            }
            FbaLog.trimToBudget(file, 1024);
            assertEquals(1024, file.length());
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            String original = new String(bytes, StandardCharsets.UTF_8);
            assertEquals(original.substring(original.length() - 1024), text);
        } finally {
            assertTrue(!file.exists() || file.delete());
        }
    }

    @Test
    public void smallFileIsNotRewritten() throws Exception {
        File file = File.createTempFile("fba-log-small", ".txt");
        try {
            Files.write(file.toPath(), "newest log".getBytes(StandardCharsets.UTF_8));
            FbaLog.trimToBudget(file, 1024);
            assertEquals("newest log", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } finally {
            assertTrue(!file.exists() || file.delete());
        }
    }
}

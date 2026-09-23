package dhopm.common.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTest {

    @Test
    void offByDefaultAndSilent() {
        assertFalse(Log.isEnabled());
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Log.setOut(new PrintStream(buf));
        Log.setEnabled(false);
        Log.log("should-not-appear");
        Log.time("should-not-appear", System.nanoTime());
        assertFalse(buf.toString().contains("should-not-appear"));
        Log.setEnabled(false);
    }

    @Test
    void writesWhenEnabled() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Log.setOut(new PrintStream(buf));
        Log.setEnabled(true);
        try {
            Log.log("hello");
            assertTrue(buf.toString().contains("hello"));
        } finally {
            Log.setEnabled(false);
        }
    }
}
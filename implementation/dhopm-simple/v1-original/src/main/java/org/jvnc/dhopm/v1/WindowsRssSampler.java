package org.jvnc.dhopm.v1;

import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class WindowsRssSampler implements AutoCloseable {
    private static final long INTERVAL_MILLIS = 20;

    private final long processId;
    private final AtomicBoolean sampling = new AtomicBoolean();
    private final AtomicLong startBytes = new AtomicLong(-1);
    private final AtomicLong endBytes = new AtomicLong(-1);
    private final AtomicLong peakBytes = new AtomicLong(-1);
    private final AtomicLong sampleCount = new AtomicLong();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "worker-rss-sampler");
        thread.setDaemon(true);
        return thread;
    });
    private volatile String status;
    private ScheduledFuture<?> samplingTask;

    WindowsRssSampler(long processId) {
        this.processId = processId;
        this.status = isWindows() ? "available" : "unsupported";
    }

    synchronized void startPhase() {
        if (!isWindows()) {
            return;
        }
        sampling.set(true);
        long first = sample();
        startBytes.set(first);
        endBytes.set(first);
        peakBytes.set(first);
        sampleCount.set(first >= 0 ? 1 : 0);
        if (samplingTask != null) {
            samplingTask.cancel(false);
        }
        samplingTask = executor.scheduleAtFixedRate(this::sampleSafely, INTERVAL_MILLIS,
                INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    synchronized Metrics finishPhase() {
        if (!isWindows()) {
            return new Metrics(null, null, null, 0, INTERVAL_MILLIS, "windows-working-set", status);
        }
        sampling.set(false);
        if (samplingTask != null) {
            samplingTask.cancel(false);
        }
        long last = sample();
        endBytes.set(last);
        if (last >= 0) {
            peakBytes.accumulateAndGet(last, Math::max);
            sampleCount.incrementAndGet();
        }
        String finalStatus = status;
        return new Metrics(nullable(startBytes.get()), nullable(endBytes.get()),
                nullable(peakBytes.get()), sampleCount.get(), INTERVAL_MILLIS,
                "windows-working-set", finalStatus);
    }

    private synchronized void sampleSafely() {
        if (sampling.get()) {
            long value = sample();
            if (value >= 0) {
                endBytes.set(value);
                peakBytes.accumulateAndGet(value, Math::max);
                sampleCount.incrementAndGet();
            }
        }
    }

    private synchronized long sample() {
        WinNT.HANDLE handle = null;
        try {
            handle = Kernel32.INSTANCE.OpenProcess(
                    WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ, false, (int) processId);
            if (handle == null) {
                status = "error: OpenProcess failed with " + Kernel32.INSTANCE.GetLastError();
                return -1;
            }
            ProcessMemoryCounters counters = new ProcessMemoryCounters();
            counters.cb = new WinDef.DWORD(counters.size());
            if (!Psapi.INSTANCE.GetProcessMemoryInfo(handle, counters, counters.size())) {
                status = "error: GetProcessMemoryInfo failed with " + Kernel32.INSTANCE.GetLastError();
                return -1;
            }
            counters.read();
            return counters.WorkingSetSize.longValue();
        } catch (RuntimeException | LinkageError ex) {
            status = "error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return -1;
        } finally {
            if (handle != null) {
                Kernel32.INSTANCE.CloseHandle(handle);
            }
        }
    }

    private static Long nullable(long value) {
        return value < 0 ? null : value;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Override
    public void close() {
        sampling.set(false);
        if (samplingTask != null) {
            samplingTask.cancel(false);
        }
        executor.shutdownNow();
    }

    record Metrics(Long rssStartBytes, Long rssEndBytes, Long rssPeakSampledBytes,
                   long rssSampleCount, long rssSamplingIntervalMs,
                   String rssMetric, String rssStatus) {
    }

    private interface Psapi extends StdCallLibrary {
        Psapi INSTANCE = Native.load("psapi", Psapi.class);

        boolean GetProcessMemoryInfo(WinNT.HANDLE process, ProcessMemoryCounters counters, int size);
    }

    @Structure.FieldOrder({"cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
            "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
            "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage"})
    public static final class ProcessMemoryCounters extends Structure {
        public WinDef.DWORD cb;
        public WinDef.DWORD PageFaultCount;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T PeakWorkingSetSize;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T WorkingSetSize;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaPeakPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaPeakNonPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T QuotaNonPagedPoolUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T PagefileUsage;
        public com.sun.jna.platform.win32.BaseTSD.SIZE_T PeakPagefileUsage;
    }
}

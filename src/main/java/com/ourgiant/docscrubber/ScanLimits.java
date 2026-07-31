package com.ourgiant.docscrubber;

/** Resource limits applied before/while scanning a document — kept in one place so the enforced value and its user-facing description can never drift apart. */
public final class ScanLimits {

    /**
     * Generous relative to any real-world PDF/docx/text document (even ones dense with embedded
     * images run well under this), but bounded so a single oversized file can't tie up memory/CPU
     * indefinitely on what is otherwise a per-file, single-threaded parse.
     */
    public static final long MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024;

    private ScanLimits() {
    }

    public static String describeMaxFileSize() {
        return formatSize(MAX_FILE_SIZE_BYTES);
    }

    public static String formatSize(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        // Whole numbers print clean ("100 MB"); anything else keeps enough precision that a file
        // only slightly over the limit doesn't round to display as exactly equal to the limit.
        if (mb == Math.floor(mb)) {
            return String.format("%.0f MB", mb);
        }
        return String.format("%.2f MB", mb);
    }
}

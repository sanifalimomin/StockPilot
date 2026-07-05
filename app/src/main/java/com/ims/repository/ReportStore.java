package com.ims.repository;

import java.util.List;

/** Port for storing/listing generated reports. Local filesystem or S3. */
public interface ReportStore {

    /** Store report content, returning its descriptor (location + optional download URL). */
    ReportDescriptor store(String reportId, String filename, byte[] content);

    /** List previously generated report descriptors. */
    List<ReportDescriptor> list();

    /**
     * @param filename    logical file name (e.g. "valuation.csv") identifying the report type
     * @param downloadUrl time-limited presigned GET URL (S3), or null when the
     *                    store has no URL concept (local filesystem).
     */
    record ReportDescriptor(String reportId, String filename, String location, long sizeBytes, String downloadUrl) {
    }

    /**
     * Stored object names are "&lt;uuid&gt;-&lt;filename&gt;". The report id is a
     * 36-char UUID (which itself contains dashes), so split at position 36.
     */
    static String[] splitStoredName(String name) {
        if (name.length() > 37 && name.charAt(36) == '-') {
            return new String[]{name.substring(0, 36), name.substring(37)};
        }
        int dash = name.indexOf('-');
        return dash > 0
                ? new String[]{name.substring(0, dash), name.substring(dash + 1)}
                : new String[]{name, name};
    }
}

package com.ims.repository;

import java.util.List;

/** Port for storing/listing generated reports. Local filesystem or S3. */
public interface ReportStore {

    /** Store report content, returning its location (path or s3 uri). */
    String store(String reportId, String filename, byte[] content);

    /** List previously generated report descriptors. */
    List<ReportDescriptor> list();

    record ReportDescriptor(String reportId, String location, long sizeBytes) {
    }
}

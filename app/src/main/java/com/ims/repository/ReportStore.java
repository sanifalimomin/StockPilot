package com.ims.repository;

import java.util.List;

public interface ReportStore {

    ReportDescriptor store(String reportId, String filename, byte[] content);

    List<ReportDescriptor> list();

    record ReportDescriptor(String reportId, String filename, String location, long sizeBytes, String downloadUrl) {
    }

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

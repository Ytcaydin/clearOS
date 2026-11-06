package com.clearos.ultra;

import java.io.File;
import java.util.Locale;

/** Immutable per-scan settings. No preferences or I/O in the per-file hot path. */
public final class ExcludedPaths {
    private final boolean dcim, documents, download;
    public ExcludedPaths(boolean dcim, boolean documents, boolean download) {
        this.dcim=dcim;this.documents=documents;this.download=download;
    }
    public boolean excludes(File file) {
        String p="/"+file.getAbsolutePath().replace('\\','/').toLowerCase(Locale.ROOT)+"/";
        return (dcim && p.contains("/dcim/"))
                || (documents && p.contains("/documents/"))
                || (download && (p.contains("/download/") || p.contains("/downloads/")));
    }
}

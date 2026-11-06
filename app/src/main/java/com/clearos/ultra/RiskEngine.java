package com.clearos.ultra;

import java.util.Locale;

/** Safety-first local rules; scores indicate review risk, NOT malware detection. */
public final class RiskEngine {
    private RiskEngine() {}
    public enum Level { LOW("Düşük risk"), REVIEW("İncele"), HIGH("Önemli dosya olabilir");
        public final String label; Level(String label){this.label=label;}}
    public static Level risk(SafeScanner.Candidate c) {
        String p=c.file.getAbsolutePath().toLowerCase(Locale.ROOT).replace('\\','/');
        if (c.type==SafeScanner.Type.SIMILAR||c.type==SafeScanner.Type.BLURRY
                ||c.type==SafeScanner.Type.MESSENGER||c.type==SafeScanner.Type.SCREENSHOT) return Level.HIGH;
        if(p.contains("/dcim/")||p.contains("/documents/")||p.contains("/pictures/")
                ||p.contains("/android/media/")) return Level.HIGH;
        if(c.type==SafeScanner.Type.DUPLICATE && c.duplicateKeep!=null) return Level.LOW;
        if(c.type==SafeScanner.Type.TEMP && c.suggested) return Level.LOW;
        if(c.type==SafeScanner.Type.INSTALLER && c.suggested) return Level.LOW;
        return Level.REVIEW;
    }
}

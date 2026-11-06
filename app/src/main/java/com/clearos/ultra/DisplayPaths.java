package com.clearos.ultra;

/** Display-only storage path mapping: never use a shortened label for filesystem operations. */
public final class DisplayPaths {
    private DisplayPaths() {}
    public static String label(String value) {
        if(value==null || value.isEmpty())return "Bilinmeyen konum";
        String p=value.replace('\\','/');
        if(p.startsWith("/storage/emulated/0/"))return "Dahili depolama / "+p.substring(20);
        if(p.equals("/storage/emulated/0"))return "Dahili depolama";
        if(p.startsWith("0/"))return "Dahili depolama / "+p.substring(2);
        if(p.equals("0"))return "Dahili depolama";
        if(p.startsWith("/storage/"))return "Harici depolama / "+p.substring(9);
        return p;
    }
}

package de.rptu.cs.exclaim.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum PreviewFileType {
    Text, Image, PDF, NoPreview;

    public static final Map<String, String> LANG_CLASS_MAPPING = Map.of(
        "java", "lang-java",
        "m", "lang-matlab",
        "fs", "lang-ml",
        "fsi", "lang-ml",
        "c", "lang-c",
        "h", "lang-c",
        "cpp", "lang-cpp",
        "py", "lang-python",
        "ipynb", "lang-js"
    );
    private static final Map<String, PreviewFileType> EXTENSION_TYPE_MAPPING;

    static {
        Map<String, PreviewFileType> extensionTypeMapping = new HashMap<>();
        for (String ext : LANG_CLASS_MAPPING.keySet()) {
            extensionTypeMapping.put(ext, PreviewFileType.Text);
        }
        extensionTypeMapping.put("fsproj", PreviewFileType.Text);
        extensionTypeMapping.put("txt", PreviewFileType.Text);
        extensionTypeMapping.put("md", PreviewFileType.Text);
        extensionTypeMapping.put("jpg", PreviewFileType.Image);
        extensionTypeMapping.put("jpeg", PreviewFileType.Image);
        extensionTypeMapping.put("png", PreviewFileType.Image);
        extensionTypeMapping.put("pdf", PreviewFileType.PDF);
        EXTENSION_TYPE_MAPPING = Collections.unmodifiableMap(extensionTypeMapping);
    }

    public static PreviewFileType byExtension(String extension) {
        return EXTENSION_TYPE_MAPPING.getOrDefault(extension.toLowerCase(Locale.ROOT), PreviewFileType.NoPreview);
    }

    public static PreviewFileType byFilename(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        String extension = dotIndex < 0 ? "" : filename.substring(dotIndex + 1);
        return byExtension(extension);
    }
}

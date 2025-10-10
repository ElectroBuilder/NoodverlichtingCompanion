
package nl.mikekemmink.noodverlichting.nen3140.export;

import java.util.HashSet;
import java.util.Set;

public final class ExportOptions {
    /** Laat leeg om alle locaties te exporteren */
    public final Set<String> locationIds = new HashSet<>();

    /** Downscale: lange zijde (in pixels). */
    public int maxLongEdgePx = 1600;

    /** JPEG kwaliteit (70–90 is een goede range). */
    public int jpegQuality = 80;

    /** EXIF strippen (oriëntatie toepassen, daarna zonder EXIF schrijven). */
    public boolean stripExif = true;

    /** Gebruik disk-cache voor getranscodeerde foto’s (aanbevolen voor snelheid). */
    public boolean useDiskCache = true;
}

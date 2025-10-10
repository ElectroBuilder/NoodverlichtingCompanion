package nl.mikekemmink.noodverlichting.nen3140.export;

import java.util.HashSet;
import java.util.Set;
import androidx.annotation.Nullable;

public final class ExportOptions {
    /** Laat leeg om alle locaties te exporteren */
    public final Set<String> locationIds = new HashSet<>();

    /** Downscale: lange zijde (in pixels). */
    public int maxLongEdgePx = 1600;

    /** JPEG kwaliteit (70–90 is een goede range). */
    public int jpegQuality = 80;

    /** EXIF strippen (oriëntatie toepassen, daarna zonder EXIF schrijven). */
    public boolean stripExif = true;

    /** Gebruik disk-cache voor getranscodeerde foto’s. */
    public boolean useDiskCache = true;

    /**
     * (Nieuw) Basisnaam voor de export ZIP.
     * Voorbeeld: "Locatie A" -> "Locatie A_20251010_161955.zip".
     * Als null: wordt afgeleid uit locations.json of valt terug naar "nen3140_export".
     */
    @Nullable public String outputBaseName = null;
}
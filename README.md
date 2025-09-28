
# Noodverlichting Companion (Java + XML)

Deze Android-app is een **veld-companion** voor jouw inspectieapp. Doel: per **locatie** en per **armatuur (inspectie_id)** snel een **gebrek** en **foto** vastleggen en later als **exportpakket (.zip)** importeren in de desktopapp.

## Belangrijkste principes
- De app gebruikt een **read-only kopie** van je `inspecties`-database om lijsten te tonen (locaties/armaturen).
- Nieuwe gegevens worden lokaal weggeschreven in `field.db` → tabel `gebreken` (`inspectie_id`, `omschrijving`, `datum`, `foto_pad`).
- Export maakt een zip met `mutaties.json` + map `photos/` met de fotobestanden.

## Mappen & bestanden
- **Inspecties DB**: `Android/data/<package>/databases/inspecties.db` (importeer je via de app).
- **Mutaties** (foto's + json): `Android/data/<package>/files/export/`.

## Snel starten
1. Open dit project in **Android Studio** (Java, klassieke XML-layouts).
2. Laat Gradle syncen en builden.
3. Installeer op je toestel.
4. Start de app → **Inspecties DB importeren** en kies jouw `inspecties.db` (een export vanuit de desktopapp).
5. Tik op **Start (Locaties)** → kies locatie → armatuur → **Gebrek + foto** toevoegen.
6. Terug op het hoofdscherm → **Exporteer pakket (.zip)**. Het zip-bestand verschijnt in `.../Android/data/{package}/files/export/`.

## JSON-formaat (`mutaties.json`)
```json
{
  "schema": 1,
  "generated_at": "YYYY-MM-DDTHH:mm:ss",
  "defects": [
    {
      "id_local": 1,
      "inspectie_id": 123,
      "omschrijving": "Kapotte lamp",
      "datum": "2025-09-25",
      "foto_bestand": "gebrek_123_20250925_101010.jpg"
    }
  ]
}
```

## Desktop-import (pseudo)
- Pak het zip uit naar een tijdelijke map.
- Lees `mutaties.json`.
- Voor elk item: `INSERT INTO gebreken (inspectie_id, omschrijving, datum, foto_pad) VALUES (?,?,?,?)` waarbij `foto_pad` wijst naar `data/fotos/<bestandsnaam>` en kopieer de foto daarheen.
- **Let op:** dit schema/kolomnamen sluiten aan op jouw app (`gebreken`, `inspectie_id`, `foto_pad`).

## Bekende SQL uit jouw app
- Locaties-lijst: `SELECT DISTINCT "Locatie" FROM inspecties WHERE "Locatie" IS NOT NULL AND TRIM("Locatie") <> '' ORDER BY "Locatie"` (zelfde query wordt hier gebruikt).
- `gebreken`-tabel heeft o.a. kolommen `inspectie_id`, `omschrijving`, `datum`, `foto_pad` (de app schrijft in dit formaat).

## Toestemmingen
- **Camera** (foto maken) en **READ_MEDIA_IMAGES** (Android 13+) voor gallerij.
- Foto's worden opgeslagen onder de app-specifieke **external files** map en automatisch in de export opgenomen.

## Wat je nog kunt uitbreiden
- Keuzelijst **"Omschrijving gebrek"** uit jouw `config.json` exporteren en hier inladen.
- Validatie dat `inspectie_id` bestaat in `inspecties.db`.
- UI voor batch-selectie / offline synchronisatie.

— gegenereerd: 2025-09-25 07:18

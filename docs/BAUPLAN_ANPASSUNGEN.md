# Bauplan bearbeiten und je Datei merken

## Block als erledigt ausblenden

Im Spiel einen Bauplan-Block anvisieren und **H** drücken. Die Aktion entfernt ihn
aus der Vorschau, zählt ihn für Fortschritt und Ebenenwechsel als erledigt und
nimmt ihn aus dem offenen Materialbedarf. Das gilt auch, wenn an der Stelle Luft
ist. Lazy-Bauen überspringt die Stelle. Echte Weltblöcke werden nicht abgebaut.

Die Taste ist unter **B → Platzierung → Block als erledigt ausblenden** oder in
Minecrafts Steuerung einstellbar, auch auf eine Maus-Seitentaste. Es genügt der
Tastendruck bzw. der Klick der zugewiesenen Maustaste. Es wird die nächste sichtbare
Bauplan-Zelle im Blickstrahl bearbeitet; keine Auswahl durch echte Wände und keine
Bearbeitung ungeladener Stellen. Die bestehende Taste N für gewollte Abweichungen
behält ihre bisherige Bedeutung.

**Letztes Ausblenden rückgängig** holt im aktuellen Baulauf entfernte Stellen einzeln
zurück. Der Rückgängig-Verlauf endet beim Datei-/Weltwechsel; die Ausblendungen
selbst bleiben gespeichert.

## Baupläne wechseln

Jede Bauplandatei besitzt pro Welt/Server ihren eigenen gespeicherten Stand:
Position, Dimension, Drehung, Spiegelung, Ebenenmodus/-nummer, Sichtbarkeit,
Materialtausch, übernommene Abweichungen und ausgeblendete Blöcke. A laden, anpassen,
B laden und danach A erneut wählen stellt A wieder so her, wie es eingestellt war.
Gleichnamige Dateien in verschiedenen Ordnern bleiben getrennt. Entladen beendet
nur die aktive Vorschau und bewahrt die Anpassungen für später auf.

Die bisherigen Weltdateien unter `config/examplemod/blueprints/<Welt>.json` bleiben
der Verweis auf den zuletzt aktiven Plan. Daneben speichert `<Welt>.json.plans/`
die Stände je Datei, benannt anhand des normalisierten Dateipfads. Alte
Einzel-Speicherstände werden beim Laden übernommen. Dateioperationen laufen seriell
im Hintergrund und nutzen den vorhandenen atomischen Konfigurationsschreiber.

Die Originaldatei im Schematics-Ordner bleibt unverändert. Wird ihr Inhalt extern
geändert, bleiben Position und Ansichtsmodus erhalten; blockbezogene Anpassungen
werden mit Hinweis zurückgesetzt, damit gespeicherte Zellnummern nicht versehentlich
andere Blöcke treffen. Vorhandene Anpassungen werden dabei als
`.before-source-change-<ID>.bak` gesichert.

## Original wiederherstellen

Unter **B → Platzierung → Originalblöcke wiederherstellen** zweimal innerhalb von
vier Sekunden klicken. Dadurch werden ausgeblendete Blöcke, akzeptierte Abweichungen
und Materialtausch des aktiven Plans zurückgesetzt. Position, Drehung, Spiegelung
und Ebene bleiben erhalten. Andere Baupläne bleiben unverändert.

## Prüfung

`blueprintSavedStatesTest` gehört zu Gradle `check`: getrennte Dateien/Welten,
normalisierte Pfade, A → B → A, Ausblendungen und Materialtausch, Altformat,
geänderte Quelldatei, Rücksetzen und beschädigte Speicherstände.

Sicherung: `.build-tmp/blueprint-edits-20261014/before.zip`.
Im Spiel noch prüfen: H auf Luft-Geisterblock, Fortschritt/Materialbedarf und
Lazy-Bauen, Maus-Seitentaste, Rückgängig, vollständiges Original, A → B → A und
Weltneustart. Der vollständige Gradle-Build war zuletzt durch die lokale
Loopback-Verbindung blockiert; eine direkte Java-Kompilierung ersetzt keinen Spieltest.

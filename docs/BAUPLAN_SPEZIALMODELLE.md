# Truhen und Schilder in der Bauplan-Vorschau

Änderung vom 13.10.2026: Truhen, Redstone-Truhen, Endertruhen sowie Stand-, Wand-
und Hängeschilder werden mit Minecraft-Modellen und Texturen dargestellt.
Doppeltruhen verwenden die geplante linke/rechte Hälfte, unabhängig davon, ob
die Nachbarhälfte schon in der Welt steht. Schilder verwenden den nativen Renderer
für Holzart, Drehung und Befestigung. Schildtexte werden weiterhin nicht aus
Bauplandateien übernommen.

Die Spezialmodelle liegen versionsabhängig in `compat/BlueprintSpecialModels`.
Die eigene Entity-Pipeline in `compat/RenderTypes` berücksichtigt Deckkraft und
Tiefentest und zeichnet auf dasselbe Ziel wie die übrige Bauplan-Vorschau.
Vorschau-Blockentities sind von der Welt getrennt; es werden keine Blöcke gesetzt.
Modelle und Schildinstanzen werden wiederverwendet. Andere bisher nicht unterstützte
Spezialblöcke und Flüssigkeiten behalten ihre Ersatzkörper.

Validierung: Alle Haupt- und Testquellen mit JDK 21 gegen Forge 57.0.3 kompiliert;
alle neun vorhandenen Regressionstest-Programme erfolgreich ausgeführt.
Gradle-Buildversuch mit `tools/lumen-build.ps1 -NoInstall` scheiterte vor der
Kompilierung an `Unable to establish loopback connection`. Keine Installation.

Noch im Spiel prüfen: einzelne und doppelte Truhen in allen vier Richtungen,
Wand-/Stand-/Hängeschilder verschiedener Holzarten, Bauplan drehen/spiegeln,
Deckkraft ändern, Ebenenfilter, Ressourcen neu laden (F3+T). Prüfen, dass gebaute
Blöcke bei „nur fehlende“ verschwinden und fehlerhafte Blöcke ihre Markierung behalten.

Sicherung der geänderten bestehenden Dateien:
`.build-tmp/sign-chest-preview-20261013/`.

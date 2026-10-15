<div align="center">

# ✦ LUMEN CLIENT

**Bauen. Farmen. Finden. Alles im Blick.**

Ein Minecraft-Client mit Bauplänen, Farm Tracker, Minimap und einem frei anpassbaren HUD.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.7-62B47A?style=flat-square)
![Forge](https://img.shields.io/badge/Forge-57.0.3-DF8A3B?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Client only](https://img.shields.io/badge/Seite-nur%20Client-8B5CF6?style=flat-square)

[Loslegen](#loslegen) · [Funktionen](#funktionen) · [Baupläne](#baupläne) · [Farmen](#farm-tracker) · [Leistung](#leistung-und-aufnahmen) · [Befehle](#alle-befehle)

</div>

---

Lumen wird **nur auf deinem Rechner installiert**. Eine zusätzliche Server-Mod ist nicht nötig.
Mit **X** öffnest du das Menü: Module einschalten, Einstellungen ändern, eigene Tasten belegen und Profile speichern.
Akzentfarben, Menü-Stil und HUD-Positionen lassen sich anpassen.

Die README beschreibt den aktuellen Quellstand. Welche Funktionen deine installierte JAR enthält, hängt von ihrem Build ab.

## Loslegen

1. **Minecraft 1.21.7**, **Forge 57.0.3** und **Java 21** verwenden.
2. `LumenClient-1.21.7_v<Nummer>.jar` nach `%APPDATA%/.minecraft/mods` kopieren. Nur eine Lumen-JAR im Ordner behalten.
3. Mit dem Forge-Profil starten, eine Welt betreten und **X** drücken.

| Du möchtest … | Hier geht es los |
| --- | --- |
| Ein Gebäude nachbauen | **B** → Bauplan laden |
| Deine Farmen verfolgen | **/farm** → Vorschläge übernehmen |
| Materialien sammeln | **/sammelliste** → Zielmengen festlegen |
| Ein gelagertes Item finden | **K** → Kisten durchsuchen |
| Einen Ort merken | **F7** → Wegpunkt speichern |
| Ruckler untersuchen | **/leistung** → Aufnahme starten |

## Funktionen

### Bauen und organisieren

| Funktion | Das kann Lumen |
| --- | --- |
| **Baupläne** | Gebäude als transparente Vorschau platzieren, drehen, spiegeln und Ebene für Ebene nachbauen. |
| **Lazy-Bauen** | Erreichbare Standardblöcke der aktuellen Ebene nacheinander aus deinem Inventar setzen. **G** schaltet den Lauf um. |
| **Sammelliste** | Eigene Materialziele oder den offenen Bauplanbedarf im HUD verfolgen; erledigte Einträge ausblenden. |
| **Farm Tracker** | Beete zu Farmen zusammenfassen, Reife je Pflanzenart anzeigen und einmal pro beobachtetem Erntezyklus benachrichtigen. |
| **Kisten-Gedächtnis** | Inhalte geöffneter Behälter merken, nach Items suchen und zum passenden Lagerplatz navigieren. |
| **Auto-Abwurf** | Ausgewählte Items abwerfen, optional eine Wunschmenge behalten; gehaltene und wertvolle Items schützen. |

Die Sammelliste hat zwei getrennte Quellen: **Aktueller Bauplan** und **Eigene Ziele**.
Im Bauplanmodus bleiben offene Materialien auch dann sichtbar, wenn du genug davon im Inventar hast. **„1 offen“** heißt: ein Item ist noch zu verbauen; der Balken zeigt weiterhin deinen Vorrat. Bei eigenen Sammelzielen blendet „Erledigte ausblenden“ Einträge aus, sobald genügend Material im Inventar liegt. Eine leere Materialliste ist kein Nachweis für einen fertigen Bauplan – unbekannte oder nicht als Item erhältliche Blöcke werden über den Ebenenfortschritt und die Abschlusskontrolle geprüft.
Im Bauplanmodus erscheint nur der Bedarf des einen geladenen Plans. Beim Entladen oder während des Ladens
verschwindet die Liste; ein neuer Plan ersetzt den bisherigen Bedarf vollständig. Eigene Ziele bleiben separat
unter **Quelle: Eigene** erhalten. Ein Klick auf eine Bauplan-Zeile ersetzt deren Material, statt ein dauerhaftes
Sammelziel anzulegen. Über **Eigenes Ziel** legst du bewusst eine unabhängige Zielmenge an.

Die Sammelliste lässt sich mit **Shift + Mausrad** scrollen. Bei offenem Chat geht das auch direkt über der Liste.
Per Rechtsklick kannst du ein Material allein anzeigen, in die Hand nehmen oder im Kisten-Gedächtnis suchen.
Bauplan-Materialien lassen sich außerdem für den gesamten Plan ersetzen.

### Orientierung und Sicht

| Funktion | Das kann Lumen |
| --- | --- |
| **Minimap** | Karte mit Höhlenansicht, Wegpunkten, Todespunkten und Spieler-Markierungen. |
| **Wegpunkte und Navigation** | Orte benennen, gruppieren, einfärben und exportieren; Kompassziel mit Ankunftshinweis und Nether-/Oberwelt-Umrechnung. |
| **Todespunkte** | Beim Tod automatisch einen Wegpunkt anlegen und als Ziel setzen. |
| **Spieler-Radar** | Ausgewählte Spieler mit Entfernung im HUD anzeigen, sofern ihre Position dem Client tatsächlich bekannt ist. |
| **Zoom** | **C** halten; die Vergrößerung mit dem Mausrad ändern. |
| **Lichtlevel und Chunk-Grenzen** | Mögliche Spawnflächen und Chunk-Ränder sichtbar machen. |
| **Blockumrandung und Tooltips** | Anvisierte Blöcke hervorheben; Shulker-Inhalt, Haltbarkeit und Nahrungswerte anzeigen. |
| **X-Ray, Block-ESP und Entity-ESP** | Ausgewählte Blöcke und Entitäten hervorheben, teilweise auch durch Wände. |
| **Fullbright** | Dunkle Bereiche heller darstellen. |

### Bewegung und Oberfläche

| Funktion | Das kann Lumen |
| --- | --- |
| **Freecam** | Mit **F** die Kamera frei bewegen, während der Spieler stehen bleibt. |
| **Flug, NoFall und Auto-Sprint** | Bewegungsfunktionen über eigene Module steuern. |
| **Lokal unsichtbar** | Den eigenen Körper nur in deiner Ansicht ausblenden. |
| **HUD-Editor** | Anzeigen wie Kompass, Rüstung, Sammelliste, Bauplan-Fortschritt und Leistung mit der Maus verschieben. |
| **Design** | Sechs Akzentfarben, Dunkel/Mitternacht/Hell und einstellbare Menü-Transparenz. |
| **Profile** | Zusammenstellungen von Modulen und Einstellungen speichern und wechseln. |
| **Leistungsanzeige** | Frame-Verlauf und gemessene Lumen-Zeiten vergleichen; eine Diagnose als JSON aufnehmen. |

Server entscheiden weiterhin über Bewegung, Platzierung und Teleports. Auf fremden Servern ist ein Teleport nicht garantiert;
Lumen versucht ihn einmal und wertet die Antwort aus. Im Einzelspieler wird die Position auch im integrierten Server umgesetzt.
Beachte die Regeln des jeweiligen Servers, insbesondere bei Automatik-, Sicht- und Bewegungsfunktionen.

## Baupläne

**Datei wählen → Vorschau ausrichten → Material prüfen → nachbauen.**

Unterstützt werden **`.litematic` (v4–7)**, **`.schem` (Sponge v1–3)** und **Struktur-`.nbt`**.
Dateien kommen nach `.minecraft/schematics` oder werden auf das Bauplan-Fenster gezogen.
Alte Blocknamen werden soweit möglich umgewandelt; das alte `.schematic`-Format vor Minecraft 1.13 wird nicht unterstützt.

### Übersicht behalten

- **Vorschau:** Vorschaubilder in der Dateiliste, echte Blocktexturen, einstellbare Deckkraft und Markierungen. Truhen und Schilder haben eigene texturierte Modelle; andere Sonderfälle wie Betten und Flüssigkeiten nutzen eine vereinfachte Darstellung.
- **Block-Info:** Standardmäßig **linkes Alt halten**. Zeigt Name, Ausrichtung, Inventarbestand und offenen Bedarf. Auch als Umschalter, dauerhaft oder ausgeschaltet nutzbar.
- **Materialauswahl:** Mittelklick auf einen noch offenen Bauplan-Block nimmt das passende Item in die Hand. Im Kreativmodus erhältst du es auch ohne Vorrat; im Überlebensmodus wird weiterhin nur vorhandenes Material ausgewählt. Ein Material lässt sich gezielt hervorheben, ersetzen oder in gespeicherten Kisten suchen.
- **Ebenen-Fortschritt:** Kompakte Anzeige wie **„Ebene 3/12 – 87 %“** mit Fortschrittsbalken.
- **Tasten direkt im HUD:** Unter dem Ebenenbalken stehen die aktuell belegten Tasten für **Abweichung übernehmen** und **Block ausblenden**, auch nach einer eigenen Umbelegung.
- **Änderungshistorie:** Unter **B → Prüfen** siehst du die letzten 20 Änderungen dieser Sitzung. Ausblenden, akzeptierte Abweichungen, Materialtausch und Zurücksetzen lassen sich Schritt für Schritt rückgängig machen. Vorher steht dort genau, welcher Schritt zurückgenommen wird. Die Historie endet beim Entladen oder Wechseln des Bauplans; die eigentlichen Bauplan-Änderungen bleiben wie bisher gespeichert. Echte Weltblöcke werden dadurch nicht verändert.
- **Warum geht das nicht?** Die Block-Info nennt fehlendes Material, eine zu große Entfernung, belegte Stellen oder fehlende/ungeeignete Befestigungen. Bei falscher Ausrichtung oder Form stehen Soll und Ist dabei. Erkannte fehlende Befestigungen werden beim Anvisieren kurz gelb umrandet. Die Hinweise nutzen geladene Client-Daten; Serverregeln wie Grundstücksschutz können sie nicht vorhersagen.
- **Abschlusskontrolle:** Nach dem Abschluss der letzten Bau-Ebene beginnt automatisch eine frische Prüfung des gesamten Bauplans. Unter **B → Prüfen** kannst du sie auch manuell starten. Angezeigt werden fehlende und falsche Blöcke, falsche Ausrichtungen sowie ungeladene und unbekannte Stellen. Die Restliste zeigt bis zu 64 Positionen mit Fehlergrund; Anklicken wählt ihre Bau-Ebene. Die Gesamtzahlen umfassen alle Positionen. Erkannte spätere Änderungen und Chunk-Entladungen markieren die Momentaufnahme als veraltet. Große Pläne werden schrittweise innerhalb des bestehenden Scan-Budgets geprüft.
- **Blöcke über zwei Ebenen:** Türen und hohe Pflanzen werden auf beiden betroffenen Ebenen vollständig berücksichtigt. Die Vorschau zeigt auch die zugehörige Hälfte außerhalb der gewählten Ebene. Erst beide korrekten oder akzeptierten Hälften schließen die Platzierung ab; der Materialbedarf zählt nur ein Item. Fehlt später eine Hälfte, wird die Platzierung wieder offen. Diese Blöcke setzt du weiterhin manuell.
- **Befestigung zuerst:** Hängende Laternen, Decken-Hebel und -Knöpfe, Hängeschilder, Glocken an der Decke, Sporenblüten, Hänge-Wurzeln, Höhlen- und Trauerranken sowie nach unten gerichtete Tropfsteine und Amethystknospen/-haufen gehören zur Bau-Ebene ihrer Befestigung. Beispiel: Eine Laterne unter Ebene 2 wird zusammen mit der Decke auf Ebene 2 angezeigt und gezählt. Zusammenhängende hängende Teile folgen ihrer obersten Befestigung. Vorschau, Fortschritt, Ebenen-Materialliste und Lazy-Bauen nutzen diese Zuordnung; die echten Blockpositionen bleiben gleich. Ohne Befestigung im Bauplan bleibt die Stelle auf ihrer ursprünglichen Ebene. Die Block-Info nennt bei verschobenen Aufgaben die Bau-Ebene.
- **Automatischer Ebenenwechsel:** Optional unter **B → Platzierung**. Ist die Ebene vollständig gebaut oder als erledigt anerkannt, folgt nach kurzer Prüfung die nächste. Unbekannte und ungeladene Stellen verhindern den Wechsel. **Bild ↑ / Bild ↓** funktionieren weiterhin.

### Lazy-Bauen

Unter **B → Platzierung → Lazy-Bauen** aktivieren, Material ins Inventar legen und **G einmal drücken**.
Erneutes Drücken stoppt den Lauf. Du musst die Taste nicht halten.

Lumen nutzt die normale Minecraft-Reichweite und eine sichtbare Ansetzfläche. Du wählst zwischen **Im Blickfeld** und
**Aktuelle Ebene** sowie den Tempostufen **Ruhig / Normal / Zügig**. Jede Platzierung wartet auf die Serverbestätigung.

Volle Standardblöcke, Glas, Stämme sowie passend ausgerichtete Stufen und Treppen werden unterstützt.
Komplexe Blöcke wie Truhen, Schilder, Türen und Betten setzt du selbst. Die Kamera wird nicht automatisch gedreht.
Material kann aus dem Hauptinventar in eine volle Hotbar getauscht werden; dabei wird nichts gelöscht oder erzeugt.

Bei Materialmangel erscheint beispielsweise **„12 Stein fehlen“**, bei noch unvollständigem Abgleich **„Mind. …“**.
Nachfüllen, Inventar schließen und mit **G** fortsetzen. Menüs, manuelle Hotbar-Wechsel und Änderungen am Plan stoppen den Lauf.
Auch nach einem automatischen Ebenenwechsel startest du mit **G** neu.

### Anpassen und wiederverwenden

| Aktion | Wirkung |
| --- | --- |
| **N – Abweichung übernehmen** | Einen vorhandenen, anders gesetzten Block akzeptieren. Wird er später abgebaut, gilt die Stelle wieder als fehlend. |
| **H – Als erledigt ausblenden** | Anvisierte Bauplan-Stelle ausblenden und aus Fortschrittsprüfung und offenem Materialbedarf nehmen, auch wenn dort Luft ist. Kein Weltblock wird gelöscht. |
| **B → Prüfen → Letzte Änderung rückgängig machen** | Die letzten 20 Bauplan-Änderungen schrittweise zurücknehmen: Ausblenden, Abweichungen, Materialtausch und Zurücksetzen. |
| **Originalblöcke wiederherstellen** | Ausblendungen, akzeptierte Abweichungen und Materialtausch zurücksetzen; Platzierung und Ebene behalten. |

**Jede Bauplandatei behält pro Welt/Server ihren eigenen Stand.** Du kannst zwischen A und B wechseln und später mit
A weiterbauen: Position, Drehung, Spiegelung, Ebene und Anpassungen werden wiederhergestellt. Die Originaldatei bleibt unverändert.
Wird ihr Inhalt außerhalb von Lumen geändert, werden blockbezogene Anpassungen mit Hinweis zurückgesetzt und gesichert.

### Eigene Baupläne aufnehmen

Im Reiter **Aufnehmen** zwei gegenüberliegende Ecken setzen, Namen und Format wählen und speichern.
Zur Wahl stehen `.litematic` mit Vorschaubild und Struktur-`.nbt`. Die Befehle am Ende der README speichern als `.litematic`.
Erfasst werden geladene Blöcke mit Zustand und Ausrichtung, keine Kisteninhalte, Schildtexte oder Entitäten.

Details: [Lazy-Bauen](docs/LAZY_BAUMODUS.md) · [Anpassungen und Speicherstände](docs/BAUPLAN_ANPASSUNGEN.md)

## Farm Tracker

Öffnen mit **`/farm`** oder **X → Welt → Farm Tracker → Farm-Dashboard**.

1. In die Nähe bepflanzter Beete gehen.
2. Einen erkannten **Vorschlag übernehmen** und individuell benennen.
3. Erntereife auf der Farmkarte verfolgen und bei Bedarf **Ansteuern** wählen.

Erkannt werden **Weizen, Karotten, Kartoffeln, Rote Bete und Netherwarzen**.
Eine Farmkarte fasst die Beete zusammen und zeigt zusätzlich die Reife je Pflanzenart, etwa
**„Weizen 90 % · Karotten 45 % · Netherwarzen 100 %“**. Farmen lassen sich umbenennen, löschen und per Wegpunkt ansteuern.

- **Gemeinsame Farmflächen:** Beete auf gleicher Höhe können über Wege und Wasserkanäle hinweg zusammengehören. Der Beet-Abstand ist von **0 bis 16 freien Blöcken** einstellbar, standardmäßig **6**. Bereits erfasste Flächen werden berücksichtigt, um doppelte Farmen zu vermeiden.
- **Eigene Fläche, optional:** Über **Fläche wählen** zwei gegenüberliegende Ecken setzen und benennen. Alle erkannten Pflanzplätze innerhalb des ausgewählten Bereichs gehören zu dieser Farm – auch über breite Wege hinweg. Später hinzugefügte Beete innerhalb der Grenzen werden mit erfasst.
- **Erntehinweis:** Einstellbare Grenze von **1–100 %**, standardmäßig **100 %**. Eine Farm meldet einmal. Erst wenn eine beobachtete Ernte die Reife unter die Grenze senkt und sie anschließend wieder erreicht wird, folgt eine neue Meldung.
- **Ehrlicher Stand:** Nur vollständig verfügbare Messungen aktualisieren die Reife. Bei ungeladenen Bereichen bleibt der letzte bekannte Stand mit Kennzeichnung sichtbar. Wasser, Wege und leere Pflanzplätze gelten nicht als reif.
- **Dauerhaft gespeichert:** Namen, Flächen, Dimension, letzter Stand und Meldungsstatus bleiben pro Welt/Server erhalten.

Die Erkennung arbeitet schrittweise und lädt keine zusätzlichen Chunks. Manuelle Auswahl: maximal **65.536 Positionen**
im Volumen und **4.096 Pflanzplätze**. Bereits gespeicherte Farmen müssen vollständig in die neue Auswahl passen.

Details: [Farmen, Erntehinweise und Ebenenautomatik](docs/EBENEN_UND_FARM_TRACKER.md)

## Leistung und Aufnahmen

Unter **X → Client → Leistungsanzeige** oder über **`/leistung`** öffnen. Die Messung ist standardmäßig ausgeschaltet.

Das verschiebbare HUD zeigt FPS, Frame-Verlauf und gemessene Lumen-Zeit.
In der Detailansicht siehst du **Durchschnitt, Spitzen und Zeit pro Sekunde**, getrennt nach Modul-Ticks, Welt-Vorschauen,
HUD-Elementen und ausgewählten Speicheraufrufen. Messungen lassen sich stoppen und für einen Vergleich zurücksetzen.

**Ruckler aufnehmen und weitergeben:**

1. **Aufnahme starten**, Menü schließen und normal spielen.
2. Nach dem Ruckler **Stoppen & exportieren** wählen – oder bis zum automatischen Ende nach **120 Sekunden** warten.
3. **Exportordner** öffnen und die Datei `lumen-leistung-…json` weitergeben. Kurz dazuschreiben, was im Spiel passiert ist.

Der Bericht enthält Sekundenintervalle, die **64 stärksten Frame-Ausreißer über 50 ms**, aktive Lumen-Module und technische
Angaben wie Versionen, Grafikeinstellungen und Speicherbelegung. Serveradressen, Spielernamen, Koordinaten und
Welt-/Bauplandateinamen werden nicht erfasst. Es erfolgt kein automatischer Upload.

Der Export läuft im Hintergrund. Bei einem Schreibfehler bleibt die Aufnahme zur Wiederholung im Speicher.
Weltverlassen, Ausschalten oder Zurücksetzen der Messung beenden und exportieren eine laufende Aufnahme ebenfalls.

Gemessen werden Client-Aufrufzeiten einschließlich Wartezeiten. **Keine direkte GPU-, Server- oder Hintergrundthread-Messung.**
FPS-Limit, VSync und offene Menüs beeinflussen den Frame-Verlauf; ein Ausreißer allein beweist noch keine Ursache.

Details: [Leistungsanzeige und Diagnose-Export](docs/LEISTUNGSANZEIGE.md)

## Tasten

Standardbelegung – feste Aktionen sind unter **Optionen → Steuerung → Lumen Client** änderbar.
Modultasten legst du im Client-Menü fest; Bauplan-Aktionen auch direkt im Bauplan-Menü, inklusive Maus-Seitentasten.

| Taste | Aktion |
| --- | --- |
| **X** | Client-Menü öffnen |
| **F** | Freecam umschalten |
| **C halten** | Zoomen |
| **B** | Bauplan-Menü öffnen |
| **Bild ↑ / Bild ↓** | Bauplan-Ebene wechseln |
| **Linkes Alt halten** | Bauplan-Block-Info anzeigen; Verhalten einstellbar |
| **N** | Bauplan-Abweichung übernehmen oder zurücknehmen |
| **H** | Bauplan-Stelle als erledigt ausblenden |
| **G** | Aktiviertes Lazy-Bauen starten/stoppen; nach Materialpause fortsetzen |
| **Mittelklick** | Bauplan-Block auswählen: Kreativ auch ohne Vorrat, Überleben nur aus dem Inventar |
| **Shift + Mausrad** | Sammelliste im HUD scrollen |
| **K** | Kisten-Gedächtnis durchsuchen |
| **F6** | Wegpunkt-Menü öffnen |
| **F7** | Aktuelle Position als Wegpunkt speichern |
| **F5** | Zum letzten Wegpunkt teleportieren |

## Bauen und Projektstruktur

**Voraussetzung: Java JDK 21.** Im Projektordner starten:

```bat
Lumen_Bauen_und_Installieren.bat
```

Das Skript baut, führt die Tests aus und installiert die JAR. Alte Versionen werden nach
`.minecraft/mod-backups/lumen` verschoben; die letzten zehn bleiben erhalten. Jede Version wird zusätzlich unter `releases/` abgelegt.

| Aufruf | Wirkung |
| --- | --- |
| `Lumen_Bauen_und_Installieren.bat` | Bauen, testen und installieren |
| `Lumen_Bauen_und_Installieren.bat -NoInstall` | Bauen und testen, ohne Installation |
| `gradlew build` | Gradle-Build ohne Installation |
| `gradlew check` | Prüfungen und Regressionstests ausführen |
| `Lumen_Bauen_und_Installieren.bat -Mc <Version> -NoInstall` | Eine zusätzlich eingerichtete Minecraft-Version bauen |

Versionsschema: **`<Minecraft-Version>_v<Nummer>`**. `mod_build` in `gradle.properties` wird nach erfolgreicher Installation erhöht.
Weitere Minecraft-Versionen müssen erst eingerichtet werden; aktuell ist **1.21.7 / Forge 57.0.3** die Zielplattform.
Anleitung: [Neue Minecraft-Version einrichten](docs/NEUE_MINECRAFT_VERSION.md).

Die Regressionstests decken unter anderem Bauplan-Formate und -Speicherstände, Lazy-Bauen, Ebenenfortschritt,
Farmflächen und Erntehinweise, Materiallisten, Kistensuche, Teleports sowie Leistungsmessung und Diagnose-Export ab.

**Validierungsstand der neuesten Erweiterungen:** Java-21-Kompilierung mit Forge-Prüfern und alle 16 Regressionstest-Programme
waren erfolgreich. Der vollständige Gradle-Build in der Agent-Umgebung scheitert derzeit an
`Unable to establish loopback connection`. Die neueste JAR-Installation und Prüfung im Spiel stehen deshalb noch aus.

```text
mc_2026/
├─ src/main/         Gemeinsamer Code und Ressourcen
│  └─ java/…/        module, feature, gui/hud, render, blueprint,
│                    farm, chest, building, waypoint, performance
├─ src/mc/1.21.7/    Versionsabhängiger Code und Mixins
├─ src/test/         Regressionstests
├─ versions/         Minecraft-/Forge-Konfiguration
├─ tools/            Build- und Versionsskripte
├─ docs/             Ausführliche Anleitungen
└─ releases/         Gebaute Versionen
```

### Wo deine Daten liegen

Alle Pfade beziehen sich auf den Minecraft-Spielordner, normalerweise `.minecraft`.

| Pfad | Inhalt |
| --- | --- |
| `config/examplemod/client.json` | Module, Einstellungen, Profile, Modultasten und HUD-Positionen |
| `options.txt` | Minecraft-Tastenbelegung einschließlich fester Lumen-Aktionen |
| `config/examplemod/teleports/` | Wegpunkte und Navigationsziel pro Welt/Server |
| `config/examplemod/teleports/exports/` | Exportierte Wegpunkte |
| `config/examplemod/blueprints/` | Aktiver Bauplan und gespeicherte Anpassungen je Datei und Welt/Server |
| `config/examplemod/farms/` | Farmflächen, Namen, Messwerte und Erntehinweis-Status |
| `config/examplemod/chests/` | Gemerkte Behälterinhalte pro Welt/Server |
| `config/examplemod/performance/exports/` | Aufgenommene Leistungsberichte als JSON |
| `schematics/` | Geladene und selbst aufgenommene Bauplan-Dateien |

Die technische Mod-ID bleibt **`examplemod`**, damit vorhandene Einstellungen weiterverwendet werden.
Lumen ist kein offizielles Minecraft-Produkt und steht in keiner Verbindung zu Mojang oder Microsoft.
**Lizenz: All Rights Reserved.**

## Alle Befehle

Die folgenden Befehle gehören zu Lumen und werden clientseitig verarbeitet.
**`<…>` = Pflichtangabe · `[…]` = optional.** Die Klammern nicht mit eingeben.
Namen und Suchtexte dürfen Leerzeichen enthalten. Koordinaten bei `/tpadd` sind absolute Zahlen, keine `~`- oder `^`-Angaben.

### Baupläne und Materialien

| Befehl | Beschreibung |
| --- | --- |
| `/bauplan` | Bauplan-Menü öffnen. |
| `/bauplan ecke1` | Erste Ecke für eine eigene Aufnahme setzen: anvisierter Block, sonst eigene Blockposition. |
| `/bauplan ecke2` | Zweite gegenüberliegende Ecke für die Aufnahme setzen. |
| `/bauplan speichern [Name]` | Ausgewählten Bereich als `.litematic` in `schematics/` speichern. Der Name ist optional. Für `.nbt` das Aufnahme-Menü verwenden. |
| `/sammelliste` | Eigene Materialziele und Zielmengen bearbeiten. |
| `/kiste [Suche]` | Kisten-Gedächtnis öffnen; optional direkt filtern, z. B. `/kiste diamant`. |

### Farmen

| Befehl | Beschreibung |
| --- | --- |
| `/farm` | Farm-Dashboard mit gespeicherten Farmen und Vorschlägen öffnen. |
| `/farm ecke1` | Erste Ecke der optionalen Farmfläche an der aktuellen Spielerposition setzen. |
| `/farm ecke2` | Zweite Ecke an der aktuellen Spielerposition setzen. Auch die Höhe gehört zur Auswahl. |
| `/farm speichern <Name>` | Geladene Pflanzen innerhalb der Auswahl als gemeinsame, benannte Farm speichern. |
| `/farm abbrechen` | Laufende Flächenauswahl verwerfen; gespeicherte Farmen bleiben erhalten. |

### Leistung und Diagnose

| Befehl | Beschreibung |
| --- | --- |
| `/leistung` | Leistungsansicht mit Messung, Aufnahme und Exportordner öffnen. |
| `/leistung aufnahme` | Eine neue Aufnahme starten und die Messung bei Bedarf aktivieren. Maximal 120 Sekunden. Wenn ein Export fehlgeschlagen ist, wird zuerst dieser wiederholt. |
| `/leistung export` | Laufende Aufnahme beenden und als JSON exportieren. Einen fehlgeschlagenen Export erneut versuchen. Ohne Aufnahme oder fehlgeschlagenen Export wird keine Datei erzeugt. |

### Wegpunkte und Navigation

| Befehl | Beschreibung |
| --- | --- |
| `/wp` | Wegpunkt-Menü öffnen. |
| `/wp ziel <ID oder Name>` | Gespeicherten Wegpunkt als Kompassziel setzen. |
| `/wp ziel aus` | Aktuelles Kompassziel entfernen. |
| `/tpname <Name>` | Aktuelle Position als Wegpunkt speichern; maximal 32 Zeichen. Mit `/tpname skip` ohne eigenen Namen speichern. |
| `/tpadd <x> <y> <z> [Name]` | Wegpunkt an absoluten Koordinaten in der aktuellen Dimension anlegen; optionaler Name, maximal 32 Zeichen. |
| `/tpmanager list` | Wegpunkt-Menü öffnen, wie `/wp`. |
| `/tpmanager <ID oder Name>` | Teleport zu einem gespeicherten Wegpunkt versuchen. Auf fremden Servern kann dieser abgelehnt werden. |
| `/tpmanager back` | Zur gespeicherten Ausgangsposition des letzten Teleports zurückkehren. |
| `/tpmanager delete <ID>` | Wegpunkt mit dieser ID direkt löschen. |

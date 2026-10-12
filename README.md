<div align="center">

# ✦ LUMEN CLIENT

**Moderner Minecraft-Client als Forge-Mod – mit Minimap, Bauplänen, Wegpunkten und frei anpassbarem HUD.**

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.7-62B47A?style=flat-square)
![Forge](https://img.shields.io/badge/Forge-57.0.3-DF8A3B?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-E76F00?style=flat-square)
![Client only](https://img.shields.io/badge/Seite-nur%20Client-8B5CF6?style=flat-square)

</div>

---

Lumen läuft **komplett auf deinem Rechner**. Auf Servern muss nichts installiert werden, und Lumen verändert keine Welt.
Alles wird über ein eigenes Menü (**X**) gesteuert: Module ein- und ausschalten, einstellen, Tasten belegen,
Profile speichern. Akzentfarbe und Stil (Dunkel, Mitternacht, Hell) lassen sich anpassen.

## Inhalt

- [Funktionen](#funktionen)
- [Baupläne](#baupläne)
- [Tasten und Befehle](#tasten-und-befehle)
- [Installation](#installation)
- [Selbst bauen](#selbst-bauen)
- [Projektaufbau](#projektaufbau)
- [Hinweise](#hinweise)

## Funktionen

### 🎨 Render
| Modul | Beschreibung |
|---|---|
| **X-Ray** | Zeigt nur Erze und ausgewählte Blöcke |
| **Block-ESP** | Findet ausgewählte Blöcke durch Wände, optional mit Linien dorthin |
| **Entity-ESP** | Spieler, Monster, Tiere und Items durch Wände, mit Lebensbalken |
| **Fullbright** | Alles hell, auch in Höhlen |
| **Lichtlevel** | Zeigt, wo Monster spawnen können |
| **Chunk-Grenzen** | Chunk-Grenzen als Linien |
| **Zoom** | **C** halten zum Heranzoomen, Stärke mit dem Mausrad |
| **Blockumrandung** | Umrandung am anvisierten Block in Akzentfarbe, Dicke und Füllung einstellbar |
| **Tooltips** | Shulker-Inhalt als Raster, Haltbarkeit als Zahl, Hunger und Sättigung bei Essen |

### 🌍 Welt
| Modul | Beschreibung |
|---|---|
| **Minimap** | Karte mit Biomfarben, Höhenschattierung und Höhlenansicht; zeigt Wegpunkte, Todespunkte und Spieler |
| **Wegpunkte** | Orte speichern, in Gruppen sortieren, exportieren, mit Lichtstrahl in der Welt anzeigen |
| **Navigation** | Kompass zum Ziel, Meldung bei Ankunft, Portal-Punkt bei Zielen in Nether oder Oberwelt |
| **Todespunkte** | Beim Tod automatisch ein Wegpunkt, der Kompass zeigt dorthin |
| **Baupläne** | `.litematic`, `.schem` und `.nbt` als durchsichtige Vorschau nachbauen, [mehr dazu](#baupläne) |

### 🏃 Bewegung und Spieler
| Modul | Beschreibung |
|---|---|
| **Freecam** | Kamera frei bewegen, der Spieler bleibt stehen (**F**) |
| **Flug** | Fliegen wie im Kreativmodus |
| **NoFall** | Kein Fallschaden |
| **Auto-Sprint** | Automatisch sprinten beim Vorwärtslaufen |
| **Unsichtbar (lokal)** | Eigenen Körper ausblenden, nur für dich |

### ⚙ Client
| Modul | Beschreibung |
|---|---|
| **HUD** | Logo, Modulliste, Koordinaten, Infos, Kompass, Rüstung und Minimap, alles mit der Maus verschiebbar |
| **Design** | Akzentfarbe (6 Farben) und Stil (Dunkel, Mitternacht, Hell) |
| **Profile** | Komplette Einstellungen speichern und mit einem Klick wechseln |

## Baupläne

Gebäude aus dem Internet oder aus der Kreativwelt in Survival nachbauen, Block für Block.

```
Datei wählen  →  Vorschau platzieren und drehen  →  Materialliste  →  nachbauen
```

**Unterstützte Formate**

| Format | Herkunft |
|---|---|
| `.litematic` | Litematica (v4–7), z. B. von [Abfielder](https://www.abfielder.com/schematicsMain) |
| `.schem` | WorldEdit / Sponge (v1–3), z. B. von [Planet Minecraft](https://www.planetminecraft.com/projects/?share=schematic) |
| `.nbt` | Vanilla-Strukturblock |

Baupläne aus älteren Minecraft-Versionen werden automatisch umgewandelt, z. B. `grass` zu `short_grass`.

**So geht's**
1. Datei in `.minecraft/schematics` legen oder einfach auf das Bauplan-Fenster ziehen.
2. Mit **B**, `/bauplan` oder im Menü unter *Werkzeuge → Baupläne* öffnen und laden.
3. Mit *Vor mir*, *Anvisiert* oder per Koordinaten platzieren, dann drehen und spiegeln.
4. Mit **Bild ↑ / Bild ↓** Ebene für Ebene bauen.
5. Die Materialliste zeigt Gesamtbedarf, was noch fehlt und was schon im Inventar ist.

**Darstellung**
- Echte Blocktexturen, halbtransparent. Truhen, Betten, Schilder und Flüssigkeiten erscheinen als farbiger Körper.
- **Block-Info:** Zielst du auf einen noch nicht gebauten Block, zeigt ein Tooltip Name, Ausrichtung (z. B. „Norden“), Inventar und Restbedarf.
- Farben: 🔵 fehlt · 🔴 falscher Block · 🟡 falsche Ausrichtung · 🟣 unbekannter Block
- Der geladene Plan wird **pro Welt gemerkt** und beim nächsten Betreten wiederhergestellt.

## Tasten und Befehle

| Taste | Aktion |
|---|---|
| **X** | Client-Menü |
| **F** | Freecam |
| **C** (halten) | Zoom |
| **B** | Baupläne |
| **Bild ↑ / Bild ↓** | Bauplan-Ebene wechseln |
| **F6** | Wegpunkt-Menü |
| **F7** | Wegpunkt speichern |
| **F5** | Zum letzten Wegpunkt teleportieren |

Feste Tasten lassen sich unter *Optionen → Steuerung → Lumen Client* ändern. Für jedes Modul kann man im Client-Menü eine eigene Taste festlegen.

| Befehl | Aktion |
|---|---|
| `/wp` | Wegpunkt-Menü öffnen |
| `/wp ziel <Name>` · `/wp ziel aus` | Kompass-Ziel setzen bzw. entfernen |
| `/tpname <Name>` | Aktuelle Position als Wegpunkt speichern |
| `/tpadd <x> <y> <z> [Name]` | Wegpunkt an Koordinaten anlegen |
| `/tpmanager <ID\|Name>` | Zu einem Wegpunkt teleportieren |
| `/tpmanager back` · `/tpmanager delete <ID>` | Zurück zur letzten Position, Wegpunkt löschen |
| `/bauplan` | Bauplan-Fenster öffnen |

## Installation

1. [Minecraft Forge **57.0.3** für 1.21.7](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.21.7.html) installieren.
2. `LumenClient-1.21.7_vX.jar` nach `%APPDATA%\.minecraft\mods` kopieren.
3. Minecraft mit dem Forge-Profil starten und im Spiel **X** drücken.

> [!IMPORTANT]
> Immer nur **eine** Lumen-JAR im `mods`-Ordner lassen, sonst startet Forge nicht.

## Selbst bauen

**Voraussetzung:** [Java JDK 21](https://adoptium.net/temurin/releases/?version=21). Alles Weitere lädt Gradle beim ersten Build selbst.

```bat
Lumen_Bauen_und_Installieren.bat
```

Das Skript sucht Java 21, baut die Mod, führt alle Tests aus und installiert sie in `.minecraft\mods`.
Ältere Lumen-Versionen werden dabei nach `.minecraft\mod-backups\lumen` verschoben, die letzten 10 bleiben erhalten.
Jede Version liegt zusätzlich unter `releases\`.

| Aufruf | Wirkung |
|---|---|
| `Lumen_Bauen_und_Installieren.bat` | Bauen, testen, installieren, Versionsnummer erhöhen |
| `Lumen_Bauen_und_Installieren.bat -NoInstall` | Nur bauen und testen |
| `gradlew build` | Klassischer Gradle-Build ohne Installation |

**Versionsschema:** `<Minecraft-Version>_v<Nummer>`, z. B. `1.21.7_v3`. Je höher die Nummer, desto neuer.
Die Nummer steht als `mod_build` in `gradle.properties` und wird nach jeder erfolgreichen Installation automatisch erhöht.

**Tests** (laufen bei jedem Build mit):

| Test | Prüft |
|---|---|
| `blueprintReaderTest` | Bauplan-Formate, Varint- und Bit-Dekodierung, Drehen und Spiegeln |
| `teleportAttemptTest` | Teleport-Ablauf: bestätigt, abgelehnt, Timeout, Rücksetzungen |
| `waypointGroupsTest` | Wegpunkt-Gruppen, Speichern, Laden und Export |

## Projektaufbau

```
mc_2026/
├─ Lumen_Bauen_und_Installieren.bat   Bauen und installieren mit einem Doppelklick
├─ tools/lumen-build.ps1              Das eigentliche Bau-Skript
├─ build.gradle · gradle.properties   Build-Konfiguration, Version, Abhängigkeiten
└─ src/main/java/com/example/examplemod/
   ├─ module/      Modul-System, Einstellungen, Profile, Konfiguration
   ├─ feature/     Alle Module (X-Ray, Minimap, Baupläne, …)
   ├─ gui/         Menüs, Theme, Tooltips
   │  └─ hud/      HUD-Elemente und HUD-Editor
   ├─ render/      3D-Zeichenhilfen, Minimap-Karte, Bauplan-Vorschau
   ├─ waypoint/    Wegpunkte, Navigation, Teleport
   ├─ blueprint/   Bauplan-Formate, Platzierung, Abgleich mit der Welt
   └─ mixin/       Eingriffe in Minecraft (Rendering, HUD, Kamera)
```

**Gespeicherte Daten** (im Ordner `.minecraft`)

| Pfad | Inhalt |
|---|---|
| `config/examplemod/client.json` | Module, Einstellungen, Tasten, Profile |
| `config/examplemod/teleports/` | Wegpunkte pro Welt bzw. Server |
| `config/examplemod/blueprints/` | Zuletzt genutzter Bauplan pro Welt |
| `schematics/` | Bauplan-Dateien |

Die technische Mod-ID bleibt `examplemod`, damit bestehende Einstellungen und Wegpunkte erhalten bleiben.

## Hinweise

> [!WARNING]
> Funktionen wie X-Ray, ESP, Flug oder NoFall können auf vielen Servern gegen die Regeln verstoßen.
> Vor dem Einsatz die Serverregeln lesen. Im Einzelspieler ist alles erlaubt.

Lumen ist kein offizielles Minecraft-Produkt und steht in keiner Verbindung zu Mojang oder Microsoft.

**Lizenz:** All Rights Reserved.

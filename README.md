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
- [Mehrere Minecraft-Versionen](#mehrere-minecraft-versionen)
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
| **Baupläne** | `.litematic`, `.schem` und `.nbt` als durchsichtige Vorschau nachbauen oder eigene speichern, [mehr dazu](#baupläne) |
| **Kisten-Gedächtnis** | Merkt sich den Inhalt jeder geöffneten Kiste; Suche (**K**) zeigt, wo ein Item liegt, und führt hin |

### 🏃 Bewegung und Spieler
| Modul | Beschreibung |
|---|---|
| **Freecam** | Kamera frei bewegen, der Spieler bleibt stehen (**F**) |
| **Flug** | Fliegen wie im Kreativmodus |
| **NoFall** | Kein Fallschaden |
| **Auto-Sprint** | Automatisch sprinten beim Vorwärtslaufen |
| **Unsichtbar (lokal)** | Eigenen Körper ausblenden, nur für dich |
| **Auto-Abwurf** | Ausgewählte Items automatisch wegwerfen (wie Strg+Q), auf Wunsch mit Menge zum Behalten (z. B. 64 Bruchstein). Wertvolles und das Item in der Hand bleiben geschützt |

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
- **Vorschaubild** in der Dateiliste und beim geladenen Plan, damit ähnlich benannte Baupläne nicht verwechselt werden.
  Gespeicherte Litematica-Bilder werden direkt übernommen, sonst zeichnet Lumen eine Ansicht schräg von oben. Maus darüber: große Ansicht.
- Echte Blocktexturen, halbtransparent. Truhen, Betten, Schilder und Flüssigkeiten erscheinen als farbiger Körper.
- **Block-Info:** **Alt** halten und auf einen noch nicht gebauten Block zielen: Ein Tooltip zeigt Name, Ausrichtung (z. B. „Norden“), Inventar und Restbedarf.
  Wählbar: *Taste halten* (Standard), *Taste umschalten*, *Immer* oder *Aus*. Die Taste lässt sich direkt im
  Bauplan-Fenster (*Platzierung → Block-Info-Taste*) neu belegen, auch mit Maus-Seitentasten. Doppelte Belegungen werden angezeigt.
- **Markierungen:** *Aus*, *Nur Fehler* (Standard: 🔴 falscher Block · 🟡 falsche Ausrichtung · 🟣 unbekannt) oder *Alles* (zusätzlich 🔵 fehlt).
- **Deckkraft** der Vorschau von 15 bis 100 %.
- **Mittelklick wählt Bauplan-Block** (Standard: an): Mausrad-Klick auf einen noch nicht gebauten Block nimmt das passende Item
  in die Hand, genau wie *Block auswählen* im Überlebensmodus. Liegt es in der Hotbar, wird der Platz gewählt, sonst wird es
  aus dem Inventar in die Hotbar getauscht. Es wird nie ein Item erzeugt, auch nicht im Kreativmodus. Fehlt es, erscheint z. B.
  „Eichenholztreppe nicht im Inventar · 34 noch offen“. Ist ein echter Block näher, gilt der normale Mittelklick.
- Der geladene Plan wird **pro Welt gemerkt** und beim nächsten Betreten wiederhergestellt.

**Eigene Baupläne speichern** (Reiter *Aufnehmen*)
1. Zwei gegenüberliegende Ecken setzen: anvisierter Block oder eigene Position, auch per `/bauplan ecke1` und `/bauplan ecke2`.
2. Name eingeben, Format wählen: `.litematic` (mit Vorschaubild, auch in Litematica nutzbar) oder `.nbt` (Strukturblock).
3. Speichern, auch per `/bauplan speichern <Name>`. Die Datei landet in `.minecraft/schematics`, Vorhandenes wird nie überschrieben.

Gespeichert werden Blöcke mit Ausrichtung und Zustand, aber keine Kisteninhalte, Schildtexte oder Tiere.

## Tasten und Befehle

| Taste | Aktion |
|---|---|
| **X** | Client-Menü |
| **F** | Freecam |
| **C** (halten) | Zoom |
| **B** | Baupläne |
| **Bild ↑ / Bild ↓** | Bauplan-Ebene wechseln |
| **Alt** (halten, änderbar) | Bauplan: Block-Info zum anvisierten Block |
| **Mausrad-Klick** | Bauplan: Item des anvisierten, noch nicht gebauten Blocks in die Hand nehmen |
| **K** | Kisten-Gedächtnis durchsuchen |
| **F6** | Wegpunkt-Menü |
| **F7** | Wegpunkt speichern |
| **F5** | Zum letzten Wegpunkt teleportieren |

Feste Tasten lassen sich unter *Optionen → Steuerung → Lumen Client* ändern. Für jedes Modul kann man im Client-Menü eine eigene Taste festlegen.
Sie schaltet das Modul ein und aus; beim Kisten-Gedächtnis öffnet sie stattdessen die Suche.

| Befehl | Aktion |
|---|---|
| `/wp` | Wegpunkt-Menü öffnen |
| `/wp ziel <Name>` · `/wp ziel aus` | Kompass-Ziel setzen bzw. entfernen |
| `/tpname <Name>` | Aktuelle Position als Wegpunkt speichern |
| `/tpadd <x> <y> <z> [Name]` | Wegpunkt an Koordinaten anlegen |
| `/tpmanager <ID\|Name>` | Zu einem Wegpunkt teleportieren |
| `/tpmanager back` · `/tpmanager delete <ID>` | Zurück zur letzten Position, Wegpunkt löschen |
| `/bauplan` | Bauplan-Fenster öffnen |
| `/bauplan ecke1` · `ecke2` · `speichern <Name>` | Bereich auswählen und als eigenen Bauplan speichern |
| `/kiste [Suche]` | Kisten-Gedächtnis durchsuchen, z. B. `/kiste diamant` |

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
| `Lumen_Bauen_und_Installieren.bat -Mc 1.21.8` | Eine andere eingerichtete Minecraft-Version bauen |
| `gradlew build` · `gradlew build -Pmc_version=1.21.8` | Klassischer Gradle-Build ohne Installation |

**Versionsschema:** `<Minecraft-Version>_v<Nummer>`, z. B. `1.21.7_v3`. Je höher die Nummer, desto neuer.
Die Nummer steht als `mod_build` in `gradle.properties` und wird nach jeder erfolgreichen Installation automatisch erhöht.

**Tests** (laufen bei jedem Build mit):

| Test | Prüft |
|---|---|
| `blueprintReaderTest` | Bauplan-Formate, Varint- und Bit-Dekodierung, Drehen und Spiegeln |
| `teleportAttemptTest` | Teleport-Ablauf: bestätigt, abgelehnt, Timeout, Rücksetzungen |
| `waypointGroupsTest` | Wegpunkt-Gruppen, Speichern, Laden und Export |
| `dropGuardTest` | Auto-Abwurf: Schutz vor Endlosschleifen beim Wiederaufheben |
| `chestSearchTest` | Kisten-Gedächtnis: Suche, Zusammenfassen, Shulker-Inhalte |

## Mehrere Minecraft-Versionen

Lumen ist für weitere Minecraft-Versionen vorbereitet. Gemeinsamer Code liegt in `src/main/`.
Was sich je Version unterscheidet, steht in `versions/<Version>.properties` und `src/mc/<Version>/`.
Eine neue Version wird **neben** den bestehenden angelegt:

```bat
powershell -ExecutionPolicy Bypass -File tools
ew-mc-version.ps1 -To 1.21.8 -Forge 58.0.0
Lumen_Bauen_und_Installieren.bat -Mc 1.21.8 -NoInstall
```

Die ausführliche Anleitung mit Checkliste steht in [docs/NEUE_MINECRAFT_VERSION.md](docs/NEUE_MINECRAFT_VERSION.md).

## Projektaufbau

```
mc_2026/
├─ Lumen_Bauen_und_Installieren.bat   Bauen und installieren mit einem Doppelklick
├─ tools/
│  ├─ lumen-build.ps1                 Das eigentliche Bau-Skript
│  └─ new-mc-version.ps1              Neue Minecraft-Version anlegen
├─ versions/1.21.7.properties         Forge-, Mapping- und Java-Version je Minecraft-Version
├─ build.gradle · gradle.properties   Build-Konfiguration, Standard-Version, Build-Nummer
├─ docs/                              Anleitungen für Entwickler
└─ src/
   ├─ main/java/com/example/examplemod/     Gemeinsamer Code
   │  ├─ module/      Modul-System, Einstellungen, Profile, Konfiguration
   │  ├─ feature/     Alle Module (X-Ray, Minimap, Baupläne, …)
   │  ├─ gui/         Menüs, Theme, Tooltips
   │  │  └─ hud/      HUD-Elemente und HUD-Editor
   │  ├─ render/      3D-Zeichenhilfen, Minimap-Karte, Bauplan-Vorschau
   │  ├─ waypoint/    Wegpunkte, Navigation, Teleport
   │  ├─ chest/       Kisten-Gedächtnis: Speicher und Suche
   │  └─ blueprint/   Bauplan-Formate, Platzierung, Abgleich mit der Welt
   └─ mc/1.21.7/                            Versionsabhängiger Code
      ├─ mixin/       Eingriffe in Minecraft (Rendering, HUD, Kamera)
      ├─ compat/      Eigene Render-Zustände
      └─ ClientKeybinds, examplemod.mixins.json
```

**Gespeicherte Daten** (im Ordner `.minecraft`)

| Pfad | Inhalt |
|---|---|
| `config/examplemod/client.json` | Module, Einstellungen, Tasten, Profile |
| `config/examplemod/teleports/` | Wegpunkte pro Welt bzw. Server |
| `config/examplemod/blueprints/` | Zuletzt genutzter Bauplan pro Welt |
| `config/examplemod/chests/` | Kisten-Gedächtnis pro Welt bzw. Server |
| `schematics/` | Bauplan-Dateien |

Die technische Mod-ID bleibt `examplemod`, damit bestehende Einstellungen und Wegpunkte erhalten bleiben.

## Hinweise

> [!WARNING]
> Funktionen wie X-Ray, ESP, Flug oder NoFall können auf vielen Servern gegen die Regeln verstoßen.
> Vor dem Einsatz die Serverregeln lesen. Im Einzelspieler ist alles erlaubt.

Lumen ist kein offizielles Minecraft-Produkt und steht in keiner Verbindung zu Mojang oder Microsoft.

**Lizenz:** All Rights Reserved.

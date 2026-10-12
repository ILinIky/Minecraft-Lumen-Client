# Plan: Edition „Lumen Mods“

> **Status:** Geplant, noch nicht umgesetzt (Stand 2026-10-12, Lumen Client `1.21.7_v3`).
> Erst ausführen, wenn weitere Funktionen dazugekommen sind. Vorher die
> [Bestandsaufnahme](#5-bestandsaufnahme-vor-dem-start-neu-erstellen) neu erstellen, weil sich Zeilen und
> Abhängigkeiten bis dahin ändern.

## 1. Ziel

Aus **demselben Code** entstehen zwei Ausgaben:

| | Lumen Client | Lumen Mods |
|---|---|---|
| JAR | `LumenClient-1.21.7_vN.jar` | `LumenMods-1.21.7_vN.jar` |
| Anzeigename (Modliste, Logo, Steuerung) | Lumen Client · LUMEN CLIENT | Lumen Mods · LUMEN MODS |
| Inhalt | alles wie bisher | ohne die Funktionen aus Abschnitt 2 |

Festgelegte Entscheidungen:

- **Der Lumen Client bleibt unverändert** in Funktionen, Namen, Tasten und Einstellungen.
- **Nie gleichzeitig installiert.** Beide behalten die technische Mod-ID `examplemod`. Forge lädt daher nie beide zugleich, und das Bau-Skript entfernt beim Installieren die jeweils andere Edition aus `mods`.
- **Nicht nur ausblenden, sondern weglassen.** Die entfernten Funktionen samt ihrer Mixins sind in der Lumen-Mods-JAR nicht enthalten. Eine automatische Prüfung stellt das bei jedem Build sicher.
- **Kein kopierter Projektordner.** Beide Editionen entstehen aus einem Projekt (wie die Minecraft-Versionen), damit neue Funktionen nicht doppelt gepflegt werden müssen.
- **„Ordner“:** Überall, wo Spieler den Namen sehen, steht „Lumen Mods“: Steuerungs-Kategorie, Modliste, Logo-Zusatz und JAR-Name. Fertige Dateien landen getrennt in `releases/client/` und `releases/mods/`.
- **Versionsnummer gemeinsam:** Gleicher Funktionsstand bedeutet gleiche Nummer `vN` für beide.

## 2. Funktionsumfang je Edition

Bei neuen Funktionen **diese Tabelle zuerst ergänzen**. Sie ist die Grundlage für `editions/mods.properties`.

| Funktion | Modul-ID | Client | Mods | Anmerkung |
|---|---|:-:|:-:|---|
| X-Ray | `xray` | ✔ | ✘ | inkl. Mixins `BlockRenderDispatcherMixin`, `ModelBlockRendererMixin` |
| Block-ESP | `block_esp` | ✔ | ✘ | `BlockSelectScreen`/`BlockLists` bleiben, falls noch anderweitig genutzt, sonst ebenfalls weg |
| Entity-ESP | `entity_esp` | ✔ | ✘ | inkl. `EntityFilterScreen` |
| Fullbright | `fullbright` | ✔ | ✘ | Aufruf `Fullbright.update()` in `ClientEvents` |
| Flug | `flight` | ✔ | ✘ | |
| NoFall | `no_fall` | ✔ | ✘ | inkl. `LocalPlayerMixin` (onGround) und Fallschaden-Event in `ClientEvents` |
| Unsichtbar (lokal) | `invisibility` | ✔ | ✘ | |
| Freecam | `freecam` | ✔ | ✘ | inkl. `CameraMixin`, `EntityTurnMixin`, `ClientInputAccessor`, Bewegungs-/Interaktions-Events, Taste F |
| Minimap | `minimap` | ✔ | ✘ | inkl. `MinimapElement`, `MinimapData`, HUD-Eintrag |
| Wegpunkt-**Teleport** | – | ✔ | ✘ | `TeleportService`, `TeleportAttempt`, `ClientPacketListenerMixin`, Taste F5, `/tpmanager <ID>` und `back`, Teleport-Knöpfe |
| Wegpunkte (anzeigen, Gruppen, Export, Todespunkte, Kompass, Navigation, Portal-Punkt) | `waypoints` | ✔ | ✔ | ohne Teleport |
| Baupläne inkl. Block-Info und Vorschaubilder | `blueprints` | ✔ | ✔ | |
| Tooltips, Blockumrandung, Zoom | … | ✔ | ✔ | |
| Lichtlevel, Chunk-Grenzen, Tracer bei Wegpunkten | … | ✔ | ✔ | Grenzfälle, Entscheidung: behalten |
| Auto-Sprint, HUD, Design, Profile | … | ✔ | ✔ | |
| Auto-Abwurf (inkl. „Behalten bis X“) | `auto_drop` | ✔ | ✔ | Komfort, wirft nur per normalem Inventar-Klick. Bei Bedarf vor dem Umsetzen neu entscheiden |
| Kisten-Gedächtnis | `chest_memory` | ✔ | ✔ | Kennt nur selbst geöffnete Behälter. Achtung: Der Kompass nutzt `TeleportService.currentDimension()`, also beim Entkoppeln mit erfassen |
| Baupläne speichern (Aufnehmen) | `blueprints` | ✔ | ✔ | liest nur geladene Chunks |

## 3. Aufbau

```
editions/
├─ client.properties      Name, JAR-Name, Logo-Zusatz; nichts ausgeschlossen
└─ mods.properties        dto. + Listen der ausgeschlossenen Quellen und Mixins
src/
├─ main/                  gemeinsamer Code
├─ mc/<MC-Version>/       versionsabhängiger Code (Mixins, Tasten, RenderTypes)
└─ edition/
   ├─ client/java/…       Brücken mit voller Funktion (Teleport, Freecam/NoFall/Fullbright-Events)
   └─ mods/java/…         dieselben Brücken als leere Varianten
```

Beispiel `editions/mods.properties`:

```properties
edition_id=mods
mod_name=Lumen Mods
mod_file_name=LumenMods
client_tag=MODS
config_file=lumen-mods.json
# Quelldateien, die nicht mitkompiliert werden (Ant-Muster relativ zu src/main/java und src/mc/<V>/java)
exclude_sources=**/feature/XRay.java,**/feature/BlockEsp.java,**/feature/EntityEsp.java,\
  **/feature/Fullbright.java,**/feature/Flight.java,**/feature/NoFall.java,**/feature/Invisibility.java,\
  **/feature/Freecam.java,**/feature/Minimap.java,**/gui/hud/MinimapElement.java,**/render/MinimapData.java,\
  **/gui/EntityFilterScreen.java,**/waypoint/TeleportService.java,**/waypoint/TeleportAttempt.java,\
  **/mixin/ClientInputAccessor.java
# Mixins, die aus examplemod.mixins.json gestrichen werden (Dateien selbst ebenfalls ausschließen)
exclude_mixins=BlockRenderDispatcherMixin,ModelBlockRendererMixin,CameraMixin,EntityTurnMixin,\
  LocalPlayerMixin,ClientPacketListenerMixin,ClientInputAccessor
# Tests, die nur in dieser Edition laufen
tests=blueprintReaderTest,waypointGroupsTest
```

## 4. Umsetzung in Schritten

Jeder Schritt endet mit einem Testbuild des **Lumen Client**. Der muss sich verhalten wie vorher.

### Schritt 0: Sicherung

`src`, `build.gradle`, `gradle.properties`, `tools`, `versions` und die `.bat`-Dateien nach
`.build-tmp/lumen-mods-<Datum>/before-editions.zip` sichern.

### Schritt 1: Gemeinsamen Code entkoppeln (nur Lumen Client, Verhalten gleich)

Ziel: Kein gemeinsamer Code verweist mehr direkt auf eine Klasse, die in Lumen Mods fehlt.

1. **Dimension:** `TeleportService.currentDimension()` wird in vielen Dateien genutzt (Kompass, Navigation,
   Wegpunkt-Menü und -Editor, Befehle, Todespunkte, Minimap). Diese Methode in eine neutrale Klasse
   `waypoint/Dimensions.java` verschieben. `TeleportService` ruft sie dann selbst auf.
2. **Ereignisse:** Die Listener für Freecam (Bewegung, Interaktion), NoFall (`LivingFallEvent`) und den Aufruf
   `Fullbright.update()` aus `ClientEvents` in eine eigene Klasse `feature/MovementCheatEvents.java` (eigener
   `@EventBusSubscriber`) verschieben. Sie fällt in Lumen Mods mit den Modulen weg.
3. **Modul-Registrierung:** `ModuleManager.init()` registriert die betroffenen Module nicht mehr direkt, sondern
   über `EditionModules.register(...)` aus `src/edition/<id>/`. Die statischen Felder (`XRAY`, `FREECAM` …)
   werden nur noch dort bzw. in den ausgeschlossenen Klassen genutzt. Vorher per Suche prüfen.
4. **Teleport-Brücke:** `waypoint/Teleports.java` je Edition mit `enabled()`, `teleport(w)`, `back()`,
   `hasBack()`, `methodLabel(dim)`, `onTick()`, `reset()`. Client: leitet an `TeleportService` weiter. Mods:
   `enabled() == false`, sonst leer. Diese Stellen auf die Brücke umstellen:
   - `ClientEvents` (Tick, F5-Taste, Login/Logout-Reset)
   - `ClientCommandHandler`: `/tpmanager <ID>` und `back` nur registrieren, wenn `enabled()`. `/tpname`, `/tpadd`,
     `/wp` und `/wp ziel` bleiben.
   - `WaypointScreen`: Teleport-Knöpfe, „Zurück“ und den Hinweis „Teleport: …“ ausblenden, wenn nicht verfügbar
   - `TeleportService` verweist auf `Freecam`. Bleibt so, weil beide nur im Client existieren.
5. **HUD:** `HudElements.create(...)` bekommt die Minimap über `EditionModules.hudElements(...)` statt direkt.
6. **Taste F5 (Teleport):** Die `KeyMapping`-Registrierung in `ClientKeybinds` (Versionsordner) nur bei aktivem
   Teleport. Am einfachsten über `Teleports.enabled()`.

Ergebnis: Testbuild Lumen Client grün, alle Tests grün, im Spiel kein Unterschied.

### Schritt 2: Editionen im Build

1. `editions/client.properties` und `editions/mods.properties` anlegen (siehe Abschnitt 3).
2. `gradle.properties`: `edition=client` als Standard.
3. `build.gradle`:
   - `editions/${edition}.properties` laden (wie bei `versions/`), mit Fehlermeldung bei unbekannter Edition.
   - `src/edition/${edition}/java` als zusätzliche Quelle. `exclude_sources` als `exclude` auf `sourceSets.main.java`.
   - `mod_name`, `mod_file_name` aus der Edition. Diese Werte aus `gradle.properties` entfernen.
   - `examplemod.mixins.json` beim Kopieren der Ressourcen filtern und `exclude_mixins` entfernen
     (`processResources { filesMatching('examplemod.mixins.json') { filter { … } } }`).
   - Sprachdateien: `key.categories.examplemod` aus `${mod_name}` füllen (Platzhalter + `expand`, wie bei `mods.toml`).
   - Tests: Nur die in `tests=` genannten Aufgaben an `check` hängen. `TeleportAttemptTest` würde in Lumen Mods
     nicht kompilieren und muss dort auch aus `sourceSets.test` ausgeschlossen werden.
4. **Prüfaufgabe `verifyEdition`** (hängt an `check`): öffnet die fertige JAR und bricht ab, wenn
   - eine Klasse aus `exclude_sources` oder ein Mixin aus `exclude_mixins` enthalten ist,
   - `examplemod.mixins.json` einen ausgeschlossenen Mixin nennt.
5. **Anzeige:** `Theme.CLIENT_TAG` („CLIENT“/„MODS“) und der Name in Menüs aus der Mod-Info lesen
   (`ModList…getDisplayName()`) statt fest im Code. Fallback „CLIENT“.
6. **Einstellungen getrennt:** `ClientConfig` nutzt `config/examplemod/<config_file>`. Client bleibt `client.json`,
   Mods bekommt `lumen-mods.json`. Grund: Lumen Mods würde sonst beim Speichern die Einstellungen der fehlenden
   Module verwerfen. Wegpunkte (`teleports/`) und Baupläne (`blueprints/`) bleiben gemeinsam.
   Beim ersten Start von Lumen Mods: falls `lumen-mods.json` fehlt, `client.json` als Vorlage kopieren
   (Design, HUD-Positionen und Profile übernehmen).

### Schritt 3: Bau-Skript

1. `tools/lumen-build.ps1`: Parameter `-Edition client|mods` (Standard aus `gradle.properties`),
   an Gradle als `-Pedition=…`. Banner und Zusammenfassung zeigen die Edition.
2. Installieren: vor dem Kopieren **alle** Lumen-JARs aus `mods` sichern: `LumenClient-*.jar`, `LumenMods-*.jar`
   und die alte `examplemod.jar`. Dadurch ist immer nur eine Edition installiert.
3. `releases/client/` und `releases/mods/` statt eines gemeinsamen `releases/`.
4. Neue Datei `LumenMods_Bauen_und_Installieren.bat` (ruft das Skript mit `-Edition mods` auf).
   `Lumen_Bauen_und_Installieren.bat` bleibt für den Client.
5. Die Build-Nummer wird wie bisher nach erfolgreicher Installation erhöht, gemeinsam für beide Editionen.
6. `tools/new-mc-version.ps1` braucht keine Änderung, weil Editionen und Minecraft-Versionen voneinander
   unabhängig sind (`-Mc 1.21.8 -Edition mods` funktioniert automatisch).

### Schritt 4: Doku

- README: Abschnitt „Editionen“ mit der Tabelle aus Abschnitt 2 (nur ✔/✘), Bau-Befehle für beide.
- `docs/NEUE_MINECRAFT_VERSION.md`: Hinweis, beide Editionen zu testen.
- `AGENTS.md` (Ordner darüber): Stand und Ablage der Sicherung eintragen. Diesen Plan als „umgesetzt“ markieren.

## 5. Bestandsaufnahme vor dem Start neu erstellen

Seit dem Stand dieses Plans können neue Verweise dazugekommen sein. Vor Schritt 1 ausführen
(im Ordner `mc_2026`, Git Bash) und das Ergebnis mit Abschnitt 4 abgleichen:

```bash
for c in XRay BlockEsp EntityEsp Fullbright Flight NoFall Invisibility Freecam Minimap MinimapElement \
         MinimapData BlockSelectScreen EntityFilterScreen BlockLists TeleportService TeleportAttempt \
         ClientInputAccessor; do
  echo "== $c"; grep -rlw "$c" src | grep -v "/$c.java"
done
```

Stand 2026-10-12 (zum Vergleich):

| Klasse | Wird außerhalb verwendet in |
|---|---|
| `TeleportService` | ClientEvents, ClientCommandHandler, Navigation, WaypointScreen, WaypointEditScreen, HudElements (Kompass), MinimapElement, ClientPacketListenerMixin. **Meist nur `currentDimension()`** |
| `Freecam` | ClientEvents, ModuleManager, TeleportService, CameraMixin, EntityTurnMixin |
| `NoFall` | ClientEvents, ModuleManager, LocalPlayerMixin |
| `Fullbright` | ClientEvents, ModuleManager |
| `EntityEsp` | ModuleManager, EntityFilterScreen, MinimapElement (nur Farbkonstanten) |
| `XRay` | ModuleManager, BlockRenderDispatcherMixin, ModelBlockRendererMixin |
| `Minimap`/`MinimapElement`/`MinimapData` | ModuleManager, HudElements und untereinander |
| `BlockSelectScreen`, `BlockLists` | nur XRay, BlockEsp, EntityEsp, können also mit ausgeschlossen werden |
| Mixins, die bleiben | `GuiMixin` (HUD), `LevelRendererMixin` (Welt-Overlays) |

## 6. Abnahme

- [ ] `Lumen_Bauen_und_Installieren.bat -NoInstall` → `LumenClient-…jar`, alle Tests grün, Inhalt wie vorher
- [ ] `LumenMods_Bauen_und_Installieren.bat -NoInstall` → `LumenMods-…jar`, `verifyEdition` grün
- [ ] Lumen Mods im Spiel: Menü zeigt keine der entfernten Module, Logo „LUMEN MODS“,
      Steuerung → Kategorie „Lumen Mods“ ohne Freecam/Teleport-Tasten
- [ ] Wegpunkte in Lumen Mods: anlegen, bearbeiten, Kompass, Navigation, Todespunkte; **kein** Teleport-Knopf,
      `/tpmanager 1` unbekannt
- [ ] Baupläne, Tooltips, Blockumrandung, Zoom, Lichtlevel, Chunk-Grenzen funktionieren in Lumen Mods
- [ ] Wechsel Client → Mods → Client: Client-Einstellungen (X-Ray-Blöcke, Flug usw.) sind noch da
- [ ] Installation einer Edition entfernt die andere aus `mods` (Sicherung in `mod-backups/lumen`)
- [ ] `-Mc <andere Version> -Edition mods` baut, sobald eine zweite Minecraft-Version existiert

## 7. Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Neue Funktion greift später direkt auf eine ausgeschlossene Klasse zu → Lumen Mods baut nicht | Beide Editionen bei jedem größeren Schritt bauen. Der Fehler zeigt sich sofort beim Kompilieren. |
| Ausgeschlossener Mixin bleibt in der JSON → Absturz beim Start | `verifyEdition` prüft JSON und JAR |
| Profile aus dem Client enthalten ausgeschlossene Module | Unbekannte Module werden beim Laden bereits übersprungen (`ModuleManager.byId` → `null`) |
| Spieler nimmt versehentlich beide JARs in `mods` | Gleiche Mod-ID → Forge meldet doppelte Mod. Das Skript räumt ohnehin beide weg. |

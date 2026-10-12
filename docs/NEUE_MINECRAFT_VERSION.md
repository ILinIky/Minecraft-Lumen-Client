# Neue Minecraft-Version hinzufügen

Lumen ist so aufgebaut, dass eine neue Minecraft-Version **neben** den bestehenden entsteht.
Die alten Versionen bleiben baubar.

```
versions/
├─ 1.21.7.properties      Forge-, Mapping-, Java-Version je Minecraft-Version
└─ 1.21.8.properties      ← neu
src/
├─ main/                  Gemeinsamer Code (≈ 90 %): Module, Menüs, HUD, Baupläne, Wegpunkte
└─ mc/
   ├─ 1.21.7/             Versionsabhängiger Code
   └─ 1.21.8/             ← neu (Kopie, dann anpassen)
```

Gebaut wird immer **eine** Version: gemeinsamer Code plus der Ordner dieser Version.
Die JAR heißt danach, z. B. `LumenClient-1.21.8_v5.jar`.

## Ablauf

**1. Forge-Version nachschlagen**
Auf [files.minecraftforge.net](https://files.minecraftforge.net) die empfohlene Forge-Version für die neue
Minecraft-Version heraussuchen. Im MDK dieser Version (`build.gradle`, `gradle.properties`) außerdem nachsehen:

- Java-Version (`java.toolchain.languageVersion`)
- Version von `eventbus-validator`
- Version des ForgeGradle-Plugins (`net.minecraftforge.gradle`)

**2. Version anlegen** (baut noch nichts)

```bat
powershell -ExecutionPolicy Bypass -File tools\new-mc-version.ps1 -To 1.21.8 -Forge 58.0.0
```

| Parameter | Bedeutung | Standard |
|---|---|---|
| `-To` | Neue Minecraft-Version | *Pflicht* |
| `-Forge` | Forge-Version dafür | *Pflicht* |
| `-From` | Vorlage | `mc_version` aus `gradle.properties` |
| `-Mappings` | Mojang-Mappings | wie `-To` |
| `-Java` | Java-Version | wie Vorlage |
| `-EventBus` | `eventbus-validator`-Version | wie Vorlage |
| `-SetDefault` | Neue Version als Standard setzen | aus |

**3. Testbuild**

```bat
Lumen_Bauen_und_Installieren.bat -Mc 1.21.8 -NoInstall
```

**4. Fehler beheben**, zuerst in `src/mc/1.21.8/`. Braucht gemeinsamer Code eine Änderung, die es nur in
der neuen Version gibt, wandert genau dieses Stück nach `compat/` in die Versionsordner, statt den gemeinsamen
Code für alle Versionen zu ändern.

**5. Im Spiel prüfen** (siehe Checkliste unten), dann ohne `-NoInstall` installieren.

## Was bei Updates typischerweise bricht

| Bereich | Ort | Warum |
|---|---|---|
| **Mixins** | `src/mc/<V>/…/mixin/` | Zielmethoden und Signaturen ändern sich fast jedes Update. Fehler zeigen sich oft erst beim **Start**, nicht beim Bauen. |
| **Render-Zustände** | `src/mc/<V>/…/compat/RenderTypes.java` | `RenderType`/`RenderPipeline` wurden in 1.21.5 und 1.21.6 umgebaut. |
| **Tastenbelegung** | `src/mc/<V>/…/ClientKeybinds.java` | Konstruktor und Kategorien von `KeyMapping` ändern sich gelegentlich. |
| **GUI-Zeichnen** | `gui/`, `gui/hud/` (gemeinsam) | `GuiGraphics` (blit, Pose, Scissor) wurde 1.21.6 neu aufgebaut. |
| **NBT** | `blueprint/BlueprintReader.java` | Getter liefern seit 1.21.5 `Optional`. |
| **Forge-Events** | `ClientEvents.java` | Event-Klassen und EventBus-Regeln (EventBus 7 seit 1.21.6). |
| **ForgeGradle** | `build.gradle`, `settings.gradle` | Neue Forge-Generationen können ein neueres Plugin brauchen. Die Plugin-Version gilt dann für alle Versionen. Bei Konflikt das ältere Projekt einfrieren oder einen eigenen Build-Zweig nutzen. |

Dauerhaft versionsunabhängig sind z. B. `TeleportAttempt`, `BlueprintTransform`, `WaypointGroups` und das Lesen
der Bauplan-Formate; ihre Tests laufen bei jedem Build mit.

## Checkliste im Spiel

- [ ] Spiel startet ohne Mixin-Fehler (`logs/latest.log` nach `examplemod` / `Mixin` durchsuchen)
- [ ] Client-Menü (**X**), Suche, Einstellungen, Tastenbelegung
- [ ] HUD und HUD-Editor, Minimap (auch Höhlenansicht)
- [ ] X-Ray, Block-/Entity-ESP, Fullbright, Lichtlevel, Chunk-Grenzen, Blockumrandung
- [ ] Freecam (**F**), Zoom (**C**), Flug, NoFall
- [ ] Wegpunkte (**F6**/**F7**), Kompass, Teleport im Einzelspieler
- [ ] Baupläne (**B**): laden, Vorschau mit Texturen, Ebenen, Block-Info, Materialliste
- [ ] Tooltips: Shulker-Raster, Haltbarkeit, Sättigung

# Leistungsanzeige

Unter **X → Client → Leistungsanzeige** einschalten. Standardmäßig aus. Über
**Messwerte öffnen** oder **/leistung** erscheint die Detailansicht. Dort lässt sich
die Messung starten, stoppen (Werte bleiben zum Lesen stehen) und zurücksetzen.
Erneutes Starten beginnt eine neue Messung. Weltwechsel leeren die Messdaten.

Das kompakte HUD lässt sich separat ausblenden und im vorhandenen HUD-Editor
verschieben. Der globale HUD-Schalter, F1 und F3 beeinflussen die Sichtbarkeit,
nicht die laufende Messung. Die Einstellungen und HUD-Position werden wie die
anderen Lumen-Einstellungen gespeichert; Messdaten bleiben nur im Arbeitsspeicher.
Eine ausdrücklich gestartete Aufnahme kann zusätzlich als Diagnose-Datei gespeichert
werden, wie unten beschrieben.

## Aufnahme zum Weitergeben

1. **/leistung → Aufnahme starten** wählen. Die Leistungsanzeige wird bei Bedarf
   eingeschaltet und das Live-Messfenster zurückgesetzt. Eine laufende Aufnahme
   wird durch erneutes Klicken nicht ersetzt.
2. Menü schließen und die Szene spielen, in der es ruckelt. Im Leistungs-HUD steht
   **REC** mit der bisherigen Dauer. Die Aufnahme läuft auch bei ausgeblendetem HUD.
3. **/leistung → Stoppen & exportieren** wählen. Alternativ: `/leistung aufnahme`
   zum Starten und `/leistung export` zum Stoppen und Exportieren.
4. Über **Exportordner** die erzeugte Datei `lumen-leistung-…json` finden und
   weitergeben. Speicherort: `.minecraft/config/examplemod/performance/exports/`.

Nach 120 Sekunden endet die Aufnahme automatisch. Auch Ausschalten der Messung,
Zurücksetzen und Verlassen der Welt beenden und exportieren eine laufende Aufnahme.
Ein hängender Client kann erst beim nächsten Render-Aufruf stoppen; der Bericht
weist deshalb die tatsächliche Dauer aus. Bei einem Absturz vor dem Export gibt es
keine Wiederherstellung der Aufnahme. Die gewöhnliche Live-Anzeige ohne Aufnahme
schreibt keine Berichte.

Die JSON-Datei enthält:

- Minecraft-, Forge-, Lumen- und Java-Version, Betriebssystem/Architektur,
  Grafikrenderer, logische Prozessoranzahl und maximalen Java-Heap.
- Sichtweite, Simulationsdistanz, FPS-Limit, VSync und Grafikmodus **bei Beginn**,
  außerdem Einzelspieler/Mehrspieler ohne Welt-/Serverkennung.
- Relative Zeitintervalle von einer Sekunde mit Aufrufanzahl, Gesamtzeit,
  Durchschnitt, Maximum und Anzahl der Aufrufe über 50 ms je Messbereich.
  Ganze Aufrufe werden dem Intervall ihres Endes zugeordnet; die Intervalle
  überlappen sich nicht. Diese Werte sind unabhängig von den gleitenden HUD-Werten.
- FPS, belegten Java-Heap, Menü-/Pausenstatus und aktive Lumen-Modul-IDs einmal
  pro Sekunde. Während eines Stillstands nicht beobachteter Kontext bleibt `null`.
  Das sind Zustandsproben, kein lückenloses Protokoll aller Modulumschaltungen.
- Die stärksten **64 Frame-Ausreißer über 50 ms** mit relativen Endzeitpunkten,
  der Gesamtzahl langsamer Frames und einer Kennzeichnung, falls die Liste gekürzt
  ist. Die Gesamtsummen enthalten weiterhin alle gemessenen Aufrufe/Frames.
- Aufnahmebeginn in UTC, tatsächliche Dauer und Grund für das Ende.

Keine Serveradressen, Spielernamen, Koordinaten, Chats, Inventarinhalte, Welt-, Farm-
oder Bauplandateinamen; keine Übertragung ins Internet. Bitte zur Datei kurz
beschreiben, was während des Ruckelns passiert ist.

Serialisierung und Schreiben laufen auf einem Hintergrund-Thread. Dateinamen sind
automatisch erzeugt und eindeutig; temporäre Dateien werden erst nach vollständigem
Schreiben umbenannt. Während des Exports kann keine zweite Aufnahme gestartet werden.
Bei einem Schreibfehler bleibt die fertige Aufnahme im Speicher und **Export
wiederholen** versucht dieselben Daten erneut zu speichern. Ein Neustart des Spiels
verwirft diesen noch nicht gespeicherten Bericht. Erfolgreiche Berichte werden nicht
automatisch gelöscht.

## Was die Werte bedeuten

- **FPS / Frame-Abstand:** Zeit zwischen zwei Render-Tick-Anfängen. Enthält auch
  FPS-Limit, VSync, Menüs und Wartezeiten. Der Verlauf zeigt die letzten 120 Frames;
  die Referenzlinie entspricht 16,7 ms (60 FPS). Balken über 50 ms sind gelb,
  die Diagrammhöhe ist auf 100 ms begrenzt; der Zahlenwert der Spitze bleibt exakt.
- **Ø / Spitze:** durchschnittliche bzw. längste gemessene Aufrufdauer eines
  Bereichs im aktuellen Fenster, in Millisekunden. Kein Server-MSPT.
- **ms pro s:** aufsummierte Dauer geteilt durch die tatsächliche Fensterlänge.
  Damit sind seltene Tick-Aufgaben und häufige Zeichenaufrufe vergleichbar.
  Die Tabelle sortiert absteigend danach. Kein prozentualer CPU-Auslastungswert.
- **Tick:** die `onTick`-Aufrufe jedes eingeschalteten Moduls, z. B. Farm-Scans,
  Minimap-Aktualisierung und Bauplan-Abgleich.
- **Welt-Vorschau:** `onWorldRender` je Modul, etwa Bauplan-Geisterblöcke und ESP.
- **HUD:** Messen, Anordnen und Zeichnen jedes sichtbaren HUD-Elements, inkl.
  Minimap und Leistungsanzeige selbst. HUD-Editor-Aufrufe werden ebenfalls erfasst.
- **Client-Nachlauf / Speicher:** Fullbright-Update, Todespunktprüfung, Navigation
  und Anstoßen der Kisten-/Farm-Speicherung nach den Modul-Ticks. Zusätzlich wird
  das zeitversetzte Speichern der Lumen-Einstellungen separat gemessen.

Die Messung erfasst verstrichene Zeit auf dem Client-Thread, auch Unterbrechungen
durch Betriebssystem oder Garbage Collection. Sie misst weder reine CPU-Zyklen
noch GPU-Zeit, Server-Ticks oder Hintergrundjobs. Nicht instrumentierte Aufrufe
(z. B. Minecraft selbst, andere Mods, Menüs, einzelne Input-/Tooltip-Callbacks und
Lade-Callbacks) gehören nicht zur Lumen-Summe. Ein auffälliger Frame beweist daher
nicht, dass das oben gelistete Modul ihn verursacht hat.

## Einen Engpass vergleichen

1. In der betroffenen Szene Messung starten, Menü schließen und etwa zehn Sekunden
   normal spielen. Sichtbare Farmen, Bauplan und Kamera möglichst gleich lassen.
2. `/leistung` öffnen und Messung stoppen. Auf Spitzen und hohe **ms pro s** achten.
3. Das verdächtige Modul ausschalten; in derselben Szene eine neue Messung starten.
4. Frame-Zeiten und Bereichszeiten vergleichen. Niedrige Lumen-Zeiten trotz großer
   Frame-Spitzen können Ursachen außerhalb dieser Messbereiche haben.

## Messaufwand

Keine zusätzlichen Welt-/Inventar-Scans oder Abhängigkeiten. Pro Messbereich
feste zehn Sekunden-Buckets und insgesamt 120 Frame-Werte. Das rollende Zeitfenster
umfasst nach dem Anlaufen 9–10 Sekunden; die tatsächliche Länge wird angezeigt und
zur Normierung benutzt. Durchschnitt/Maximum sind innerhalb dieser Buckets exakt,
die Ablaufgrenze ist auf volle Sekunden aufgelöst. Eine Zusammenfassung entsteht
höchstens alle 500 ms, sortiert nach Bedarf beim Anzeigen. Die Messpfade selbst
erzeugen keine Objekte. Ausgeschaltet erfolgen keine Uhrabfragen pro Modulaufruf.
Die Messung hat dennoch Eigenaufwand; reale FPS-Auswirkungen müssen im Spiel
geprüft werden, insbesondere bei sehr hohen Bildraten.

Eine aktive Aufnahme ergänzt feste Zählerarrays für maximal 120 Intervalle pro
Messbereich und 64 Frame-Ausreißer; sie speichert nicht jeden Aufruf einzeln.
Die fertige Aufnahme wird nach erfolgreichem Export freigegeben. Der Diagnose-Export
ist über `format: lumen-performance` und `version: 1` versioniert.

Regressionstest: `performanceMonitorTest` als Teil von Gradle `check`.
Aufnahmen und Datei-Export: `performanceRecordingTest` (ebenfalls Gradle `check`).

Validierung am 14.10.2026: gesamter Quellcode mit JDK 21 und Forge-Eventbus-Prüfern
kompiliert; Messlogik (32 Prüfungen), Aufnahme/Export (55 Prüfungen) und alle 14
bisherigen Regressionstests erfolgreich. Der Exporttest benötigt außerhalb der
Windows-Sandbox Schreibzugriff für das atomare Umbenennen seiner temporären Dateien.
Gradle-Build bleibt an `Unable to establish loopback connection`
blockiert, auch außerhalb der Sandbox. Keine neue JAR installiert; Prüfung der
Anzeige und des tatsächlichen Messaufwands in Minecraft steht noch aus.

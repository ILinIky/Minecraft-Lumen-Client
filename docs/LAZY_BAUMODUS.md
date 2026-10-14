# Lazy-Bauen im Bauplan

Lazy-Bauen setzt erreichbare, fehlende Blöcke einer Bauplan-Ebene nacheinander mit
normalen Minecraft-Platzierungsaktionen. Es ist standardmäßig ausgeschaltet.

## Bedienung

1. Bauplan laden und korrekt platzieren.
2. **B → Platzierung → Lazy-Bauen: An** wählen. Dadurch wird „Nur Ebene“ aktiviert
   und der Materialfokus aufgehoben.
3. Baumaterial ins normale Inventar legen. Ein freier Hotbar-Platz erleichtert
   das automatische Nachladen.
4. **G einmal drücken** schaltet das Bauen ein, **erneut drücken** schaltet es aus.
   Die Taste muss nicht gehalten werden. Bereits übergebene Serveraktionen können
   durch Ausschalten nicht zurückgerufen werden. Der Laufzustand wird nicht gespeichert.

Die Taste lässt sich im Bauplan-Menü inklusive Konfliktanzeige oder unter Minecrafts
Steuerung ändern. Maus-Seitentasten sind ebenfalls möglich. Eine Belegung, die mit
Angreifen oder Benutzen kollidiert, sollte vermieden werden: diese Eingaben pausieren
das automatische Bauen.

**Im Blickfeld** (Standard) berücksichtigt fehlende Stellen in einem schmalen Bereich
vor dem Spieler. **Aktuelle Ebene** berücksichtigt alle erreichbaren Stellen dieser
Ebene, auch seitlich. In beiden Modi muss eine echte Ansetzfläche sichtbar sein.
Der echte Fadenkreuztreffer wird bevorzugt, danach die nächstgelegenen ausführbaren
Platzierungen. Die Reichweite stammt direkt aus Minecrafts Block-Interaktionsattribut
(auch im Kreativmodus). Geprüft werden tatsächliche Treffer und die nächstgelegenen
Punkte der Blockform, einschließlich Kanten, Stufen und Treppen. So scheitert eine
erreichbare Kante nicht mehr an einer zu weit entfernten Flächenmitte.

**Ruhig / Normal / Zügig** erlauben höchstens 2 / etwa 3,3 / 5 Platzierungen pro Sekunde.
Jede einzelne Platzierung muss vorher durch eine passende Server-Blockmeldung
bestätigt sein. Die tatsächliche Geschwindigkeit kann deshalb niedriger liegen.

Das verschiebbare HUD „Bauplan-Ebene“ zeigt Fortschritt, Anzahl der in diesem
Baulauf bestätigten Platzierungen und aktuellen Arbeitsstatus. Ein-/Aus-Zustand
und Shortcut stehen in einer eigenen Zeile, der Arbeitsstatus darunter. Der nächste
Zielblock wird dezent markiert; gelb bedeutet „Serverbestätigung ausstehend“.

## Was automatisch gebaut wird

- Einfache volle Baublöcke, Glas, Stämme/Säulen.
- Einzelne Stufen und Treppen, wenn die normale Platzierung mit deiner aktuellen
  Blickrichtung exakt zum Bauplan passt.
- Nur die ausgewählte Ebene, auch wenn „Bis Ebene“ weitere Ebenen einblendet.

Die Ausrichtung wird vor dem Setzen mit dem bestehenden Bauplan-Abgleich geprüft.
Der Modus dreht weder den Spieler noch die Kamera. Für eine anders ausgerichtete
Treppe musst du dich passend hinstellen bzw. drehen. Doppelte Stufen, wassergefüllte
Zustände, fallende Blöcke, Truhen, Schilder, Türen, Betten und sonstige komplexe oder
interaktive Spezialblöcke bleiben manuell. Ansetzflächen sind ebenfalls auf einfache,
nicht interaktive Baublöcke begrenzt, damit der Modus keine Kisten oder Menüs öffnet.

Blöcke werden nicht abgebaut. Falsche, richtige, unbekannte und explizit akzeptierte
Stellen werden übersprungen. Eine akzeptierte Stelle wird auch nach ihrem Abbau
nicht automatisch neu bebaut. Materialfokus und „Alle Ebenen“ pausieren Lazy-Bauen.
Flüssigkeitsgefüllte Zielpositionen werden nicht automatisch verändert.

## Inventar und Stopps

Es werden nur vorhandene Standard-Blockitems aus Hotbar oder Hauptinventar verwendet.
Benannte oder anders angepasste Items, Rüstung und Zweithand bleiben unangetastet.
Material aus dem Hauptinventar wird mit einem normalen Inventar-Swap in einen freien
Hotbar-Platz getauscht; falls kein Platz frei ist, darf ein gewöhnlicher Blockstapel
getauscht werden. Ist die gesamte Hotbar anders belegt, wird der ausgewählte Platz
verwendet. Sein bisheriger Inhalt wandert per normalem SWAP in den frei werdenden
Inventarplatz; kein Item wird gelöscht oder abgeworfen. Ein leeres Inventar ist
nicht erforderlich. Bei ausbleibendem Materialtausch stoppt der Lauf nach zwei
Sekunden, statt ständig erneut zu tauschen.

Beim Ausschalten wird die ursprüngliche Hotbar-Auswahl zurückgestellt, sofern du sie
nicht selbst geändert hast. Getauschte Stapel bleiben an ihren neuen Inventarplätzen.
Es werden keine Items erzeugt, auch nicht im Kreativmodus.

Fehlendes Material wird konkret angezeigt, beispielsweise **12 Stein fehlen · Ebene 3**.
Die Zahl stammt aus dem bestehenden Blockabgleich für die aktuelle Ebene und zieht
vorhandenes, verwendbares Material ab. Solange die Ebene noch nicht vollständig
bekannt ist, steht **Mind.** davor. Nach dem Nachfüllen das Inventar schließen und
**G** drücken, um fortzusetzen. Andere verfügbare, ausführbare Platzierungen werden
zuerst abgearbeitet. Ein am Mauszeiger gehaltenes Item muss vorher abgelegt werden.

Bei Ablehnung, fehlender Bestätigung nach drei Sekunden, fehlendem passenden
Baumaterial oder ungeeignetem Inventar wird angehalten. Es gibt keine automatische
Wiederholung derselben ausstehenden Platzierung. Ein erneuter Tastendruck
erlaubt einen neuen Durchlauf. Ohne erreichbare Ansetzfläche bleibt der Modus aktiv;
man kann näher herangehen oder den Blick ändern.

Manuelles Benutzen und Abbauen haben Vorrang: Währenddessen werden keine weiteren
Automatik-Aktionen gestartet. Nach dem Loslassen wartet Lazy-Bauen vier Ticks und
setzt den eingeschalteten Lauf fort. Menüs, Fokusverlust, Freecam, Tod,
Zuschauer-/Abenteuermodus ohne Baurecht, Reiten und ein manueller Hotbar-Wechsel
schalten den Lauf dagegen aus. Änderungen
am Plan, seiner Platzierung, Ebene oder akzeptierten Abweichungen stoppen den
laufenden Durchlauf.

Die Ebenenautomatik wartet während einer ausstehenden Platzierung. Nach Bestätigung
der fertigen Ebene kann sie wie gewohnt weiterwechseln. Für die neue Ebene muss die
Bautaste erneut gedrückt werden; dadurch beginnt keine neue Ebene unbeabsichtigt.

## Technische Integration und Prüfung

`feature/LazyBuilder` nutzt `BlueprintPlan.compare`, `BlueprintPlan.matches` und den
bestehenden räumlichen Index. Pro Planung maximal 64 Kandidaten und 48 Sichtprüfungen,
plus maximal 18 Sichtprüfungen zur abschließenden Prüfung mit dem tatsächlich
ausgewählten Item. Keine Planung pro Render-Frame, keine Chunk-Anforderungen.

Platzieren über `MultiPlayerGameMode.useItemOn`, Inventar über reguläre SWAP-Klicks.
Keine eigenen Bewegungs-/Platzierungspakete, kein Reach-Bypass und keine serverseitige
Installation. Serverregeln und Schutzmechanismen entscheiden weiterhin über Erfolg.
Das Beobachten von Block-/Sektionsupdates liegt im bestehenden versionsabhängigen
`ClientPacketListenerMixin`. Clientvorhersagen allein zählen nicht als Bestätigung.

`lazyBuildGateTest` ist Teil von Gradle `check`: Serienplatzierung, Mindestabstand,
Serverbestätigung, doppelte Antworten, Ablehnung, Timeout und Umschalten sowie
Geometrieprüfungen für Kanten und Stufen (insgesamt 30 Prüfungen). Alle zwölf
Regressionstest-Programme bestehen. Alle Java-Quellen kompilieren mit JDK 21 gegen
Forge 57.0.3 einschließlich Forge-EventBus-Prüfern; beide Sprachdateien sind gültiges
JSON. Der vollständige Gradle-Build ist in der Agent-Umgebung weiterhin durch die
bekannte Loopback-Sperre blockiert. Installation und Sicht-/Spielprüfung stehen aus.

Spielprüfung: kleine Steinreihe mit echter Ansetzfläche, Materialwechsel aus dem
Hauptinventar, Umschalten während der Serie und während einer ausstehenden Bestätigung, volle Hotbar mit Werkzeugen, Hindernis
vor der Ansetzfläche, Treppen mit passender/falscher Blickrichtung, geschützter
Serverbereich, Menü-/Ebenen-/Weltwechsel.

Sicherung vor der Erweiterung: `.build-tmp/lazy-build-20261014/before.zip`.

Sicherung vor Toggle-/Reichweiten-Anpassung: `.build-tmp/lazy-toggle-20261014/before.zip`.

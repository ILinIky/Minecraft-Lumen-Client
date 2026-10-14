# Automatischer Ebenenwechsel und Farm Tracker

## Bauplan-Ebenen

Im Bauplan-Menü (B) unter **Platzierung → Automatischer Ebenenwechsel** einschalten.
Dabei wird „Nur Ebene“ gewählt und ein eventuell gesetzter Materialfokus aufgehoben.
Die Einstellung bleibt gespeichert; sie ist zunächst ausgeschaltet.

Die aktuelle Ebene erscheint als `Ebene 3/12 – 87 %` mit Fortschrittsbalken.
Die Anzeige lässt sich unter **HUD → Bauplan-Ebene** ausblenden und im HUD-Editor
verschieben. Sie ist auch im Bauplan-Menü sichtbar.

Die Automatik nutzt `BlueprintPlan.compare`, einschließlich der bestehenden Regeln
für akzeptierte Abweichungen. Ein akzeptierter, inzwischen abgebauter Block fehlt
weiterhin. Ungeprüfte, nicht geladene und nicht unterstützte Positionen blockieren
den Wechsel. Nach einem vollständigen erfolgreichen Durchlauf folgt eine erneute
Prüfung nach mindestens 30 Client-Ticks (etwa 1,5 Sekunden). Große Ebenen benötigen
entsprechend länger. Auf der letzten Ebene bleibt die Anzeige stehen; leere
Zwischenebenen können ebenfalls nach der Wartezeit übersprungen werden.

Bild auf / Bild ab und die Minus-/Plus-Schaltflächen funktionieren weiter.
Wechsel der Ebene, Platzierung, Rotation, Spiegelung oder akzeptierten Abweichungen
verwerfen die laufende Prüfung. Beim Entladen eines Chunks wird sie ebenfalls
zurückgesetzt. Bei „Alle Ebenen“ und Materialfokus pausiert die Automatik. Bei
„Bis Ebene“ wird nur die ausgewählte oberste Ebene auf Fertigstellung geprüft.

Leistung: Der bestehende Scan bleibt auf maximal 2.000 Blockabfragen pro Tick
begrenzt (512 für die aktuelle Ebene, 1.488 für den gesamten Plan). Die Ebenen teilen
sich die vorhandenen Vergleichszustände und Materialzähler.

## Farm Tracker

Öffnen mit **`/farm`** oder **X → Welt → Farm Tracker → Farm-Dashboard**.
Das Modul ist standardmäßig aktiv, kann aber jederzeit ausgeschaltet werden.

1. In die Nähe bepflanzter Felder gehen.
2. Im Dashboard **Vorschläge** wählen.
3. **Übernehmen** anklicken, einen Namen eingeben und speichern.

Erkannt werden Weizen, Karotten, Kartoffeln, Rote Bete und Netherwarzen über ihre
tatsächlich empfangenen Blockzustände. Beete auf gleicher Höhe werden über Wege und
Wasserkanäle hinweg verbunden. Unter **X → Welt → Farm Tracker → Beet-Abstand** sind
0 bis 16 freie Blöcke einstellbar, standardmäßig 6. Die Verbindung gilt auch über
mehrere aufeinanderfolgende Beete. Unterschiedliche Höhen und Dimensionen bleiben getrennt.

Der Scan untersucht schrittweise 32 Blöcke in X/Z-Richtung und 12 Blöcke ober-/unterhalb
des Spielers. Ein Durchlauf dauert bei 20 TPS ungefähr sieben Sekunden, danach folgt
eine kurze Pause. Es werden keine Chunks angefordert. Am Rand abgeschnittene Flächen
werden nicht als vollständige Farm vorgeschlagen: dafür näher an die Farm gehen.

Die gespeicherte Fläche besteht aus den bei der Bestätigung bepflanzten Positionen,
nicht aus einem Rechteck mitsamt Wasser und Wegen. Die Positionen bleiben auch nach
der Ernte erhalten. Erntereife in Prozent = reife Pflanzen / gespeicherte Positionen.
Unbepflanzte Positionen zählen also nicht als reif. Die Erkennung berücksichtigt
die jeweilige maximale Wachstumsstufe (z.B. Netherwarzen 3 statt Weizen 7).

Karten zeigen Name, Position, Dimension, Fläche, Fortschrittsbalken und Messalter.
„Bereit zur Ernte“ erscheint nur bei vollständig reifen und aktuell bestätigten
Farmen. Bei entladenen, nur teilweise verfügbaren oder noch ungeprüften Bereichen
bleibt der letzte vollständig gemessene Stand erhalten und wird entsprechend
gekennzeichnet. Es gibt keine Hochrechnung des Wachstums außerhalb geladener Chunks.

Mit **Name** umbenennen; **Löschen** benötigt einen zweiten Bestätigungsklick.
**Ansteuern** legt bei Bedarf einen normalen Wegpunkt in der Gruppe „Farmen“ an und
setzt ihn als Kompassziel. Erneutes Ansteuern verwendet denselben Wegpunkt. Beim
Umbenennen wird auch der verknüpfte Wegpunkt aktualisiert. Ein einmal erstellter
Wegpunkt bleibt beim Löschen der Farm als eigenständiger Wegpunkt erhalten.
Verworfene und gelöschte Flächen werden während der Sitzung nicht sofort wieder
vorgeschlagen (die begrenzte Merkliste wird beim Dimensions-/Weltwechsel geleert).

Grenzen: maximal 128 Farmen pro Welt, 4.096 Positionen pro Farm und 32 gleichzeitige
Vorschläge. Größere erkannte Flächen werden nicht stillschweigend abgeschnitten.
Gespeicherte Farmen besitzen einen umschließenden rechteckigen Bereich je Höhe.
Beete innerhalb oder im eingestellten Abstand gehören zu derselben Farm. Neue
Pflanzplätze erscheinen als **Farmbereich erweitern** und werden mit **Übernehmen**
hinzugefügt. Bereits erfasste Positionen werden nicht erneut angeboten. Nur tatsächliche
Pflanzplätze zählen zur Erntereife, nicht die Wege und Lücken im umschließenden Bereich.
Beim Aktivieren/Betreten und Ändern des Abstands werden auch ältere benachbarte Farmen
vereinigt. Der zuerst gespeicherte Name, die ID, Position und der Wegpunkt bleiben
erhalten; die Erntereife wird vollständig neu geprüft. Vor Änderungen wird eine
Momentaufnahme als `.before-areas-<ID>.bak` gespeichert. Ein kleinerer Abstand trennt
bereits vereinigte Bereiche nicht wieder. Maximal 4096 Pflanzplätze pro Bereich;
bei Überschreitung bleibt der bisherige Speicherstand unverändert.
Die optionale Hervorhebung reifer Pflanzen in der Welt ist nicht implementiert.

## Übersicht je Pflanzenart

Jede Farmkarte enthält einzelne Zeilen mit Prozentwert und Balken für Weizen,
Karotten, Kartoffeln, Rote Bete und Netherwarzen. Die Werte beziehen sich auf die
zuletzt bekannten Pflanzplätze der jeweiligen Art. Geerntete Plätze behalten ihre
Zuordnung und zählen mit 0 %; eine andere Neubepflanzung aktualisiert die Zuordnung.
Bei ungeladenen Farmen gelten die gespeicherten Werte, entsprechend dem Status der
Karte. Weitere Pflanzenarten stehen gegebenenfalls im Tooltip der Zeilen.

## Eigene Farmfläche (optional)

Die automatische Erkennung bleibt verfügbar. Zusätzlich gibt es **Fläche wählen**
im Dashboard. Für eine große Anlage mit Wegen:

1. An die erste Ecke auf Höhe der Pflanzen gehen und `/farm ecke1` eingeben.
2. Zur gegenüberliegenden Ecke gehen und `/farm ecke2` eingeben.
3. Im Dashboard **Fläche wählen → Fläche speichern** mit eigenem Namen verwenden,
   alternativ `/farm speichern Name der Farm`.

Die Ecken verwenden die Spielerposition; die Fußhöhe wird auf die nächste ganze
Blockhöhe aufgerundet, damit das Stehen auf Ackerboden die Pflanzenhöhe auswählt.
Beide Ecken sind eingeschlossen, auch die Höhenbegrenzung gilt. Im Auswahlfenster
können die Ecken ebenfalls per Schaltfläche an der aktuellen Position gesetzt werden.
`/farm abbrechen` bzw. **Auswahl aus** verwirft die laufende Auswahl.

Die Auswahl wird mit maximal 768 Positionen pro Tick geprüft; nur vollständig
verfügbare Bereiche werden gespeichert. Maximal 65.536 Positionen im Auswahlvolumen
und 4096 tatsächlich bekannte Pflanzplätze. Wasser und Wege zählen nicht zur Reife.
Später hinzugefügte Beete innerhalb der gespeicherten Grenzen werden beim normalen
512-Positionen-Scan erkannt. Bei Entladen bleiben die letzten vollständigen Werte.
Bereits erfasste Farmen müssen vollständig in die Auswahl passen und werden mit
Sicherung in die gemeinsame Karte übernommen. Teilüberschneidungen werden abgelehnt.
Der Beet-Abstand erweitert einen manuell gewählten Bereich nicht.

## Speicherung und Prüfung

### Erntehinweise

Unter **X → Welt → Farm Tracker → Erntehinweise** ein-/ausschalten und bei
**Erntehinweis ab** die Grenze von 1 bis 100 % wählen (Standard: 100 %).
Nur eine vollständig geladene, neue Messung kann eine Meldung auslösen. Die
Einblendung nennt Farmname, gemessene Erntereife und eingestellte Grenze.

Jede Farm meldet einmal pro beobachtetem Erntezyklus. Die Sperre wird mit der Farm
gespeichert, übersteht Umbenennen, Dimensions-/Weltwechsel und normales Beenden.
Sie wird erst gelöst, wenn bei unveränderter Fläche weniger reife Pflanzen
gemessen werden **und** die Erntereife unter die aktuelle Grenze fällt. Eine
kleine Teilernte oberhalb der Grenze erzeugt deshalb keinen Meldungsstrom.
Erst beim erneuten Erreichen der Grenze folgt der nächste Hinweis. Änderungen
der Prozentgrenze oder eine größere Farmfläche allein gelten nicht als Ernte.
Vollständig zwischen zwei Beobachtungen liegende Ernte-/Wachstumszyklen können
clientseitig nicht erkannt werden. Alte Dateien ohne Meldungsstatus beginnen
mit einer noch nicht gemeldeten Farm.

Die Hinweise verwenden die vorhandenen vollständigen Messungen, ohne zusätzliche
Weltabfragen. Regressionstest: `farmHarvestNoticeTest` (Gradle `check`).

### Leistung

Die Erkennung mit größerem Beet-Abstand nutzt räumliche Gruppen vorhandener
Pflanzen statt für jede Position alle möglichen Nachbarpositionen zu erzeugen.
Damit entfallen Millionen kurzlebiger Hilfsobjekte. Die normale Erkennung direkt
benachbarter Pflanzen behält ihren kleinen, festen Nachbarschaftstest.
Bereichsfilter für manuelle Farmen und die Vorbereitung der Bereichsgrenzen beim
Laden laufen im Hintergrund. Unveränderte Flächen bauen ihre Indizes nicht bei
jeder neuen Reifemessung erneut auf.

Lokaler synthetischer Belastungstest (JDK 21, 16.384 Pflanzen auf vier Ebenen,
je zwei Aufwärmrunden und Mittel aus drei Messungen):

| Beet-Abstand | Vorher, Zeit / Allokation je Durchlauf | Nachher, Zeit / Allokation je Durchlauf |
| --- | --- | --- |
| 6 | 31,7 ms / 85,0 MiB | 7,3 ms / 5,4 MiB |
| 16 | 114,5 ms / 460,0 MiB | 11,0 ms / 5,4 MiB |

Dies misst nur den Hintergrundalgorithmus, keine FPS oder Minecraft-Framezeiten.
Spielseitige Blockabfragen bleiben auf 768 Erkennungs-/Auswahlpositionen plus
512 Messpositionen pro Tick begrenzt; JSON-Schreiben bleibt im Hintergrund.
Große Bauplan-Vorschauen (bis zu 4096 Geisterblöcke) und die Minimap, besonders
die Höhlenberechnung neuer Chunks, bleiben mögliche Lastquellen. Ob sie auf dem
jeweiligen Rechner tatsächlich ruckeln, erfordert eine Messung im laufenden Spiel.

Sicherung vor Erntehinweisen/Optimierung: `.build-tmp/harvest-notices-20261014/before.zip`.

Farmdateien: `config/examplemod/farms/<Welt-oder-Server-Schlüssel>.json`, Formatversion 2; vorhandene Dateien der Version 1 werden weiter gelesen.
Der Weltschlüssel und das atomische Schreiben stammen aus der bestehenden
Wegpunkt-/Konfigurationslogik. Laden, JSON-Erzeugung und Speichern laufen im Hintergrund;
Schreibvorgänge werden gesammelt. Weltwechsel verwerfen ausstehende UI-Ergebnisse,
vorherige Speichervorgänge werden vor erneutem Laden derselben Welt abgearbeitet.

Pro Tick maximal 768 Positionen für neue Farmen plus 512 für gespeicherte Farmen.
Zusammenhängende Flächen werden aus einer unveränderlichen Momentaufnahme im
Hintergrund berechnet. Farmdaten werden nur bei vollständig geladenen Messungen
aktualisiert. Fehlerhafte Speicherdateien werden nicht überschrieben.

Neue Gradle-Checks: `layerProgressTest`, `farmTrackerTest` (beide in `check`).
Alle Haupt-/Testquellen wurden mit JDK 21 gegen Forge 57.0.3 einschließlich der
Forge-EventBus-Annotationsprüfer kompiliert, alle zwölf
Regressionstest-Programme erfolgreich ausgeführt. Der vorhandene Gradle-Build ist
in der Agent-Umgebung wegen der bekannten Loopback-Sperre nicht verfügbar.
Sichtprüfung und Installation im Spiel stehen noch aus.

Abschlussprüfung Farmbereiche: 45 Farm-Checks bestanden, einschließlich Abstand,
umschließender Fläche, mehrfacher Übernahme, Höhen-/Dimensionstrennung und Größenlimit.
Der vollständige Build wurde erneut versucht; Gradle meldet weiterhin
`Unable to establish loopback connection`. Keine neue JAR installiert.

Im Spiel prüfen: Ebene fertigstellen, Abweichung akzeptieren, während der Wartezeit
einen Block entfernen, manuell wechseln, Chunks entladen; Farm vorschlagen, benennen,
abernten, neu bepflanzen, Dimension wechseln, Welt neu öffnen und Wegpunkt ansteuern.

Sicherung vor den Änderungen: `.build-tmp/layers-farms-20261013/before.zip`.

Erweiterung Pflanzenarten/manuelle Auswahl: 58 Farm-Regressionen bestanden; Sicherung `.build-tmp/farm-beds-materials-20261014/before.zip`.

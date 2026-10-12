# Sammelliste, Materialtausch und Anzeige

## Material-Sammelliste

Öffnen: `/sammelliste` oder X → Welt → Sammelliste → Sammelliste öffnen.
Mit **Item hinzufügen** ein Item suchen und eine genaue Zielmenge von 1 bis
2.000.000 eingeben. Ein Klick auf einen vorhandenen Eintrag bearbeitet die Menge;
0 entfernt das eigene Ziel. Eigene Ziele bleiben in der Client-Konfiguration
gespeichert und gehören auch zu Profilen. Maximal 256 eigene Einträge.

Unter B → Material → **Sammelliste im HUD** lässt sich der aktuelle Bauplan
verknüpfen. Die Zielmenge entspricht dann dem noch offenen Bedarf und sinkt beim
Bauen. Bei einem Materialtausch wechselt auch das benötigte Item. Ein anderer
geladener Bauplan ersetzt den verknüpften Bedarf; eigene Ziele bleiben bestehen.
Gleiche Items aus beiden Quellen verwenden die größere Zielmenge, keine Summe.
Ein einzelnes Material kann über **Dieses Material anheften** als festes eigenes
Ziel übernommen werden. Dieses Ziel folgt späteren Bauplanänderungen nicht.

Das HUD zeigt Inventar / Ziel plus Fortschrittsbalken. Es zählt lose Items aus dem
Spielerinventar, nicht Inhalte von Shulkern, Kisten oder dem Mauszeiger.
Aktualisierung alle fünf Client-Ticks. Erledigte Einträge sind standardmäßig
ausgeblendet. Die Zahl der HUD-Zeilen ist einstellbar; weitere Einträge bleiben im
Menü erreichbar. Position über X → Client → HUD → HUD bearbeiten verschieben.
Sowohl das Modul Sammelliste als auch HUD und dessen Element Sammelliste müssen an sein.

Ungeprüfte Bauplanpositionen zählen zum Bedarf. Bei deaktivierter Bauplanvorschau
oder in einer anderen Dimension zeigen die Ziele den letzten Abgleich; das
Sammellisten-Menü kennzeichnet dies. Ohne geladenen Bauplan wartet die Verknüpfung.

## Bauplan-Material ersetzen

B → Material → **Material ersetzen**. Links eine ursprüngliche Blockart wählen,
dann einen passenden Ersatz suchen. Bretter, Treppen und Stufen werden einzeln
ersetzt. Es erscheinen nur Blockitems, die sämtliche gespeicherten Eigenschaften
des ursprünglichen Blocks übernehmen können. So bleiben unter anderem Ausrichtung,
Achse, Treppenform, obere/untere Hälfte und Doppelstufen erhalten. Drehen und
Spiegeln funktionieren weiterhin.

Vorschau, Blockauswahl und Materialbedarf verwenden sofort den Ersatz. Die
Welt und die Bauplandatei werden nicht geändert. **Original** setzt eine Blockart
zurück; **Alle Originalmaterialien** setzt alle Ersetzungen zurück. Ersetzungen
werden mit der Bauplan-Platzierung pro Welt gespeichert und beim nächsten Betreten
wieder geladen. Der Dateibrowser zeigt weiterhin das Original-Vorschaubild.

Nach dem Tausch wird der Bauplan erneut abgeglichen. Übernommene Abweichungen an
betroffenen Blöcken werden zurückgesetzt, andere bleiben erhalten. Die räumlichen
Indizes werden wiederverwendet, damit große Pläne nicht erneut indexiert werden.

## Prüfung

`gradlew build` führt zusätzlich `materialTargetsTest` (Sammelziele,
Palettentausch) und `blueprintMaterialTest` (Zustandserhalt, Paletten-Schema,
Rotation/Spiegelung, Zurücksetzen und Wiederanwenden von Ersetzungen) über `check` aus.
Sicherung vor der Erweiterung: `.build-tmp/build-tools-20261012/src-before.zip`.

## Menü-Transparenz und Suchfelder

X → Client → Design → **Menü-Deckkraft**: 20–100 %, kleinere Werte machen die
Menüflächen transparenter. **Hintergrund abdunkeln** regelt unabhängig davon die
Abdunklung der Spielwelt (0–80 %). Texte und Suchfelder behalten ihren Kontrast.
Beide Werte werden in der Client-Konfiguration und in Profilen gespeichert.
Alle Lumen-Eingabefelder verwenden Text ohne Schatten und hellere Platzhalter;
Textfarben passen sich einem Stilwechsel sofort an.

## Spieler-Radar

X → Render → **Spieler-Radar** einschalten. **Spieler auswählen** öffnet eine
durchsuchbare Liste der Online-Spieler und deiner bereits gespeicherten Auswahl.
Klick auf einen Spieler schaltet ihn in der Auswahl an oder aus. Die Auswahl wird
über die Spieler-UUID gespeichert, sodass ein Namenswechsel sie nicht aufhebt.
Eine leere Auswahl zeigt niemanden. Offline-Einträge lassen sich weiter abwählen.

Der **Radius** ist von 8 bis 256 Blöcken einstellbar und zählt die gerade Entfernung
einschließlich Höhenunterschied. Ausgewählte Spieler im Radius erscheinen rechts
mit Name und Entfernung, die nächsten zuerst. Verlässt jemand den Radius oder
kennt der Client seine Position nicht mehr, verschwindet der Eintrag. Der Abgleich
erfolgt alle fünf Client-Ticks. Die Online-Liste allein reicht nicht für eine
Entfernungsanzeige: Es zählen nur bekannte Positionen in der aktuellen Dimension.

Das HUD-Element Spieler-Radar lässt sich im HUD-Editor verschieben und separat
ausblenden. Ohne Treffer ist es außerhalb des Editors unsichtbar. Das HUD und das
Radar-Modul müssen eingeschaltet sein. Die Zahl der sichtbaren Zeilen ist im
Radar-Modul einstellbar. Keine zusätzlichen Chat- oder Tonmeldungen.

`playerRadarTest` prüft Auswahl, Radiusgrenze, Sortierung, Ausschluss des eigenen
Spielers und Namenswechsel. Sicherung vor diesen Änderungen:
`.build-tmp/design-radar-20261012/src-before.zip`.

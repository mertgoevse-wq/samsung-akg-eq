# Design System: Sonora
**Project ID:** Kein Stitch-Projekt verbunden; Designgrundlage sind `Theme.kt`, `Screens.kt`, `SpatialScreen.kt` und die UX-Spezifikation v0.1.

## 1. Visual Theme & Atmosphere

Sonora wirkt ruhig, konzentriert und technisch, ohne wie ein Messinstrument auszusehen. Das dunkle Anthrazit schafft eine unaufdringliche Hörumgebung; warmes Kupfer lenkt den Blick nur auf aktive Zustände und primäre Aktionen. Großzügige Abstände und wenige, klar gruppierte Flächen sollen die tägliche Bedienung mit einer Hand erleichtern. Die Sprache bleibt direkt und alltagsnah, während Diagnosewerte auf den dafür vorgesehenen Bereich beschränkt sind.

Die visuelle Spezifikation ist im Repository als Entwurf gekennzeichnet. Dieses Dokument fasst belegte Werte aus der implementierten Compose-Oberfläche und dem Theme zusammen; es behauptet keine Stitch-Screenshot- oder Projektmetadaten, die hier nicht verfügbar sind.

## 2. Color Palette & Roles

- **Nacht-Anthrazit (#0E1013):** Grundfläche und dunkelster Hintergrund. Es hält die Oberfläche visuell zurück und lässt Inhalte ohne dekorative Effekte hervortreten.
- **Graphit-Fläche (#171A1F):** Standardfläche für Karten und Navigation. Die Fläche trennt zusammengehörige Inhalte subtil vom Hintergrund.
- **Schiefer-Fläche (#232830):** Sekundäre Fläche und Material-3-`surfaceVariant`, etwa für nicht ausgewählte, zurückhaltende UI-Flächen.
- **Warmes Kupfer (#C98B5E):** Primärfarbe für aktive Zustände, Auswahl und wichtigste Aktionen. Der Akzent signalisiert Bedienbarkeit statt Dekoration.
- **Helles Kupfer (#E3B591):** Sekundärer warmer Akzent für ergänzende Hervorhebung.
- **Papierweiß (#F2EDE7):** Primärer Text auf dunklen Flächen; weicher als reines Weiß und damit passend zur ruhigen Klangmarke.
- **Nebelgrau (#9AA0A6):** Sekundärtext, Erklärungen und weniger wichtige Informationen.
- **Bernstein-Warnung (#E0A458):** Warnungen und Fehlerzustände, die nicht mit dem normalen Kupfer-Aktivzustand verwechselt werden sollen.

Farbe ist nie das einzige Statussignal: Auswahl und Aktivität müssen zusätzlich durch Text, Form oder ein Bedienelement wie RadioButton/Switch erkennbar sein. Die UX-Spezifikation verlangt mindestens 4,5:1 Textkontrast und 3:1 für große Elemente; Kontrastwerte einzelner Tokenpaare wurden in diesem Repository nicht automatisiert nachgewiesen.

## 3. Typography Rules

- **Schriftfamilie:** Android-Systemschrift; in `Theme.kt` ist keine individuelle Schriftfamilie eingebunden.
- **Große Marken-/Einstiegsebene:** leichte Display-Schrift mit 40 sp und leicht negativer Laufweite (−0,5 sp), sparsam für den Sonora-Auftritt.
- **Bildschirmtitel:** kräftige, klare Hierarchie mit `headlineMedium` (28 sp, SemiBold); kleinere Titel nutzen 22 sp SemiBold.
- **Abschnitts- und Kartentitel:** `titleLarge` (20 sp, SemiBold) oder `titleMedium` (16 sp, Medium), abhängig von ihrer Bedeutung.
- **Fließtext:** 16 sp mit 24 sp Zeilenhöhe für zentrale Erklärungen; 14 sp mit 21 sp Zeilenhöhe für kompakte Inhalte.
- **Sekundärtext und Messwerte:** 12 sp mit 18 sp Zeilenhöhe. Werte und Einheiten bleiben explizit lesbar; sie werden nicht allein durch Farbe unterschieden.
- **Kleine Labels:** 12 sp Medium mit leicht geöffneter Laufweite (0,4 sp), zum Beispiel für Status und dB-Werte.

Verwende Material-Theme-Typografie statt ad hoc gesetzter Größen. Schreibe in der UI auf Deutsch und bevorzuge kurze, verständliche Aussagen.

## 4. Component Stylings

* **Buttons:** Material-3-Buttons verwenden Kupfer für die primäre Aktion und dunkle Schrift auf der Kupferfläche. Umrandete und Text-Buttons sind sekundär. Onboarding-Aktionen sind breit und 52 dp hoch; allgemeine interaktive Ziele sollen laut UX-Vorgabe mindestens 48 dp erreichen. Es gibt keine eigenständige dekorative Button-Schattenbehandlung.
* **Cards/Containers:** Informationskarten liegen auf Graphit-Flächen (#171A1F) mit großzügig gerundeten Ecken (20 dp) und etwa 18 dp Innenabstand. Auswahlkarten im Bereich „Raum“ haben sanft gerundete Ecken (16 dp) und mindestens 56 dp Höhe; eine ausgewählte Karte erhält eine hellere Schieferfläche plus RadioButton und Statussemantik. A/B-Flächen sind etwas kompakter gerundet (14 dp). Flächen bleiben flach: Im Theme und den betrachteten Komponenten ist keine explizite Elevation oder kein Drop-Shadow definiert.
* **Inputs/Forms:** Material-3-Eingabefelder und Slider fügen sich in das dunkle Farbschema ein. Regler zeigen ihren aktuellen Wert und ihre Einheit; Frequenzregler erhalten TalkBack-Beschreibungen. Auswahl erfolgt über Chips, RadioButtons oder Switches, nicht über reine Farbänderung.
* **Navigation:** Eine untere Navigationsleiste führt durch „Jetzt“, „Klang“, „Raum“ und „Profile“; „Mehr“ bündelt ergänzende Einstellungen. Symbole stehen immer neben Textlabels.
* **Warnungen und Status:** Warntext nutzt Bernstein und eine klare Erklärung. Nicht unterstützte Audiofunktionen werden ausdrücklich benannt; die Oberfläche darf keine nicht vorhandene DSP-Wirkung suggerieren.

### Bereich „Raum“ — konkrete Komponenten und Zustände

- **Kopfbereich:** Der Titel „Raum“ steht auf der normalen Bildschirmtitel-Ebene. Darunter erklärt ein gedämpfter Einzeiler, dass es Sonoras eigene Simulationen und keine lizenzierten Fremdformate sind.
- **Moduswahl:** Eine vertikal scrollbare Liste bietet „Aus“ und die neun Modi Kino-Weit, Objekt-Raum, Raum 21.1, Kopf-3D, Live-Saal, Studio-Trocken, Sprach-Fokus, Nacht sowie Breit & Warm. Jede Auswahl ist eine flache, 16-dp-gerundete Fläche mit mindestens 56 dp Höhe, Titel, kurzer Beschreibung und RadioButton. Der ganze Eintrag ist auswählbar und für TalkBack als zusammengehörige Radiowahl mit Name, Beschreibung und Auswahlstatus ausgezeichnet.
- **Nicht ausgewählte Karte:** Graphitfläche (#171A1F), helle Titeltypografie und Nebelgrau für die Beschreibung. Keine Schatten und kein dekoratives Bild.
- **Ausgewählte Karte:** Schieferfläche (#232830) plus aktivierter RadioButton und expliziter Zustand „Ausgewählt“. Die Auswahl ist damit gleichzeitig über Fläche, Form und Semantik erkennbar, nicht nur über Kupferfarbe.
- **Aus-Zustand:** Ein eigener Eintrag „Aus“ ist immer Teil der Liste. Seine Beschreibung sagt, dass der Originalklang ohne Raumklangeffekt zurückkehrt. Bei Auswahl werden virtuelle Effekte deaktiviert; der Eintrag wird wie jeder Modus eindeutig markiert.
- **Stärke-Regler:** Unter den Modi folgt ein einzelner horizontaler Slider mit Titel „Stärke“ und sichtbarem Prozentwert (0–100 %). Er hat 19 Zwischenstufen plus beide Endpunkte (21 Positionen). TalkBack erhält eine Beschriftung mit dem aktuellen Prozentwert. Die Stärke ist ein gemeinsamer Intensitätswert, keine separate Einstellung pro Modus.
- **Teilweise oder nicht verfügbare Verarbeitung:** Wenn ein Modus aktiv ist, aber das Gerät nicht alle angeforderten Klanganteile unterstützt, erscheint unter dem Regler eine schieferfarbene, abgerundete Erklärungskarte. Sie nutzt die normale Fließtextgröße und benennt, dass der Equalizer aktiv bleibt und verfügbare Raumklangeffekte teilweise wirken können. Sie ist kein zusätzliches Aktions- oder Fehlerdialogelement.
- **Dauerhafter DSP-Hinweis:** Am Ende des Inhalts steht in Nebelgrau, dass Virtualisierung und Raumhall von den Geräte-Audioeffekten abhängen und Mittenfokus sowie Dynamikkontrolle derzeit nicht separat verarbeitet werden. Dieser Hinweis macht die technische Grenze sichtbar, ohne die Moduswahl als vollständige Umsetzung aller Klangbeschreibungen auszugeben.
- **Allgemeiner Hauptschalter aus:** Ist Sonora global deaktiviert, bleibt die gewählte Ansicht lesbar, aber es wird kein Raumklang angewendet. Die UI soll in diesem Fall nicht den Eindruck eines hörbar aktiven Modus erwecken; die Statuskommunikation folgt dem globalen Aus-Zustand.

## 5. Layout Principles

- **Ein-Hand-Bedienung:** Die Hauptnavigation sitzt unten; wichtige tägliche Aktionen bleiben leicht erreichbar.
- **Klares Raster:** Bildschirme verwenden in der Regel 20 dp Außenabstand, Karten etwa 18 dp Innenabstand und vertikale Gruppierungsabstände von ungefähr 10–14 dp. Diese Werte stammen aus der implementierten Oberfläche und sind Richtwerte, keine universell erzwungene Bildschirmmatrix.
- **Inhaltsgruppen statt Kartenwüste:** Flache Karten fassen nur tatsächlich zusammengehörige Einstellungen zusammen. Zwischen Abschnitten bleibt sichtbare Luft; dekorative Diagramme und unmotivierte Flächen sind zu vermeiden.
- **Lange Listen:** Gleichartige Modus- und Profilzeilen werden vertikal gescrollt. Frequenz- und Moduslisten dürfen wachsen, ohne den Bildschirm zu überladen.
- **Barrierefreiheit:** Interaktive Trefferflächen mindestens 48 dp, Regler mit Namen und Wert, und Status nie ausschließlich über Farbe vermitteln. Systemschriftvergrößerung darf nicht zu abgeschnittenen Texten führen.
- **Tiefe und Bewegung:** Funktionale Ebenen entstehen primär durch geringe Helligkeitsunterschiede der Flächen, nicht durch Schatten oder Verläufe. Animationen sollen nur direkte Aktionsrückmeldung geben; die UX-Spezifikation nennt 120–180 ms als Zielbereich und lehnt permanente Dekoration ab.

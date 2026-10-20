# Sonora — UX-Spezifikation (Entwurf)

**Status:** Draft · **Version:** v0.1 · **Stand:** 2026-10-20
**Zielgerät:** Samsung Galaxy A56 5G · **Hörer:** Samsung EO-IC100 (USB-C)
**Bezug:** [PRD](../prd.md)

---

## 1. Gestaltungsprinzipien

1. **Ein klarer Hauptschalter.** Die tägliche Nutzung braucht genau eine Entscheidung:
   Sonora an oder aus. Alles andere ist optional.
2. **Keine Attrappen.** Jeder Regler, jeder Wert und jede Anzeige ist mit echten
   Messwerten oder echten Einstellungen verbunden. Keine Platzhalter-Buttons,
   keine erfundenen Diagramme, keine dekorativen Kartenwüsten.
3. **Sprache ohne Fachbegriffe.** Der Interview-Modus fragt in Alltagssprache
   („Klingt es dir zu dumpf oder zu schrill?“), nie in Frequenzwerten.
4. **Hörbar vor sichtbar.** Jede Änderung wird sofort hörbar; die Oberfläche
   bestätigt sie nur. Der A/B-Vergleich ist überall mit einem langen Druck erreichbar.
5. **Ruhige, dunkle Oberfläche.** Dunkles Grundthema, eine Akzentfarbe, viel Weißraum.
   Farbe trägt Bedeutung (aktiv/inaktiv/Warnung), nicht Dekoration.
6. **Ein-Hand-Bedienung.** Primäre Aktionen liegen in der unteren Bildschirmhälfte,
   Trefferflächen mindestens 48 dp.
7. **Zustände sind ehrlich.** Fehlende Berechtigung, geschützter Inhalt, beendeter
   Hintergrunddienst und fehlender Hörer werden sichtbar erklärt — nie still verschluckt.

---

## 2. Informationsarchitektur

| # | Bereich | Zweck | Primäre Aktion |
|---|---|---|---|
| 1 | **Onboarding** | Hörer erkennen, Berechtigungen erklären, ersten Klang einstellen | „Loslegen“ |
| 2 | **Jetzt** | Alltag: Status, aktives Profil, Umgebung, An/Aus | Hauptschalter |
| 3 | **Klang** | Equalizer mit 10 / 31 / 64+ Bändern, Vorverstärkung, A/B | Band ziehen |
| 4 | **Interview** | Geführtes Gespräch zum persönlichen Profil | „Weiter“ / „Überspringen“ |
| 5 | **Profile** | Eigene Profile, Presets, Import/Export | Profil wählen |
| 6 | **Raum** | Virtuelle Klangräume (Kino, Objekt, 21.1, Kopf-3D, Saal …) | Modus wählen |
| 7 | **Streaming** | Inhaltserkennung, optionale Kontoverbindung | Schalter |
| 8 | **Einstellungen** | Berechtigungen, Datenschutz, Akku, Diagnose | Schalter |
| 9 | **Diagnose** | Latenz, Akku, Effect-Status, lokales Ereignisprotokoll | Bericht exportieren |

Navigation: vier Hauptbereiche (**Jetzt · Klang · Raum · Profile**) in einer unteren
Leiste; Interview, Streaming, Einstellungen und Diagnose werden von dort geöffnet.
Kein Hamburger-Menü, keine versteckten Hauptfunktionen.

---

## 3. Bildschirme

### 3.1 Onboarding

- Schritt 1: Willkommen in Alltagssprache, ein Satz zum Datenschutz („Das Mikrofon
  bleibt auf dem Handy“).
- Schritt 2: Hörererkennung. Zeigt „Samsung EO-IC100 erkannt“ oder eine ruhige
  Anleitung zum Einstecken. Kein Sackgassen-Zustand.
- Schritt 3: Drei Hörbeispiele (dumpf / ausgewogen / schrill) zur groben Startkurve.
- Schritt 4: Berechtigungen einzeln erklärt — Mikrofon (Umgebung), Benachrichtigungen
  (Hintergrundbetrieb), optional Medienzugriff (Inhaltserkennung). „Jetzt nicht“ ist
  überall möglich und blockiert die App nicht.

**Zustände:** Hörer nicht verbunden · Mikrofon verweigert · Benachrichtigungen
verweigert · Abbruch und späterer Wiedereinstieg.

### 3.2 Jetzt

- Kopfzeile: „Sonora ist aktiv“ + aktives Profil.
- Große Karte: aktueller Inhalt (App/Titel, nur wenn erkannt) und aktiver Raum.
- Umgebungsanzeige: ruhig / mittel / laut, als drei klare Stufen — kein Fake-Pegel.
- Hauptschalter unten, Daumen erreichbar.
- Schnellzugriff: drei zuletzt genutzte Presets.

**Zustände:** kein Ton · geschützter Inhalt (Hinweis: „Diese App erlaubt keine
systemweite Klangänderung“) · Dienst läuft nicht · Hörer getrennt.

### 3.3 Klang (Equalizer)

- Umschalter **10 · 31 · 64+** Bänder. Beim Wechsel bleibt die Kurve erhalten
  (Interpolation), nichts springt.
- 10 Bänder: große, fingerfreundliche Regler.
- 31 Bänder: 1/3-Oktave, horizontale Beschriftung nur an Ankerpunkten (63, 250, 1k, 4k, 16k).
- 64+: feinere Auflösung, horizontale Scroll-/Zoom-Geste, gleiche Bedienlogik.
- Vorverstärkung als eigener Regler; Clipping-Warnung erscheint erst, wenn sie nötig ist.
- A/B: langer Druck auf den Hauptschalter vergleicht „Sonora“ und „Original“.
- „Zurücksetzen“ mit Undo-Snackbar.

**Zustände:** keine Änderung · geändert, nicht gespeichert · Clipping-Gefahr ·
geschützter Inhalt.

### 3.4 Interview

- Eine Frage pro Bildschirm, maximal 8 Fragen.
- Jede Frage bietet ein hörbares Beispielpaar (A/B) statt Fachwissen.
- Fortschritt als schmale Linie, keine Punkte-Orgie.
- „Überspringen“ ist immer sichtbar; die App fällt auf ein sicheres Standardprofil zurück.
- Abschluss: Zusammenfassung in einem Satz + „Profil anhören“ + „Speichern“.

**Zustände:** Frage unbeantwortet · Audioausgabe blockiert · Abbruch · Wiederaufnahme.

### 3.5 Profile

- Liste mit Name, Kurzbeschreibung, zuletzt genutzt.
- Aktionen: umbenennen, duplizieren, löschen (mit Undo), exportieren, importieren.
- Presets sind klar als „von Sonora“ markiert, eigene Profile als „von dir“.
- Suche erst ab mehr als 8 Profilen.

**Zustände:** leer (mit ehrlicher Erklärung) · Importfehler · Speicher voll.

### 3.6 Raum (Spatial)

- Karten pro Modus mit einem Satz Alltagssprache und einem Hörtest.
- Modi: **Kino-Weit**, **Objekt-Raum**, **Raum 21.1**, **Kopf-3D**, **Live-Saal**,
  **Studio-Trocken**, **Sprach-Fokus**, **Nacht**.
- Intensität als ein Regler; „Aus“ stellt den Originalklang wieder her.
- Hinweis, dass es sich um eigene Klangsimulationen handelt, nicht um lizenzierte
  Fremdformate.

**Zustände:** Modus aktiv · deaktiviert · Inhalt stereo/mono · geschützter Inhalt.

### 3.7 Streaming

- Schalter 1: „Erkennen, was läuft“ (Medienzugriff). Erklärt, dass nur Titel/App
  gelesen werden, kein Audio.
- Schalter 2: „Spotify/YouTube verbinden“ (optional, Internet). Zeigt genau, welche
  Daten wohin gehen. Abmelden löscht das Token.
- Kein automatischer Netzwerkzugriff, solange beide Schalter aus sind.

**Zustände:** Berechtigung fehlt · Verbindung abgelaufen · Anbieter nicht erreichbar ·
kein Internet.

### 3.8 Einstellungen

- Berechtigungen mit Status und Direktlink.
- Datenschutz: „Mikrofon nur auf dem Gerät“, „Ereignisse nur lokal“, Export/Löschen.
- Akku: aktueller Verbrauch pro Stunde, Zielwert 5 %.
- Updates: Version, Profil-Schema, „Nach Update prüfen“.
- Diagnose öffnen.

### 3.9 Diagnose

- Effect-Status (global / nur App / nicht verfügbar).
- Gemessene Zusatzlatenz in Millisekunden.
- Akkuverbrauch der letzten Stunde.
- Letzte Fehler in Alltagssprache, mit „Bericht kopieren“ (ohne Audiodaten).

---

## 4. Kernabläufe

### 4.1 Erster Start

1. Onboarding → Hörer erkennen → grobes Hörprofil.
2. Berechtigungen erklären; Ablehnung führt zu eingeschränktem, aber nutzbarem Modus.
3. „Jetzt“ zeigt den Hauptschalter; Sonora startet mit dem Standardprofil.
4. Angebot: „Persönliches Profil im 2-Minuten-Gespräch erstellen“ (überspringbar).

### 4.2 Täglicher Start

1. App startet im Hintergrund, Notification zeigt „Sonora aktiv · Profil X“.
2. Musik startet → Erkennung wählt passendes Profil.
3. Nutzer ändert einen Regler → Änderung sofort hörbar, Profil wird als „angepasst“ markiert.
4. Speichern oder verwerfen.

### 4.3 Interview

1. Start aus „Jetzt“ oder „Profile“.
2. Acht Fragen, je ein A/B-Beispiel.
3. Auswertung auf dem Gerät, Profilvorschau.
4. Speichern → Profil ist sofort aktiv; A/B gegen Original möglich.

### 4.4 Fehlerfall „geschützter Inhalt“

1. Sonora erkennt, dass die globale Klangänderung nicht greift.
2. Ruhiger Hinweis im „Jetzt“-Bereich, kein Fehlerdialog.
3. Angebot: „In dieser App ist nur der eingebaute Klang möglich“ + Link zur Erklärung.

---

## 5. Visuelle Sprache

- **Grund:** dunkles Anthrazit, eine warme Akzentfarbe (Kupfer), zwei Neutrale.
- **Typografie:** Systemschrift für Fließtext, eine gut lesbare Zahlenschrift für Werte.
- **Bewegung:** nur Rückmeldung auf Aktionen (120–180 ms), kein Dauer-Animation.
- **Icons:** eine Familie, 24 dp, klare Bedeutung, kein Icon ohne Text im Onboarding.
- **Keine** Verläufe ohne Funktion, keine schwebenden Karten ohne Inhalt, keine
  Diagramme, die nur dekorativ sind.

---

## 6. Barrierefreiheit

- TalkBack-Beschriftungen für jeden Regler inkl. aktuellem Wert und Einheit.
- Kontrast mindestens 4,5:1 für Text, 3:1 für große Elemente.
- Trefferflächen ≥ 48 dp; Regler zusätzlich per Plus/Minus bedienbar.
- Systemschriftgröße und Großschrift werden unterstützt; kein abgeschnittener Text.
- Farbe ist nie das einzige Signal (aktiv = Farbe + Form + Text).
- Haptisches Feedback beim Einrasten von Reglern, abschaltbar.

---

## 7. Offene Punkte

- [ ] Visuelle Detailgestaltung (Farbwerte, Abstände) nach dem ersten lauffähigen Build.
- [ ] Echtes Geräte-Screenshot als Design-Referenz ergänzen.
- [ ] Verhalten bei Samsung-eigenen Klangeffekten (Dolby/Adapt Sound) dokumentieren.
- [ ] Rechtliche Prüfung der Modusnamen vor einer Play-Store-Version.

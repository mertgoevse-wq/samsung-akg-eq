# Sonora — Systemweiter KI-Klangbegleiter für das A56

> Systemweite, KI-gestützte Klangsteuerung für das Samsung Galaxy A56 mit den EO-IC100: adaptiver Equalizer, persönliche Klangprofile und virtueller Raumklang — ohne Root, mit KI direkt auf dem Gerät.

---

## Kurz erklärt (in einfachen Worten)

- **Was du bekommst:** eine App, die den Klang auf deinem ganzen Handy verbessert — nicht nur in einer einzigen App.
- **Wie sie klingt:** Sie misst die Umgebung, kennt deinen Hörgeschmack aus einem kurzen Gespräch und stellt den Klang automatisch passend ein.
- **Was du steuerst:** 10, 31 oder 64+ Klangregler, fertige Presets, eigene Profile und verschiedene Raumklänge.
- **Was sie nicht tut:** Sie nimmt nichts heimlich auf und sendet nichts ins Internet, solange du es nicht einschaltest.
- **Was zuerst gebaut wird:** die Version ohne Root für dein A56 und deine EO-IC100. Root und andere Handys kommen später.

---

## 1. Name der Initiative (Initiative Name)

`Sonora — Systemweiter KI-Klangbegleiter für das A56`

Sonora ist der Arbeitsname für die App, die systemweit auf dem Galaxy A56 läuft. Der Name wird in APK, Store-Eintrag, Profilformat und Dokumentation einheitlich verwendet.

---

## 2. Dokumentstatus (Document Status)

| Field | Detail |
|---|---|
| **Status** | `Draft` |
| **Version** | v0.1 |
| **Last Updated** | 2026-10-20 |
| **Author** | Sonora Project Lead |
| **Owner** | Sonora Project Lead |
| **Reviewers** | Sonora Core Architecture Team |
| **Approvers** | Sonora Project Lead |
| **Approval Date** | — |

---

## 3. Hintergrund (Background)

### Problembeschreibung (Problem Statement)

Der Standard-Klangpfad des Galaxy A56 ist für alle gleich: Er kennt weder den Menschen, der hört, noch die Umgebung, in der gehört wird, noch die Eigenheiten der angeschlossenen EO-IC100. Wer mit kabelgebundenen USB-C-Hörern Musik, Filme, Podcasts oder Spiele hört, bekommt eine feste Abstimmung — im Zug zu dünn, im Büro zu basslastig, bei Sprache zu unklar. Vorhandene Equalizer-Apps lösen das nur teilweise: Entweder wirken sie ausschließlich in der eigenen App, oder sie verlangen einen Root-Zugriff, der Garantie und Sicherheit des Geräts gefährdet. Es fehlt ein Weg, der ohne Eingriff ins System so weit wie möglich systemweit wirkt, die Umgebung einbezieht und den Hörgeschmack in normaler Sprache erfragt statt in Frequenzwerten.

Betroffen ist in Phase 1 genau eine Person: der Projektinhaber mit seinem A56 und den EO-IC100. Das Problem tritt täglich und bei jedem Inhalt auf; die Auswirkung ist kein Ausfall, sondern dauerhaft mittelmäßiger Klang und der Zwang, zwischen Apps und Einstellungen zu wechseln.

### Evidenz & Daten (Evidence & Data)

Formale Nutzerforschung liegt für dieses persönliche Projekt nicht vor. Die Problembeschreibung stützt sich auf die am 2026-10-20 im Intake dokumentierte Ausgangslage des Nutzers: Der Samsung-Standardklang passt sich weder der Umgebung noch dem Hörgeschmack an, und bestehende Equalizer-Lösungen sind entweder app-lokal oder root-pflichtig. Diese Aussage ist eine Team-Annahme und noch nicht durch Messdaten belegt — sie wird in Phase 0 mit einem Vorher/Nachher-Hörtest auf dem Gerät überprüft. Ein belastbarer Ausgangswert für die Klangbewertung existiert noch nicht und wird als erster Messwert erhoben.

### Benchmarks

Keine externen Benchmarks herangezogen. Die Zielwerte in §4 und §7 sind interne, am 2026-10-20 vom Nutzer bestätigte Ziele und nicht mit Wettbewerbs- oder Branchenmessungen vergleichbar.

### Kontext & Historie (Context & History)

Es gibt keine frühere Version von Sonora und kein Vorgänger-PRD. Bekannte verwandte Ansätze sind app-lokale Equalizer, systemweite Effekte über die Android-Effekt-Schnittstelle, Root-Module (JamesDSP, ViPER4Android) und Streaming-Dienste mit eigenen Klangreglern. Sonora kombiniert den No-Root-Systemweg mit einer lokalen KI und einem geführten Interview; die Root-Variante bleibt eine spätere, getrennte Phase.

### Kosten der Untätigkeit (Cost of Inaction)

Ohne Sonora bleibt der Klang dauerhaft auf dem Samsung-Standard: keine Umgebungsanpassung, kein persönliches Profil, kein Raumklang über Apps hinweg. Der Nutzer müsste weiter manuell zwischen Apps und Einstellungen wechseln und auf ein hörbar besseres Erlebnis mit seinen EO-IC100 verzichten. Für die spätere Familie-/Play-Store-Version entstünde zusätzlich ein Zeitnachteil, weil die technische Machbarkeit des No-Root-Systemwegs dann erst spät geklärt würde.

### Regulatorischer Kontext (Regulatory Context)

| Regulation | Jurisdiction | Key Requirements for This Initiative | Status |
|---|---|---|---|
| DSGVO / GDPR | EU/Deutschland | Mikrofon-/Umgebungsdaten gelten als personenbezogene Daten; Verarbeitung nur mit Rechtsgrundlage, transparent, datenminimierend. Phase 1 verarbeitet ausschließlich auf dem Gerät, ohne Speicherung und ohne Versand. | `[TBD — compliance review required]` |
| Google Play Data Safety & Developer Program Policy | Global (Play Store) | Korrekte Deklaration von Mikrofon-, Vordergrunddienst- und Netzwerknutzung; Datenschutzerklärung; keine irreführenden Angaben. Relevant ab Phase 3. | `[TBD — compliance review required]` |
| EU AI Act — Transparenzpflichten | EU | Der KI-Interviewmodus interagiert mit dem Nutzer; die Interaktion muss als KI erkennbar sein. Keine Hochrisiko-Einstufung vorgesehen. | `[TBD — legal review required]` |
| Marken- und Lizenzrecht (Dolby, DTS, Auro-3D) | EU/Global | Geschützte Formatnamen dürfen nicht als eigene, lizenzierte Fähigkeit dargestellt werden. Sonora nutzt eigene Modusnamen und eigene DSP-Simulationen. | `[TBD — legal review required]` |

**Compliance Review Owner:** Sonora Project Lead
**Review Deadline:** `[TBD — vor Phase 3 (Play Store)]`

---

## 4. Ziel (Objective)

### Primäres Ziel (Primary Objective)

> Der A56-Nutzer hört mit seinen EO-IC100 in jeder Alltagssituation hörbar besseren Klang und nutzt Sonora täglich — ohne Absturz, ohne spürbaren Akkuverlust und ohne Eingriff ins System.

### Key Results

| # | Key Result | Baseline | Target | Timeline |
|---|---|---|---|---|
| KR1 | Klangbewertung im verblindeten Vorher/Nachher-Test (1–10) | Noch nicht gemessen; Referenz ist der Samsung-Standardklang | ≥ 8/10 in der ersten Version; ≥ 9/10 nach dem ersten erfolgreichen Zyklus | 2026-11-03 |
| KR2 | Abstürze in 7 Tagen | 0 (App existiert noch nicht) | 0 Abstürze | 2026-11-03 |
| KR3 | Akkuverbrauch im Hintergrund | Noch nicht gemessen | ≤ 5 % pro Stunde | 2026-11-17 |
| KR4 | Nutzungstage pro Woche | 0 (App existiert noch nicht) | 7 von 7 Tagen | 2026-12-03 |

### Ausrichtung an Unternehmens-OKRs (OKR Alignment)

Es gibt kein Unternehmens-OKR; Sonora ist ein persönliches Projekt. Oberstes persönliches Ziel ist ein täglich genutzter, hörbar besserer Klang auf dem A56. Das PRD ist so geschnitten, dass es später als Produkt-OKR für Familie, Freunde und eine Play-Store-Version weiterverwendet werden kann.

---

## 5. Umfang & Grenzen (Scope & Boundaries)

### In Scope

- Systemweite Klangbeeinflussung ohne Root, so weit Android und One UI es zulassen.
- Equalizer mit umschaltbaren 10-, 31- und 64+-Bändern, Vorverstärkung und A/B-Vergleich.
- Lokale Umgebungsmessung über das Mikrofon und automatische Anpassung.
- Geführtes Interview in Alltagssprache zur Erstellung eines persönlichen Profils.
- Presets und eigene Profile inklusive Export/Import und Update-Migration.
- Virtuelle Raumklang-Modi (eigene DSP-Simulationen, keine Fremd-Codecs).
- Erkennung laufender Inhalte über Medienzugriff und automatische Profilwahl.
- Optional: Spotify-/YouTube-Kontoverbindung, nur nach ausdrücklichem Einschalten.
- Stabiler Hintergrundbetrieb mit sichtbarer Benachrichtigung, Hörschutz und Diagnose.
- Aufbau einer aktualisierbaren App-Struktur (Schema-Versionierung, Migrationen).

### Out of Scope (Außerhalb des Umfangs)

| Item | Reason | Future Plan |
|---|---|---|
| Root-Variante in Phase 1 | Garantie- und Sicherheitsrisiko; erst nach validierter No-Root-Version sinnvoll | Phase 4 als separate APK, nicht im Play Store |
| Echte Dekodierung von Dolby Atmos, DTS:X oder Auro-3D | Proprietäre Lizenzen; ohne Vertrag nicht zulässig | Eigene DSP-Simulationen; Lizenzprüfung vor Play Store |
| iOS-Unterstützung | Zielgerät ist ausschließlich das Android-A56 | Keine |
| Bluetooth-Kopfhörer-Kalibrierung | Fokus auf die kabelgebundene EO-IC100 | Später, nach Phase 2 |
| Cloud-KI und Server-Auswertung | Nutzerentscheidung: KI nur auf dem Gerät, Internet nur auf Wunsch | Optionale Online-Funktion in einer späteren Phase |
| Play-Store-Veröffentlichung in Phase 1 | Erst nach Stabilitäts- und Datenschutznachweis | Phase 3 |
| Mehrbenutzerkonten und soziale Funktionen | Persönliche App ohne Konto-Modell | Keine |
| Werbung, In-App-Käufe, Monetarisierung | Nicht Teil des Ziels | Keine |

### Plattform- und Segmentabdeckung (Platform & Segment Coverage)

| Dimension | Coverage |
|---|---|
| **Platform** | Android 15 / One UI 7 auf Samsung Galaxy A56 5G; kabelgebundene USB-C-Hörer EO-IC100 |
| **User Segment** | Einzelnutzer (Projektinhaber); später Familie und Freunde |
| **Geography** | Deutschland (EU/DSGVO) |
| **Phase** | Phase 1 — persönliche tägliche Nutzung |

---

## 6. Hypothese (Hypothesis)

> *"We believe that a system-wide, on-device adaptive sound engine for the A56 owner with the Samsung EO-IC100 will result in a clearly better listening experience and daily use, because the phone's standard audio path offers no automatic environment adaptation and no personal profile across apps."*

Auf Deutsch: Wir glauben, dass eine systemweite, auf dem Gerät laufende adaptive Klang-Engine für den A56-Nutzer mit den EO-IC100 zu einem deutlich besseren Hörerlebnis und täglicher Nutzung führt, weil der Standard-Klangpfad des Handys weder eine automatische Umgebungsanpassung noch ein persönliches Profil über Apps hinweg bietet.

### Confidence Level

`Medium`

**Reasoning:** Die technische Machbarkeit des systemweiten No-Root-Wegs ist auf dem A56 noch nicht gemessen; der Nutzen für den Nutzer ist dagegen klar beschrieben und seine Zielwerte sind bestätigt. Die Hypothese wird in Phase 0 mit einem Prototyp und einem Vorher/Nachher-Hörtest geprüft.

### Falsifikationsbedingung (Falsification Condition)

Die Hypothese gilt als widerlegt, wenn nach zwei Abstimmungsrunden eine verblindete Klangbewertung unter 8/10 bleibt, wenn der Nutzer nach 30 Tagen an weniger als 4 von 7 Tagen Sonora verwendet, oder wenn der Hintergrundbetrieb trotz Optimierung mehr als 5 % Akku pro Stunde verbraucht.

### Post-Launch Learning Plan

Das Sonora Core Team prüfen die Hypothese am 2026-11-17 (2 Wochen), am 2026-12-03 (30 Tage) und am 2027-02-03 (Quartal). Ergebnisse werden im PRD und in der Diagnose dokumentiert.

---

## 7. Erfolgsmetriken (Success Metrics)

### Primary Metrics

| Metric | Type | Baseline | Target | Measurement Method | Timeline | Owner |
|---|---|---|---|---|---|---|
| Klangbewertung im verblindeten A/B-Test (1–10) | Leading | Noch nicht gemessen; Referenz ist der Samsung-Standardklang | ≥ 8/10 in Version 1; ≥ 9/10 nach erfolgreichem Zyklus | 10 verblindete Durchläufe in der App, Protokoll lokal | 2026-11-03 | Project Lead |
| Abgeschlossene persönliche Profile | Leading | 0 (App existiert noch nicht) | Mindestens 1 Profil in Woche 1 | Lokales Ereignis `interview_completed` | 2026-11-10 | Project Lead |
| Nutzungstage pro Woche | Lagging | 0 (App existiert noch nicht) | 7 von 7 Tagen | Lokales Nutzungsprotokoll `app_opened` | 2026-12-03 | Project Lead |

### Guardrail Metrics

| Metric | Baseline | Alert Threshold | Measurement Method | Owner |
|---|---|---|---|---|
| Akkuverbrauch im Hintergrund | Noch nicht gemessen | > 5 % pro Stunde | Android-Batteriestatistik plus Sonora-Diagnose | Project Lead |
| Abstürze pro 7 Tage | 0 (neue App) | ≥ 1 | Lokales Crash-Log plus manueller Wochentest | Project Lead |
| Zusatzlatenz des Effekts | Noch nicht gemessen | > 10 ms | Loopback-Messung auf dem A56 | Project Lead |

### Target Basis

Alle Zielwerte wurden am 2026-10-20 im Interview vom Nutzer bestätigt. Es gibt keine externe Benchmark-Grundlage; die Werte sind interne Ziele für Phase 1.

---

## 8. Anforderungen (Requirements)

### Functional Requirements

#### US-001 — Systemweiter Equalizer mit umschaltbaren Bändern

**Priority:** `Must-have`

**User Story:**
> As an **A56 owner**, I want a system-wide equalizer with switchable 10, 31, or 64+ bands, so that every app sounds better without switching apps.

Auf Deutsch: Als A56-Nutzer möchte ich einen systemweiten Equalizer mit umschaltbaren 10, 31 oder 64+ Bändern, damit jede App besser klingt, ohne dass ich die App wechseln muss.

**Acceptance Criteria:**
```gherkin
Scenario: Happy path — Equalizer wirkt in einer Musik-App
  Given Sonora ist aktiv und die EO-IC100 sind angeschlossen
  When ich im Bereich "Klang" 31 Bänder wähle und den Bass anhebe
  Then ist die Änderung sofort hörbar und bleibt beim Wechsel zu einer anderen App erhalten

Scenario: Geschützter Inhalt blockiert den systemweiten Effekt
  Given eine Streaming-App erlaubt keine globale Klangänderung
  When ich dort die Wiedergabe starte
  Then zeigt Sonora einen ruhigen Hinweis und bietet die verfügbaren Ausweichwege an, ohne die Wiedergabe zu unterbrechen
```

**Dependencies:** US-008 (Hintergrundbetrieb)
**Notes:** Der No-Root-Weg nutzt Androids Effekt-Schnittstelle am globalen Ausgabemix; die tatsächliche Wirksamkeit auf One UI wird in Phase 0 gemessen.

---

#### US-002 — Umgebungsadaptive Klangregelung

**Priority:** `Must-have`

**User Story:**
> As a **music listener**, I want Sonora to measure the environment on-device and adjust the sound automatically, so that music stays clear on the train, in the office, or outdoors.

Auf Deutsch: Als Musikhörer möchte ich, dass Sonora die Umgebung direkt auf dem Handy misst und den Klang automatisch anpasst, damit Musik im Zug, im Büro oder draußen klar bleibt.

**Acceptance Criteria:**
```gherkin
Scenario: Anpassung an eine laute Umgebung
  Given die Umgebung ist laut und das Mikrofon ist erlaubt
  When ich Musik starte
  Then passt Sonora den Klang innerhalb von zwei Sekunden an und zeigt die Stufe "laut" an

Scenario: Mikrofon verweigert
  Given der Nutzer hat den Mikrofonzugriff abgelehnt
  When Sonora startet
  Then bleibt das zuletzt gespeicherte Profil aktiv und die App erklärt, welche Funktion fehlt
```

**Dependencies:** US-001, US-008
**Notes:** Audio wird nicht gespeichert und nicht gesendet; analysiert werden nur Pegel- und Spektralwerte.

---

#### US-003 — KI-Interview für ein persönliches Klangprofil

**Priority:** `Must-have`

**User Story:**
> As a **first-time Sonora user**, I want the app to interview me in plain language with sound examples, so that it creates a personal profile that matches my hearing and taste.

Auf Deutsch: Als neuer Sonora-Nutzer möchte ich, dass die App mich in normaler Sprache mit Hörbeispielen interviewt, damit ein persönliches Profil entsteht, das zu meinem Gehör und Geschmack passt.

**Acceptance Criteria:**
```gherkin
Scenario: Interview vollständig abschließen
  Given ich öffne das Interview zum ersten Mal
  When ich alle Fragen beantworte und auf "Speichern" tippe
  Then ist das neue Profil sofort aktiv und ich kann es gegen den Originalklang vergleichen

Scenario: Unsichere Antwort
  Given ich kann eine Frage nicht beantworten
  When ich "Kann ich nicht sagen" wähle
  Then bietet Sonora ein hörbares A/B-Beispiel an und überspringt die Frage, ohne das Interview abzubrechen
```

**Dependencies:** US-004
**Notes:** Maximal acht Fragen; die Auswertung läuft vollständig auf dem Gerät.

---

#### US-004 — Profile, Presets und Sicherung

**Priority:** `Must-have`

**User Story:**
> As an **A56 owner**, I want to save, rename, export, and import sound profiles and choose curated presets, so that I can switch contexts and keep my settings after reinstalling.

Auf Deutsch: Als A56-Nutzer möchte ich Klangprofile speichern, umbenennen, exportieren und importieren sowie fertige Presets wählen, damit ich zwischen Situationen wechseln kann und meine Einstellungen nach einer Neuinstallation behalte.

**Acceptance Criteria:**
```gherkin
Scenario: Profil speichern und umbenennen
  Given ich habe die Klangkurve verändert
  When ich auf "Speichern" tippe und einen Namen eingebe
  Then erscheint das Profil in der Liste und bleibt nach einem Neustart erhalten

Scenario: Import einer beschädigten Profildatei
  Given ich wähle eine ungültige Profildatei
  When der Import startet
  Then bricht Sonora den Import ab, erklärt den Grund und verändert kein bestehendes Profil
```

**Dependencies:** None
**Notes:** Exportformat versioniert; Migrationen sind Pflicht (US-011).

---

#### US-005 — Virtuelle Raumklang-Modi

**Priority:** `Must-have`

**User Story:**
> As a **movie listener**, I want to switch between independent virtual spatial modes, so that I get an immersive experience on my wired earphones.

Auf Deutsch: Als Film- und Serienhörer möchte ich zwischen unabhängigen virtuellen Raumklang-Modi wechseln, damit ich mit meinen kabelgebundenen Hörern ein räumliches Erlebnis bekomme.

**Acceptance Criteria:**
```gherkin
Scenario: Modus wechseln
  Given ein Film läuft
  When ich von "Kopf-3D" zu "Raum 21.1" wechsle
  Then ändert sich der Klang sofort und die Wiedergabe läuft ohne Unterbrechung weiter

Scenario: Raumklang ausschalten
  Given ein Raumklang-Modus ist aktiv
  When ich "Aus" wähle
  Then höre ich wieder den unveränderten Originalklang
```

**Dependencies:** US-001
**Notes:** Eigene DSP-Simulationen; keine Dekodierung proprietärer Formate. Modusnamen und Markenhinweis siehe §9.

---

#### US-006 — Erkennung laufender Inhalte und automatische Profilwahl

**Priority:** `Should-have`

**User Story:**
> As an **A56 owner**, I want Sonora to recognize which app is playing and pick a matching profile, so that I do not have to adjust manually.

Auf Deutsch: Als A56-Nutzer möchte ich, dass Sonora erkennt, welche App gerade abspielt, und automatisch ein passendes Profil wählt, damit ich nicht manuell nachstellen muss.

**Acceptance Criteria:**
```gherkin
Scenario: Musik startet in Spotify
  Given der Medienzugriff ist erlaubt
  When Spotify die Wiedergabe startet
  Then wählt Sonora das Musikprofil und zeigt die erkannte App an

Scenario: Medienzugriff verweigert
  Given der Medienzugriff wurde nicht erteilt
  When eine App abspielt
  Then bleibt das aktuelle Profil aktiv und Sonora erklärt, wie die Erkennung aktiviert werden kann
```

**Dependencies:** US-004
**Notes:** Es werden nur App- und Titelinformationen gelesen, kein Audio.

---

#### US-007 — Optionale Spotify-/YouTube-Kontoverbindung

**Priority:** `Should-have`

**User Story:**
> As a **streaming listener**, I want to optionally connect Spotify or YouTube to improve recommendations, so that profiles match the content better, only when I turn it on.

Auf Deutsch: Als Streaming-Hörer möchte ich optional Spotify oder YouTube verbinden, damit die Profile besser zum Inhalt passen — aber nur, wenn ich es einschalte.

**Acceptance Criteria:**
```gherkin
Scenario: Verbindung einschalten
  Given ich öffne den Streaming-Bereich
  When ich die Kontoverbindung aktiviere und der Anbieter zustimmt
  Then zeigt Sonora genau an, welche Daten wohin gehen, und speichert das Token sicher auf dem Gerät

Scenario: Verbindung trennen
  Given eine Kontoverbindung besteht
  When ich sie trenne
  Then löscht Sonora das Token und stellt alle Netzwerkaufrufe zum Anbieter ein
```

**Dependencies:** US-006, DEP-004, DEP-005
**Notes:** Phase 2/3; standardmäßig aus; ohne Einschalten keine Netzwerkaufrufe.

---

#### US-008 — Stabiler Hintergrundbetrieb

**Priority:** `Must-have`

**User Story:**
> As an **A56 owner**, I want Sonora to run reliably in the background with a clear notification, so that the sound stays consistent across apps and the phone stays stable.

Auf Deutsch: Als A56-Nutzer möchte ich, dass Sonora zuverlässig im Hintergrund läuft und eine klare Benachrichtigung zeigt, damit der Klang über alle Apps hinweg gleich bleibt und das Handy stabil bleibt.

**Acceptance Criteria:**
```gherkin
Scenario: Bildschirm aus, Musik läuft weiter
  Given Sonora ist aktiv und Musik läuft
  When der Bildschirm eine Stunde ausgeschaltet ist
  Then bleibt der Effekt aktiv und der Akkuverbrauch liegt höchstens bei 5 % pro Stunde

Scenario: System beendet den Dienst
  Given Android beendet den Hintergrunddienst
  When ich die Musik erneut starte
  Then startet Sonora den Dienst neu, stellt das letzte Profil wieder her und informiert mich einmalig
```

**Dependencies:** US-001, US-002
**Notes:** Vordergrunddienst mit sichtbarer Benachrichtigung; keine versteckten Hintergrundprozesse.

---

#### US-009 — Hörschutz und Clipping-Schutz

**Priority:** `Must-have`

**User Story:**
> As a **listener**, I want Sonora to prevent clipping and excessive boost, so that my ears and earphones are protected.

Auf Deutsch: Als Hörer möchte ich, dass Sonora Übersteuerung und zu starke Anhebung verhindert, damit meine Ohren und meine Hörer geschützt sind.

**Acceptance Criteria:**
```gherkin
Scenario: Starke Bassanhebung
  Given ich hebe den Bass sehr stark an
  When die Verstärkung die Grenze erreicht
  Then greift der Limiter, der Klang verzerrt nicht und Sonora zeigt einen kurzen Hinweis

Scenario: Lange laute Sitzung
  Given ich höre länger als 60 Minuten sehr laut
  When die Grenze erreicht ist
  Then zeigt Sonora eine ruhige Lautstärke-Erinnerung und senkt nicht ungefragt die Lautstärke
```

**Dependencies:** US-001
**Notes:** Der Limiter ist nicht abschaltbar; die Lautstärkeerinnerung ist abschaltbar.

---

#### US-010 — Onboarding und Geräteerkennung

**Priority:** `Must-have`

**User Story:**
> As a **first-time user**, I want the app to recognize my EO-IC100 and guide me through setup in plain language, so that I get a good result without technical knowledge.

Auf Deutsch: Als neuer Nutzer möchte ich, dass die App meine EO-IC100 erkennt und mich in normaler Sprache durch die Einrichtung führt, damit ich ohne technisches Wissen zu einem guten Ergebnis komme.

**Acceptance Criteria:**
```gherkin
Scenario: Hörer ist angeschlossen
  Given die EO-IC100 sind per USB-C verbunden
  When ich Sonora zum ersten Mal öffne
  Then erkennt Sonora den Hörer und bietet eine kurze Startabstimmung an

Scenario: Hörer fehlt
  Given kein Hörer ist angeschlossen
  When ich Sonora öffne
  Then erklärt die App ruhig, was fehlt, und blockiert die Einrichtung nicht
```

**Dependencies:** None
**Notes:** Keine Sackgassen; jeder Schritt ist überspringbar.

---

#### US-011 — Update-Sicherheit und Profil-Migration

**Priority:** `Must-have`

**User Story:**
> As an **A56 owner**, I want updates to preserve my profiles and settings, so that I do not lose my sound setup when the app improves.

Auf Deutsch: Als A56-Nutzer möchte ich, dass Updates meine Profile und Einstellungen erhalten, damit ich meine Klangeinstellung nicht verliere, wenn die App verbessert wird.

**Acceptance Criteria:**
```gherkin
Scenario: Update von Version 1 auf Version 2
  Given ich habe eigene Profile gespeichert
  When ich die neue Version installiere und starte
  Then sind alle Profile und Einstellungen unverändert vorhanden

Scenario: Inkompatibles altes Profil
  Given ein Profil stammt aus einem älteren Schema
  When Sonora es lädt
  Then migriert Sonora es, erklärt die Änderung einmalig und behält eine Sicherungskopie
```

**Dependencies:** US-004
**Notes:** Schema-Version im Profilformat; Migrationstest vor jedem Release.

---

### Non-Functional Requirements

| ID | Category | Requirement | Priority | Verification Method |
|---|---|---|---|---|
| NFR-001 | Performance | Zusätzliche Latenz des Effekts höchstens 10 ms | `Must-have` | Loopback-Messung auf dem A56, zehn Durchläufe |
| NFR-002 | Performance | Hintergrund-CPU im Leerlauf im niedrigen einstelligen Prozentbereich | `Must-have` | Perfetto/Android Studio Profiler über 30 Minuten |
| NFR-003 | Battery | Höchstens 5 % Akku pro Stunde bei aktiver Wiedergabe | `Must-have` | Ein-Stunden-Akkutest, Android-Batteriestatistik |
| NFR-004 | Stability | 0 Abstürze in 7 Tagen | `Must-have` | Lokales Crash-Log plus manueller Wochentest |
| NFR-005 | Audio Quality | Kein hörbares Clipping; THD+N unter der Hörschwelle | `Must-have` | Messsignal plus verblindeter Hörtest |
| NFR-006 | Privacy | Kein Speichern und kein Versand von Audio; Analysedaten nur lokal | `Must-have` | Netzwerk-Mitschnitt plus Code-Review |
| NFR-007 | Security | OAuth-Tokens ausschließlich im Android Keystore; keine Secrets im Repository | `Must-have` | Secret-Scan und Code-Review |
| NFR-008 | Compatibility | Läuft auf Android 15 / One UI 7 des A56 mit EO-IC100 | `Must-have` | Gerätetest auf dem Zielgerät |
| NFR-009 | Accessibility | TalkBack, 48-dp-Trefferflächen, Kontrast 4,5:1 | `Must-have` | Accessibility Scanner und TalkBack-Test |
| NFR-010 | Updateability | Profil-Schema versioniert; Migration ohne Datenverlust | `Must-have` | Upgrade-Test von der vorherigen Build-Version |
| NFR-011 | Network | Keine Netzwerkaufrufe, solange die Streaming-Verbindung aus ist | `Must-have` | Netzwerk-Mitschnitt über 24 Stunden |
| NFR-012 | Hearing Safety | Limiter immer aktiv; keine ungefragte Lautstärkeänderung | `Must-have` | Testsignal und Bedienprüfung |

---

## 9. Lösung (Solution)

### Proposed Approach

Sonora besteht aus sechs Bausteinen:

1. **Klang-Engine:** native DSP-Kette mit umschaltbaren 10-, 31- und 64+-Band-EQs, Vorverstärkung, Limiter und Crossfeed. Die Engine hängt — wo Android es erlaubt — am globalen Ausgabemix; sonst greift sie in der eigenen Wiedergabe und zeigt die Einschränkung offen an.
2. **Umgebungsanalyse:** kurze, getaktete Mikrofonmessungen (Pegel, Spektrum), die nur Klassifikation und Korrekturwerte erzeugen. Kein Audio wird gespeichert oder gesendet.
3. **Lokale KI:** kleines Modell (LiteRT/TensorFlow Lite) für Umgebungsklasse und Profilvorschlag, Inferenz über NNAPI (NPU) mit GPU-/CPU-Rückfall. Die NPU wird nur für die Entscheidung genutzt, nicht für jeden Audiosample.
4. **Profil- und Preset-Speicher:** lokale Datenbank mit versioniertem Format, Export/Import und Migrationen.
5. **Inhaltserkennung:** Medienzugriff liest App- und Titelinformationen; die optionale Kontoverbindung ergänzt Empfehlungen nur nach Einschalten.
6. **Hintergrunddienst:** Vordergrunddienst mit sichtbarer Benachrichtigung, Wiederherstellung nach Systemabbruch und Diagnoseprotokoll.

### Raumklang-Modi (eigene Simulationen)

| Sonora-Modus | Klangcharakter | Beschriebene Referenzfamilie | Umsetzung |
|---|---|---|---|
| Kino-Weit | breite Klangwand mit Rear-Anteil | Dolby-Surround-Charakter | eigene Matrix- und HRTF-Virtualisierung |
| Objekt-Raum | Höhen und einzelne Objekte | Dolby-Atmos-Charakter | eigene objektbasierte Virtualisierung |
| Raum 21.1 | viele virtuelle Lautsprecher | 21.1-/22.2-Kino | eigene 22-Kanal-Virtualisierung |
| Kopf-3D | Klang um den Kopf | binaurales Hören | eigene HRTF-Faltung |
| Live-Saal | natürlicher Raumhall | Auro-3D-artiger Live-Klang | eigene frühe Reflexionen und Faltung |
| Studio-Trocken | neutral und direkt | Studio-Monitor | lineare Zielkurve |
| Sprach-Fokus | Dialog klar und vorn | Dialog-Enhancer | Mitten-EQ und Dynamikkontrolle |
| Nacht | leise, aber verständlich | Night-Mode | Kompressor plus Limiter |
| Breit & Warm | entspannt, analog | Crossfeed-Klang | Crossfeed-Filter |

**Markenhinweis:** Dolby, Dolby Atmos, Dolby Surround, DTS, DTS:X und Auro-3D sind Marken ihrer jeweiligen Inhaber. Sonora dekodiert diese Formate nicht und bezeichnet die Modi bewusst mit eigenen Namen. Eine rechtliche Prüfung erfolgt vor Phase 3.

### Design Artifacts

| Artifact | Link | Status |
|---|---|---|
| UX-Spezifikation | [design/ux-spec.md](design/ux-spec.md) | `Draft` |
| Wireframes | [design/ux-spec.md](design/ux-spec.md) (Bildschirmbeschreibungen) | `Draft` |
| Mockups | `[No design link — status: Draft]` | `Draft` |

### User Story Coverage Map

| User Story | Solution Component | Design Reference |
|---|---|---|
| US-001 | Klang-Engine, globaler Effekt | design/ux-spec.md §3.3 |
| US-002 | Umgebungsanalyse plus lokale KI | design/ux-spec.md §3.2 |
| US-003 | Interview und Profilvorschlag | design/ux-spec.md §3.4 |
| US-004 | Profil- und Preset-Speicher | design/ux-spec.md §3.5 |
| US-005 | Raumklang-Engine | design/ux-spec.md §3.6 |
| US-006 | Inhaltserkennung | design/ux-spec.md §3.7 |
| US-007 | Optionale Anbieter-Verbindung | design/ux-spec.md §3.7 |
| US-008 | Hintergrunddienst | design/ux-spec.md §3.2 |
| US-009 | Limiter und Hörschutz | design/ux-spec.md §3.3 |
| US-010 | Onboarding und Geräteerkennung | design/ux-spec.md §3.1 |
| US-011 | Versioniertes Profilformat | design/ux-spec.md §3.8 |

### Considered Alternatives

| Option | Description | Why Rejected |
|---|---|---|
| Nur In-App-Equalizer | Klangänderung ausschließlich in der eigenen Wiedergabe | Deckt die geforderte Systemweite über andere Apps nicht ab |
| AudioPlaybackCapture und Neuausgabe | Audio anderer Apps abgreifen und neu rendern | Quell-Apps müssen es erlauben; DRM/Streaming blockiert; höhere Latenz und Akkuverbrauch |
| Root plus Magisk-Modul | Systemweiter Effekt über ein Root-Modul | Garantie- und Sicherheitsrisiko; als separate Phase 4 vertagt |
| Nur feste Presets ohne Umgebungsmessung | Einfacher zu bauen | Verfehlt die Kernanforderung der Umgebungsanpassung |

### Technical Constraints & Decisions

- **No-Root-Systemweg:** Android erlaubt Klangeffekte am globalen Ausgabemix nicht auf jedem Gerät und nicht für jeden Inhalt. Phase 0 misst auf dem A56, welche Apps und Inhalte tatsächlich erreichbar sind. Das Ergebnis bestimmt die ehrliche Formulierung in der App; es wird keine Systemweite versprochen, die nicht gemessen ist.
- **Geschützte Inhalte:** DRM-geschützte Streams können die globale Klangänderung blockieren. Sonora zeigt das an und bietet Ausweichwege, statt still zu scheitern.
- **NPU:** Die A56-NPU wird über NNAPI angesprochen, sofern der Gerätetreiber sie für LiteRT freigibt. Andernfalls GPU-, dann CPU-Rückfall. Die tatsächliche Beschleunigung wird in Phase 0 gemessen, nicht angenommen.
- **Latenz:** Der Effekt muss unter 10 ms bleiben, damit Lippen und Bild bei Videos synchron wirken. Die Messung erfolgt per Loopback.
- **Akku:** Umgebungsanalyse läuft getaktet (nicht dauerhaft); KI-Inferenz nur bei Bedarf. Ziel ist ≤ 5 % pro Stunde.
- **Samsung-Wechselwirkung:** One UI bringt eigene Klangeffekte (z. B. Adapt Sound, Dolby) mit. Sonora erkennt Konflikte und erklärt sie, statt sie zu verstecken.
- **Datenschutz:** Mikrofonanalyse und KI laufen ausschließlich auf dem Gerät. Netzwerkzugriff nur nach ausdrücklichem Einschalten der Streaming-Verbindung.
- **Updatebarkeit:** Profilformat und Datenbank sind versioniert; jede Änderung erhält eine Migration und einen Upgrade-Test.
- **Open-Source-Nutzung:** Geprüfte Bausteine (z. B. DSP-Konzepte aus RootlessJamesDSP, GPL-3.0) dürfen nur nach Lizenzprüfung übernommen werden. Bei GPL-Code wäre eine Veröffentlichung des Quellcodes Pflicht; für die spätere Play-Store-Version ist eine eigenständige Implementierung vorzuziehen.

---

## 10. Metrik-Überwachung (Metric Monitoring)

| Field | Detail |
|---|---|
| **Dashboard / Tool** | Sonora-Diagnoseseite (lokal) plus Android Battery Historian und Perfetto |
| **DRI** | Sonora Project Lead |
| **Monitoring Cadence** | Täglich in Woche 1, wöchentlich in Monat 1, danach alle zwei Wochen |
| **Primary Metric Alert Threshold** | Klangbewertung < 8/10 oder weniger als 4 Nutzungstage in einer Woche |
| **Guardrail Metric Alert Threshold** | Akku > 5 %/h, ≥ 1 Absturz in 7 Tagen oder Latenz > 10 ms |
| **Rollback Trigger** | Zwei Abstürze in Folge beim Aktivieren oder ein Audioausfall von mehr als 2 Sekunden im Alltag |
| **Escalation Path** | Das Kernteam prüft die Diagnose, deaktiviert den Effekt und informiert Sonora Audio Engineering mit Bericht |

### Post-Launch Review Schedule

| Review | Date | Participants | Owner |
|---|---|---|---|
| 2-Week Check-in | 2026-11-17 | Sonora Core Team | Project Lead |
| 30-Day Review | 2026-12-03 | Sonora Core Team | Project Lead |
| Quarterly Retrospective | 2027-02-03 | Sonora Core Team | Project Lead |

---

## 11. Ereignis- & Datenverfolgung (Event & Data Tracking)

Alle Ereignisse bleiben standardmäßig auf dem Gerät. Es gibt keinen Server und keinen automatischen Versand.

| Event Name | Trigger Condition | Properties | Side | Destination | Maps to Metric | Compliance Flag |
|---|---|---|---|---|---|---|
| `app_opened` | App wird aus dem Hintergrund oder nach Neustart geöffnet | `{ version, android_version }` | Client | Lokale Diagnosedatenbank | Nutzungstage pro Woche | No |
| `profile_applied` | Ein Profil wird manuell oder automatisch aktiviert | `{ profile_id, source, latency_ms }` | Client | Lokale Diagnosedatenbank | Klangbewertung, Nutzungstage | No |
| `interview_completed` | Das KI-Interview wird mit "Speichern" abgeschlossen | `{ duration_s, skipped_questions, model_version }` | Client | Lokale Diagnosedatenbank | Abgeschlossene Profile | No |
| `environment_adapted` | Die Umgebungsanalyse ändert die Zielkurve | `{ noise_class, gain_db, latency_ms }` | Client | Lokale Diagnosedatenbank | Klangbewertung | No |
| `blind_test_rated` | Der Nutzer bewertet einen A/B-Durchlauf | `{ rating_1_10, mode, profile_id }` | Client | Lokale Diagnosedatenbank | Klangbewertung | No |
| `preset_selected` | Ein Preset wird ausgewählt | `{ preset_id }` | Client | Lokale Diagnosedatenbank | Nutzungstage | No |
| `spatial_mode_changed` | Ein Raumklang-Modus wird gewechselt | `{ mode_id, enabled }` | Client | Lokale Diagnosedatenbank | Nutzungstage | No |
| `streaming_connected` | Eine optionale Kontoverbindung wird hergestellt oder getrennt | `{ provider, action }` | Client | Lokale Diagnosedatenbank | Nutzungstage | No |
| `audio_effect_failed` | Der systemweite Effekt kann nicht angewendet werden | `{ error_code, app_package, android_version }` | Client | Lokale Diagnosedatenbank | Abstürze, Klangbewertung | No |
| `crash_reported` | Ein Absturz wird lokal protokolliert | `{ stack_hash, version }` | Client | Lokale Diagnosedatenbank | Abstürze pro 7 Tage | No |
| `profile_exported` | Ein Profil wird exportiert oder importiert | `{ action, format_version, success }` | Client | Lokale Diagnosedatenbank | Abgeschlossene Profile | No |

**Data Team Sign-off:**
- [ ] Pending — Sonora Project Lead
- [ ] Approved — Name und Datum eintragen

---

## 12. FAQ

| # | Question | Answer | Date | Answered By | Status |
|---|---|---|---|---|---|
| 1 | Kann Sonora ohne Root wirklich in jeder App wirken? | In den meisten Apps ja. Bei kopiergeschützten Inhalten kann Android die systemweite Klangänderung blockieren; Sonora zeigt das offen an und bietet Ausweichwege. Die genaue Abdeckung wird in Phase 0 auf dem A56 gemessen. | 2026-10-20 | Sonora Audio Engineering | `Resolved` |
| 2 | Werden Gespräche oder Umgebungsgeräusche aufgenommen? | Nein. Die Umgebung wird nur als Messwert analysiert; Audio wird weder gespeichert noch gesendet. | 2026-10-20 | Sonora Project Lead | `Resolved` |
| 3 | Wird die NPU des A56 wirklich genutzt? | Sonora nutzt die NPU über NNAPI, sofern der Gerätetreiber sie für LiteRT freigibt; sonst GPU oder CPU. Die tatsächliche Nutzung wird in Phase 0 gemessen. | — | Sonora Audio Engineering | `Open` |
| 4 | Dürfen die Namen Dolby Atmos oder DTS verwendet werden? | Nein, das sind geschützte Marken. Sonora verwendet eigene Modusnamen und eigene DSP-Simulationen; die rechtliche Prüfung vor dem Play Store ist offen. | — | Sonora Project Lead | `Open` |
| 5 | Bleiben meine Profile bei einem Update erhalten? | Ja. Das Profilformat ist versioniert und wird migriert; ein Upgrade-Test ist Pflichtbestandteil jedes Releases. | 2026-10-20 | Sonora Audio Engineering | `Resolved` |
| 6 | Wird es eine Root-Version geben? | Später als separate Variante in Phase 4, nicht im Play Store. Phase 1 bleibt vollständig ohne Root. | 2026-10-20 | Sonora Project Lead | `Resolved` |
| 7 | Was passiert, wenn eine App den systemweiten Effekt blockiert? | Sonora erklärt die Einschränkung ruhig und bietet die verfügbaren Ausweichwege an; die Wiedergabe wird nicht unterbrochen. | — | Sonora Audio Engineering | `Open` |

---

## 13. Risiken & Gegenmaßnahmen (Risks & Mitigations)

### Regulatory Risks

| ID | Regulation | Risk Description | Likelihood | Impact | Mitigation | Contingency | Owner | Phase |
|---|---|---|---|---|---|---|---|---|
| RR-001 | DSGVO / GDPR | Mikrofonanalyse könnte als Verarbeitung personenbezogener Daten ohne ausreichende Transparenz bewertet werden | Medium | High | Verarbeitung ausschließlich auf dem Gerät, keine Speicherung, klare Datenschutzerklärung, Mikrofon nur mit Berechtigung | Mikrofonfunktion deaktivieren, lokale Daten löschen, Datenschutzerklärung nachschärfen | Sonora Project Lead | Pre-launch |
| RR-002 | Google Play Data Safety & Developer Program Policy | Falsche oder unvollständige Deklaration von Mikrofon-, Vordergrunddienst- und Netzwerknutzung | Medium | High | Data-Safety-Formular vor Phase 3 prüfen, tatsächliches Verhalten per Mitschnitt belegen | Listing korrigieren, Version zurückziehen, Nutzer informieren | Sonora Project Lead | Pre-launch (Phase 3) |
| RR-003 | EU AI Act — Transparenzpflichten | Der KI-Interviewmodus könnte die erforderliche KI-Kennzeichnung vermissen lassen | Low | Medium | KI-Interaktion im Interview sichtbar kennzeichnen, keine Hochrisiko-Nutzung | Kennzeichnung nachrüsten, Interview optional rein manuell anbieten | Sonora Project Lead | Pre-launch |
| RR-004 | Marken- und Lizenzrecht (Dolby, DTS, Auro-3D) | Modusnamen oder Marketing könnten geschützte Marken verletzen oder Lizenzpflichten auslösen | Medium | High | Eigene Modusnamen, keine Codec-Dekodierung, rechtliche Prüfung vor Phase 3 | Umbenennung, Entfernen betroffener Formulierungen | Sonora Project Lead | Pre-launch |

**Legal/Compliance Sign-off on Regulatory Risks:**
- [ ] Pending — Sonora Project Lead, Prüfung vor Phase 3
- [ ] Approved — Name, Titel und Datum eintragen

### Operational & Technical Risks

| ID | Risk | Likelihood | Impact | Mitigation | Contingency | Owner | Phase |
|---|---|---|---|---|---|---|---|
| R-001 | One UI blockiert den globalen Effekt oder kollidiert mit Samsung-Klangeffekten | High | High | Phase-0-Prototyp auf dem A56, Konflikterkennung, klare Hinweise in der App | App-lokaler Effekt plus dokumentierte Einschränkung; Root-Variante als spätere Option | Sonora Audio Engineering | Pre-launch |
| R-002 | NPU ist über NNAPI nicht oder nur langsam erreichbar | Medium | Medium | Benchmark in Phase 0, Modellgröße begrenzen, GPU-/CPU-Rückfall | Inferenzrate senken, kleineres Modell, CPU-Rückfall | Sonora Audio Engineering | Pre-launch |
| R-003 | Akkuverbrauch überschreitet 5 %/h | Medium | High | Getaktete Analyse, effiziente Filter, Messung über eine Stunde | Analyseintervall erhöhen, Funktionen reduzieren | Sonora Audio Engineering | Post-launch |
| R-004 | Latenz über 10 ms und Lippen unsynchron | Medium | Medium | Native DSP, kurze Puffer, Loopback-Messung | Video-Modus mit Bypass, Latenz in der App anzeigen | Sonora Audio Engineering | Pre-launch |
| R-005 | Clipping oder Gehörschaden durch zu starke Anhebung | Low | High | Limiter immer aktiv, Boost begrenzen, Lautstärkeerinnerung | Boost deaktivieren, Nutzer informieren | Sonora Project Lead | Pre-launch |
| R-006 | GPL-Code aus Open-Source-Bausteinen kontaminiert die spätere Store-Version | Medium | High | Lizenzprüfung vor Übernahme, Konzepte statt Code übernehmen | Eigenständige Neuimplementierung | Sonora Audio Engineering | Pre-launch |
| R-007 | Funktionsumfang wächst unkontrolliert und die App wird unbedienbar | Medium | Medium | Feste Bereichsstruktur, ein Hauptschalter, Design-Review gegen AI-Slop | Funktionen auf Phase 2 verschieben | Sonora Project Lead | Post-launch |

---

## 14. Abhängigkeiten (Dependencies)

### Upstream Dependencies

| ID | Dependency | Team / Vendor | What Is Needed | Expected Date | Confirmed | Risk if Delayed | Escalation |
|---|---|---|---|---|---|---|---|
| DEP-001 | Android AudioEffect am globalen Ausgabemix | AOSP / Google | Stabile Anwendung systemweiter Effekte ohne Root | 2026-10-27 | Yes (API vorhanden; Gerätetest offen) | Kernfunktion eingeschränkt | Phase-0-Prototyp, Ausweichweg App-lokal |
| DEP-002 | MediaSession / NotificationListener | AOSP / Google | Erkennung laufender Inhalte | 2026-10-27 | Yes | Automatische Profilwahl entfällt | Manuelle Profilwahl bleibt |
| DEP-003 | LiteRT / TensorFlow Lite plus NNAPI | Google | Lokale KI-Inferenz, mögliche NPU-Nutzung | 2026-10-27 | Yes (Bibliothek); NPU-Zugriff unbestätigt | KI läuft auf GPU/CPU | Modell verkleinern |
| DEP-004 | Spotify Web API | Spotify | Optionale Kontoverbindung und Metadaten | Phase 2/3 | No | Optionale Funktion verzögert | Phase 2/3 verschieben |
| DEP-005 | YouTube Data API | Google | Optionale Kontoverbindung und Metadaten | Phase 2/3 | No | Optionale Funktion verzögert | Phase 2/3 verschieben |
| DEP-006 | RootlessJamesDSP (Referenz, GPL-3.0) | Open Source | DSP-Konzepte und Verhalten studieren | 2026-10-24 | No (Lizenzprüfung offen) | Kein Einfluss auf Phase 1 | Eigenständige Implementierung |
| DEP-007 | Play-Store-Richtlinien und Data-Safety-Formular | Google | Veröffentlichung der No-Root-Version | Phase 3 | Yes (Regeln vorhanden; Prüfung offen) | Veröffentlichung verzögert | Phase 3 verschieben |

### Downstream Dependencies

| ID | Dependent Initiative | Team | What They Need | Expected Date |
|---|---|---|---|---|
| DEP-D01 | Familie-&-Freunde-Version | Project Lead | Stabile No-Root-Basis und Installationsanleitung | 2026-12-03 |
| DEP-D02 | Play-Store-Version (ohne Root) | Project Lead | Datenschutz-, Marken- und Stabilitätsnachweis | 2027-01-15 |
| DEP-D03 | Root-Variante (separat) | Project Lead | Abgeschlossene Phase 1 und eigene Sicherheitsprüfung | 2027-03-01 |

---

## 15. Veröffentlichungsplan (Launch Plan)

### Rollout Strategy

`Staged Rollout` — Phase 0: technischer Prototyp nur auf dem A56. Phase 1: persönliche tägliche Nutzung per Sideload. Phase 2: Familie und Freunde, weiterhin ohne Root. Phase 3: Play-Store-Version ohne Root. Phase 4: separate Root-Variante außerhalb des Play Stores.

Begründung: Die systemweite Wirkung ohne Root muss zuerst auf dem Zielgerät gemessen werden. Eine breite Veröffentlichung vor diesem Nachweis würde Erwartungen wecken, die technisch nicht garantiert sind.

### Go / No-Go Criteria

| Stage | Go Condition | No-Go Condition | Decision Maker |
|---|---|---|---|
| Phase 0 — Prototyp | Globaler Effekt wirkt auf dem A56 in mindestens einer fremden App; Latenz ≤ 10 ms | Effekt wirkt auf dem A56 in keiner fremden App | Sonora Audio Engineering |
| Phase 1 — Persönlich | Klang ≥ 8/10, 0 Abstürze in 7 Tagen, Akku ≤ 5 %/h | Abstürze oder Akku > 5 %/h ohne erkennbare Gegenmaßnahme | Sonora Project Lead |
| Phase 2 — Familie & Freunde | Stabile Installation, verständliches Onboarding, keine offenen Datenschutzfragen | Häufige Supportfälle oder unklare Berechtigungen | Sonora Project Lead |
| Phase 3 — Play Store | Datenschutz-, Marken- und Stabilitätsprüfung abgeschlossen | Rechtliche oder policy-bedingte Blocker offen | Sonora Project Lead |

### Communications Plan

| Audience | Channel | Message Summary | Timing | Owner |
|---|---|---|---|---|
| Projektinhaber | Direkt im Chat | Fortschritt, Messwerte, Blocker | Täglich in Woche 1 | Sonora Audio Engineering |
| Familie und Freunde | Persönliche Nachricht | Was die App kann und was nicht, Installationshinweis | Vor Phase 2 | Sonora Project Lead |
| Play-Store-Nutzer | Store-Listing und Datenschutzerklärung | Funktionen, Grenzen, Datenschutz | Vor Phase 3 | Sonora Project Lead |

### Rollback Plan

| Field | Detail |
|---|---|
| **Rollback Trigger** | Zwei Abstürze in Folge beim Aktivieren oder ein Audioausfall von mehr als 2 Sekunden im Alltag |
| **Decision Maker** | Sonora Project Lead |
| **Execution Time** | Unter 5 Minuten |
| **Rollback Steps** | Effekt in der App deaktivieren; bei Bedarf vorherige APK installieren; letztes funktionierendes Profil wiederherstellen; Diagnosebericht sichern |

**Launch DRI:** Sonora Project Lead

**Compliance Sign-off:**
- [ ] Pending — Sonora Project Lead, vor Phase 3
- [ ] Approved — Name, Titel und Datum eintragen

---

## References / Sources

| Source | Type | Retrieved | Used In |
|---|---|---|---|
| Nutzerinterview am 2026-10-20 (Intake, Zielwerte, Scope-Entscheidungen) | paste | 2026-10-20 | §3, §4, §5, §6, §7, §15 |
| Projektdatei `docs/design/ux-spec.md` (UX-Spezifikation, Entwurf) | file | 2026-10-20 | §9 |

> Es wurden keine externen Benchmarks oder nicht bestätigten Zahlen verwendet. Alle Zielwerte sind vom Nutzer am 2026-10-20 bestätigt oder als `[TBD]` markiert.

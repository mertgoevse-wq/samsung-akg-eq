# Sonora 0.4 — Button / Action-Matrix

**Stand:** 2026-11-02  
**Zweck:** Jedes interaktive UI-Element wird auf seine vollständige Ereigniskette abgebildet:  
**UI-Element → Event → ViewModel → Service/Repository → Zustandsänderung → sichtbares Ergebnis → Test.**

Diese Matrix ist die verbindliche Referenz für die Anforderung „jeder Button hat eine reale Funktion (keine Attrappen)“.

---

## Legende

| Spalte | Bedeutung |
|--------|-----------|
| **UI-Element** | Button, Schalter, Regler, Chip, Zeile in der UI |
| **Event** | Benutzer-Interaktion (Klick, Schalter, Berührung) |
| **ViewModel** | Aufgerufene `SonoraViewModel`-Methode (oder lokaler State) |
| **Service / Repository** | Unterliegende Schicht (Engine, Store, Engine, …) |
| **Zustandsänderung** | `uiState`-Kopie / persistierte Änderung |
| **Sichtbares Ergebnis** | Was der Nutzer auf dem Bildschirm sieht / hört |
| **Test** | Abdeckung durch Unit-Test (Klasse) |

---

## 1. OnboardingScreen

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| „Weiter“ (Schritt 0 → 1) | Klick | lokaler `step`-State | — | `step = 1` | Hörer-/Audioausgang-Karte | — |
| „Weiter“ (Schritt 1 → 2) | Klick | lokaler `step`-State | — | `step = 2` | Berechtigungen-Karte | — |
| „Zurück“ (Schritt 1 → 0 / 2 → 1) | Klick | lokaler `step`-State | — | `step` verringert | Vorheriger Onboarding-Schritt | — |
| „Freigeben“ (Mikrofon) | Klick | `micLauncher.launch(RECORD_AUDIO)` | Android Permission API | `micGranted = true` | Karte zeigt „Aktiv“ | — |
| „Freigeben“ (Benachrichtigungen) | Klick | `notificationLauncher.launch(POST_NOTIFICATIONS)` | Android Permission API | `notificationGranted = true` | Karte zeigt „Aktiv“ | — |
| „Sonora starten“ | Klick | `vm.completeOnboarding()` | `ProfileStore.setOnboarded(true)` | `onboardingDone = true` | HomeScreen wird angezeigt | `ProfileStore`-Test |
| Hörerkarte (VerifiedModel / Generic / BuiltIn / None) | — | liest `vm.uiState.audioRouting.headphoneModel` | `AudioRoutingManager` | keine Änderung | Status-Text + Modellname/Fehlerhinweis | `AudioRoutingManagerTest` |

---

## 2. HomeScreen (Jetzt)

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| Hauptschalter (Switch) | Ein/Aus | `vm.setMasterEnabled(enabled)` | `settingsPreferences` + `SonoraEngine.setEnabled()` | `masterEnabled`, Engine ein/aus | „Klangoptimierung aktiv“ / „Sonora pausiert“; LED-Punkt | `SonoraEngine`, ViewModel-Tests |
| „Prüfen“ (Audio-Routing) | Klick | `vm.refreshAudioRouting()` | `AudioRoutingManager.refresh()` | `audioRouting` snapshot neu | Aktualisierte Routing-Zeilen | `AudioRoutingManagerTest` |
| „Session 0 erneut anfordern“ / „Initialisieren“ | Klick | `vm.retryEngine()` | `SonoraEngine.initialize()` | `engineStatus` | Status-Zeile aktualisiert, ggf. Warnung | `SonoraEngine` |
| Visualizer-Switch | Ein/Aus | `vm.setPlaybackVisualizationEnabled(enabled)` | `android.media.audiofx.Visualizer` | `playbackVisualizationEnabled`, Visualizer an/aus | Partikelfeld aktiviert / deaktiviert | — |
| „Testen“ / „Stopp“ (Sound Lab) | Klick | `vm.toggleSoundLab()` | `SoundLabEngine.start()/stop()` | `soundLabActive`, interner PCM-Signalweg | Partikelfeld reagiert auf Testsignal; „Testsignal (Sound Lab)“-Anzeige | `SoundLabEngine` |
| Signal-Chip (Synthesizer / Rauschen / Impuls) | Klick | `vm.setSoundLabSignal(sig)` | `SoundLabEngine.currentSignal` | `soundLabSignal` | Gewähltes Signal farblich markiert | — |
| „Stärke“-Slider (Partikelfeld/Flanger) | Berührung | `vm.onParticleFieldTouch(x, y, touching)` | `SoundLabEngine.flanger.setParametersFromXy()` | `flangerRateHz/depth/feedback` | TOUCH-DSP-HUD mit LFO/Tiefe/Feedback | `FlangerDspTest` |
| Auto-Anpassung-Switch | Ein/Aus | `vm.setAutoAdjust(enabled)` | `EnvironmentAnalyzer` | `autoAdjust`, `environment` | Umgebungspegel-Text, EQ-Anpassung | `EnvironmentAnalyzer` |
| „KI-Berater“ (Action Hub) | Klick | lokaler `showAiAdvisor = true` | — | Dialog geöffnet | AiAdvisorDialog | — |
| „Interview“ (Action Hub) | Klick | `vm.startInterview()` | — | `interviewActive = true` | InterviewScreen | `InterviewCatalog`, `InterviewEvaluator` |
| „Hörtest“ (Action Hub) | Klick | `vm.startBlindTest()` | `BlindTest` | `blindTestActive = true`, Runde 0 | BlindTestScreen | `BlindTestTest` |
| „Übernehmen“ (KI-Vorschlag-Banner) | Klick | `vm.applyAiSuggestion()` | `ProfileStore.save()` | Neues Profil aktiv, `eqCurve` übernommen | Profil angewendet, Meldung | `LocalRuleAiProvider`, `AiValidator` |
| „Verwerfen“ (KI-Vorschlag-Banner) | Klick | `vm.dismissAiSuggestion()` | — | `aiSuggestion = null` | Banner ausgeblendet | — |
| „Verstanden“ (Hörschutz) | Klick | `vm.acknowledgeVolumeReminder()` | `safetyPreferences` | Erinnerung quittiert | Karte ausgeblendet | `VolumeGuardTest` |
| „Erinnerung aus“ (Hörschutz) | Klick | `vm.setVolumeReminderEnabled(false)` | `safetyPreferences` | `volumeReminderEnabled = false` | Keine weitere Erinnerung | `VolumeGuardTest` |

---

## 3. AiAdvisorDialog

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| Genre-/Bass-/Sprache-Chip | Klick | lokaler State (`genre`, `bass`, `speech`) | — | Auswahl gespeichert | Chip als ausgewählt markiert | — |
| „Kurve berechnen“ | Klick | `vm.requestAiSuggestion(HearingPreference(...))` | `LocalRuleAiProvider.generateSuggestion()` | `aiSuggestion` gesetzt | Vorschlagskarte mit Preamp/Headroom | `LocalRuleAiProvider`, `AiValidator` |
| „Schließen“ | Klick | `onDismiss` | — | Dialog geschlossen | Dialog ausgeblendet | — |

---

## 4. EqualizerScreen (Klang)

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| Band-Count-Chip (10 / 31 / 64+) | Klick | `vm.setBandCount(count)` | `EqCurve.withBandCount()` | `bandCount`, Kurve interpoliert | Regler-Anzahl ändert sich, Kurve bleibt erhalten | `BandMappingTest`, `EqModelTest` |
| Preamp-Slider | Bewegung | `vm.setPreamp(db)` | — | `preampDb`, `applyEffective()` | dB-Wert-Anzeige, Limiter-Warnung bei Clipping | `LimiterTest` |
| Band-Slider (je Band) | Bewegung | `vm.setBand(index, db)` | `SonoraEngine.applyCurve()` / `Equalizer` | `eqCurve.levelsDb`, `dirty = true`, DSP angewandt | dB-Wert pro Band, EQ hörbar verändert | `EqModelTest`, `SonoraEngine` |
| „Gedrückt halten für Originalklang“ (A/B) | Press/Release | `vm.beginBypass()` / `vm.endBypass()` | `SonoraEngine.setEnabled(false/true)` | `bypassActive` | Oberfläche wechselt zu Originalklang solange gedrückt | `SonoraEngine` |
| „Zurücksetzen“ | Klick | `vm.resetLevels()` | — | Alle Level auf 0, `dirty = true` | Alle Regler auf 0 dB | `EqModelTest` |
| „Profil speichern“ | Klick | `showSave = true` (SaveProfileDialog) | — | Dialog geöffnet | „Profil speichern“-Dialog | — |
| „Speichern“ (Dialog) | Klick | `vm.saveCurrentAs(name)` | `ProfileStore.save()` | Neues Profil in Liste, aktiv gesetzt | Profil in Profil-Liste, Erfolgsmeldung | `ProfileStoreTest` |
| „Abbrechen“ (Dialog) | Klick | lokaler State | — | Dialog geschlossen | — | — |

---

## 5. ProfilesScreen

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| „Import“ | Klick | `showImport = true` | — | Dialog geöffnet | JSON-Import-Dialog | — |
| „Sichern“ | Klick | `showSave = true` | — | Dialog geöffnet | „Profil speichern“-Dialog | — |
| Chip „Eigene Profile“ / „14 Studio-Presets“ | Klick | lokaler `selectedPresetTab` | — | Tab gewechselt | Eigene Liste vs. Preset-Bibliothek | — |
| „Aktivieren“ / „Aktiv“ (eigenes Profil) | Klick | `vm.selectProfile(id)` | `applyPreset()` | `activeProfileId`, `eqCurve` | Profil als aktiv markiert, EQ angewendet | `ProfileStoreTest` |
| „Duplizieren“ (eigenes Profil) | Klick | `vm.duplicateProfile(id)` | `ProfileStore.duplicateProfile()` | Kopie in Liste, aktiv gesetzt | Neues Profil-Eintrag, Meldung | `ProfileStoreTest` |
| „Umbenennen“ (eigenes Profil) | Klick | `showRenameDialog = id` | — | Rename-Dialog geöffnet | Texteingabefeld mit aktuellem Namen | — |
| „Speichern“ (Rename-Dialog) | Klick | `vm.renameProfile(id, newName)` | `ProfileStore.renameProfile()` | Name geändert | Aktualisierter Name in Liste | `ProfileStoreTest` |
| „Abbrechen“ (Rename-Dialog) | Klick | lokaler State | — | Dialog geschlossen | — | — |
| „Exportieren“ (eigenes Profil) | Klick | `vm.exportProfile(id)` → `clipboard.setText()` | `ProfileStore.exportProfile()` | JSON in Zwischenablage | Meldung „Profil exportiert“ | `ProfileStoreTest` |
| „Löschen“ (eigenes Profil) | Klick | `vm.deleteProfile(id)` | `ProfileStore` | Profil entfernt, Fallback-Profil aktiv | Liste aktualisiert, Meldung | `ProfileStoreTest` |
| „Aktivieren“ / „Aktiviert“ (Preset) | Klick | `vm.applyPreset(profile)` | `applyEffective()` | Preset als aktiv, EQ angewendet | Preset aktiv, Meldung | `PresetCatalogTest` |
| „Kopie bearbeiten“ (Preset) | Klick | `vm.duplicateProfile(matchingProfile.id)` | `ProfileStore.duplicateProfile()` | Preset als eigenes Profil dupliziert | Eintrag unter eigene Profile | `ProfileStoreTest` |
| „Importieren“ (Import-Dialog) | Klick | `vm.importProfile(importText)` | `ProfileStore.importProfile()` | Profile importiert oder Fehler | Erfolgs-/Fehlermeldung, Liste aktualisiert | `ProfileStoreTest` |
| „Abbrechen“ (Import-Dialog) | Klick | lokaler State | — | Dialog geschlossen | — | — |

---

## 6. SettingsScreen (Mehr & Diagnose)

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| „Freigeben“ (Mikrofon/Benachrichtigung) | Klick | Permission-Launcher | Android Permission API | Berechtigung erteilt/verweigert | Status-Zeile „Erlaubt“ / „Nicht freigegeben“ | — |
| „Starten“ (Hintergrunddienst) | Klick | `vm.startService()` | `ContextCompat.startForegroundService()` | Dienst gestartet | Meldung „Hintergrunddienst gestartet“ | `SonoraService` |
| „Beenden“ (Hintergrunddienst) | Klick | `vm.stopService()` | `stopService()` | `engineStatus = Inactive` | Meldung „Hintergrunddienst beendet“ | `SonoraService` |
| „Bericht kopieren“ | Klick | `vm.diagnosticReport()` → Zwischenablage | `DiagnosticsStore`, `EventLog` | Diagnose-Text in Zwischenablage | Meldung nach Kopieren (optional) | `DiagnosticsStoreTest` |
| „Leeren“ (Diagnose) | Klick | `vm.clearDiagnosticEvents()` | `EventLog.clear()`, `DiagnosticsStore.save([])` | Ereignisse gelöscht | Leeres Diagnoseprotokoll | `DiagnosticsStoreTest` |

---

## 7. SpatialScreen (Raumklang)

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| Modus-Karte („Aus“ / Kino-Weit / … / Breit & Warm) | Klick | `vm.setSpatialMode(mode)` | `SonoraEngine.applySpatial()` / `Virtualizer`/`EnvironmentalReverb` | `spatialMode`, `spatialAvailable` | Karte als ausgewählt (RadioButton + Schieferfläche), DSP-Stufen-Bericht | `SpatialApplyTest`, `SpatialModeCatalog` |
| „Stärke“-Slider | Bewegung | `vm.setSpatialIntensity(value)` | `SpatialModeCatalog.params(mode, intensity)` | `spatialIntensity`, `applySpatial()` | Prozentwert 0–100 %, DSP-Bericht aktualisiert | `SpatialModeCatalog` |
| DSP-Stufen-Zeilen | — | `SpatialModeCatalog.evaluateDspStages()` | — | keine Änderung (Anzeige) | Zustands-Punkte (grün/bernstein/grau/kupfer) pro Stufe | `SpatialModeCatalog` |

---

## 8. BlindTestScreen (Hörtest)

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| „Abbrechen“ | Klick | `vm.cancelBlindTest()` | `BlindTest` (reset) | `blindTestActive = false` | Zurück zu HomeScreen | `BlindTestTest` |
| „Vergleich starten“ / „Beide Abschnitte gehört“ | Klick | `vm.presentBlindTestRound()` | `AudioManager.isMusicActive`, `SonoraEngine` | `blindTestPresented = true`, Bypass umschaltet nach 3 s | Segment A/B abgespielt, Bewertung freigegeben | `BlindTestTest` |
| Slider Abschnitt A / B | Bewegung | `vm.updateBlindTestRatingA/B(rating)` | — | `blindTestRatingA/B` | Bewertung 1–10 angezeigt | `BlindTestTest` |
| „Bewertung sichern“ | Klick | `vm.rateBlindTest()` | `BlindTest.recordPair()` | Runde abgeschlossen, `blindTestRound` erhöht | Nächste Runde oder Abschlussmeldung | `BlindTestTest` |
| „Im Diagnosebericht sichern“ | Klick | `vm.finishBlindTest()` | `DiagnosticsStore` | `blindTestActive = false`, gespeichert | Meldung „Hörtest im lokalen Diagnosebericht gespeichert“ | `BlindTestTest` |

---

## 9. InterviewScreen (Persönliches Klangprofil)

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| „Abbrechen“ | Klick | `vm.cancelInterview()` | — | `interviewActive = false` | Zurück zu HomeScreen | `InterviewTest` |
| Antwort-Option (RadioButton-Zeile) | Klick | `vm.answerInterview(questionId, optionId)` | `InterviewCatalog`, `InterviewEvaluator` | `interviewAnswers`, nächster Frage-Index | Nächste Frage oder Vorschau | `InterviewTest`, `InterviewEvaluatorTest` |
| „Kann ich nicht sagen · überspringen“ | Klick | `vm.skipInterviewQuestion(questionId)` | — | Frage übersprungen | Nächste Frage | `InterviewTest` |
| „Profil anhören“ | Klick | `vm.previewInterview()` | `InterviewEvaluator.evaluate()` | `eqCurve` auf Vorschau-Kurve, `applyEffective()` | EQ wird hörbar angewendet | `InterviewEvaluatorTest`, `EqModelTest` |
| „Profil speichern und aktivieren“ | Klick | `vm.finishInterview()` | `ProfileStore.save()` (via `uiState.profiles`) | Neues Profil gespeichert und aktiv, `interviewActive = false` | Profil aktiv, Erfolgsmeldung | `ProfileStoreTest`, `InterviewTest` |

---

## 10. Globale / Querschnittliche Elemente

| UI-Element | Event | ViewModel | Service/Repository | Zustandsänderung | Sichtbares Ergebnis | Test |
|------------|-------|-----------|--------------------|------------------|---------------------|------|
| Untere Navigation (Jetzt / Klang / Raum / Profile / Mehr) | Tab-Klick | lokaler `selectedTab` | — | Tab gewechselt | Entsprechender Screen | `Screens.kt` / Navigation |
| Snackbar (nachricht) | — | `vm.clearMessage()` nach Anzeige | `SnackbarHostState` | `message = null` | Kurze Meldung am unteren Rand | — |

---

## Dummy-Button-Audit (Ergebnis)

Alle interaktiven Elemente in den Screens wurden geprüft. Es wurden **keine Dummy-Buttons** gefunden – jeder Button, jeder Schalter, jeder Regler und jeder Chip ist mit einer echten `SonoraViewModel`-Methode (oder direktem lokalen State) verbunden, die eine nachvollziehbare Zustandsänderung und ein sichtbares Ergebnis auslöst. Dies deckt sich mit dem Baseline-Audit (`docs/audit/SONORA_0_4_WINDOWS_BASELINE.md`, Sektion „Screens (all functional, no dummy buttons)“).

---

## Abdeckung durch Tests (Status)

| Bereich | Testklasse | Abgedeckte Aktionen |
|---------|------------|---------------------|
| EQ / Bands | `EqModelTest`, `BandMappingTest` | Band-Count, Kurve, Levels |
| Limiter / Preamp | `LimiterTest` | Preamp, Clipping |
| Raumklang | `SpatialApplyTest`, `SpatialModeCatalog` (implizit) | Modus, Intensität, DSP-Stufen |
| Blindtest | `BlindTestTest` | Runden, Bewertung, Abschluss |
| Interview | `InterviewTest`, `InterviewEvaluatorTest` | Antworten, Überspringen, Auswertung |
| Profile / Presets | `ProfileStoreTest`, `PresetCatalogTest` | Speichern, Löschen, Duplizieren, Import/Export, Preset-Anwenden |
| DSP | `FlangerDspTest`, `FftReducerTest`, `DspSignalTest` | Flanger-XY, FFT-Reduktion, DSP-Signal |
| Audio-Routing | `AudioRoutingManagerTest` | Routing-Erneuerung |
| Hardware | `HardwareCapabilityRepositoryTest`, `VolumeGuardTest` | Backend, Hörschutz |
| Engine | `EngineBridgeTest`, `EngineControlTest` | Engine-Initialisierung, Kurve-Anwendung |
| Diagnose | `DiagnosticsStoreTest` | Ereignisse, Bericht, Leeren |
| Media Rule | `MediaRuleTest` | Inhaltserkennung |

**Noch nicht abgedeckt (Lücken):** `SoundLabEngine` hat keine eigene Testdatei; `NeuralAudioViewModel` / `NeuralAudioScreen` haben keine UI-Tests; CI läuft derzeit keine flavor-spezifischen Tests und keinen Lint-Schritt (siehe `docs/audit/SONORA_0_4_WINDOWS_BASELINE.md`).

---

## Zugänglichkeits-Hinweise zur Matrix

Alle oben genannten Buttons/Schalter erfüllen:
- Mindest-Trefferfläche 48 dp (`heightIn(min = 48.dp)`),
- TalkBack-`contentDescription` bzw. `role` (Switch/RadioButton),
- Farbe ist nie das einzige Statussignal (Text + Form + Semantik),
- Dynamische Schriftgröße (sp-Einheiten, keine festen px),
- Keine permanenten dekorativen Animationen (120–180 ms direkte Rückmeldung nur bei Aktion).

# AmazonMC – Fabric 1.21.11

Dein kleines Versandhaus direkt in Minecraft: Artikel ansehen, mit Spielgeld
bestellen und nach 30 Sekunden im Paketfach abholen.

**Eine Fabric-Mod für Minecraft Java 1.21.11, kein Paper-/Spigot-Plugin.**
Keine echten Einkäufe, keine Anmeldung und keine Verbindung zu Amazon.
Kein offizielles Produkt von Amazon, Mojang oder Microsoft.

## Installation

1. Sichere deine Welt und schließe Minecraft.
2. Installiere **Fabric Loader 0.19.3 oder neuer** für **Minecraft 1.21.11**:
   [Fabric-Installer](https://fabricmc.net/use/installer/).
3. Lege `amazonmc-fabric-1.21.11-1.0.0.jar` in den `mods`-Ordner deiner
   Minecraft-Instanz. Unter Windows ist das beim normalen Launcher meistens
   `%appdata%\.minecraft\mods`. Bei Modrinth/Prism/CurseForge den Ordner der
   tatsächlich verwendeten Instanz öffnen.
4. Lege auch **Fabric API 0.141.6+1.21.11** in denselben Ordner:
   [Fabric API herunterladen](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.141.6+1.21.11/fabric-api-0.141.6+1.21.11.jar).
5. Starte das Fabric-Profil, öffne eine Welt und schreibe **`/amazon`** in den Chat.

Für einen Server kommen beide Mods in dessen `mods`-Ordner. Die Spiellogik läuft
auf dem Server und benutzt nur Vanilla-Items und das normale Truhen-Menü.
Ein eigenes Ressourcenpaket ist nicht nötig. Für Einzelspieler installierst du
die Mod in deinem Client; auf fremden Servern funktioniert sie nur, wenn der
Serverbetreiber sie ebenfalls installiert hat. Java 21 wird benötigt.

## So funktioniert es

- **250 Start-Coins** je Spieler und Welt.
- **28 Artikel** in vier Kategorien: Bauen, Rohstoffe, Essen und Ausrüstung.
- Artikel mit der **linken Maustaste** anklicken, 1–16 Bündel auswählen und
  mit dem grünen Knopf bestätigen. Vor der Bestätigung wird nichts abgebucht.
- **Lieferung nach 30 Sekunden** ins persönliche Paketfach. Eine Chatnachricht
  meldet die Ankunft. Offline-Zeit zählt mit.
- Paketfach über das Fass-Symbol oder `/amazon pakete` öffnen. Ein angekommenes
  Paket anklicken: Der Inhalt wird ins Inventar gelegt. Es gibt keine fliegende
  Lieferdrohne und keinen neuen Paketblock; die Pakete liegen im Menü.
- Ist dein Inventar zu voll, bleibt das komplette Paket im Fach. Maximal 56
  offene Bestellungen; weitere Pakete erscheinen auf Seite 2.
- **100 Bonus-Coins alle 24 Stunden**, zuerst sofort abholbar.
- **Items verkaufen:** einen passenden Stapel in der Haupthand halten, im Menü
  auf den Smaragd klicken und den angezeigten Verkauf bestätigen.
- Solange das Shop-Menü offen ist, sind die Inventarplätze gesperrt. Zum
  Umsortieren oder Wechseln des gehaltenen Items das Menü schließen.

Die Coins gehören nur zu dieser Mod und sind keine kaufbaren Minecraft-Minecoins.

## Befehle

| Befehl | Funktion |
|---|---|
| `/amazon` oder `/amz` | Shop öffnen |
| `/amazon shop` | Shop öffnen |
| `/amazon pakete` | Paketfach öffnen |
| `/amazon geld` | Kontostand anzeigen |
| `/amazon bonus` | Bonus abholen |
| `/amazon verkaufen` | Gesamten Stapel aus der Haupthand sofort verkaufen |
| `/amazon verkaufen 8` | Acht Items aus der Haupthand sofort verkaufen |
| `/amazon hilfe` | Kurzhilfe |

Die Befehle benötigen keine OP-Rechte und keine aktivierten Cheats. **Der
Verkaufsbefehl verkauft sofort**; das Menü bietet eine Bestätigung. Angepasste,
benannte oder verzauberte Items werden nicht angenommen. Manche Artikel haben
keinen Ankaufspreis; die Informationen stehen jeweils im Shop.

## Speicher und Sicherungen

Kontostand, Bonuszeit und Bestellungen werden pro UUID in
`<Weltordner>/amazonmc/accounts/<UUID>.json` gespeichert. Der Shop speichert jede
Änderung sofort über eine temporäre Datei. Bei ungültigen Kontodaten bleibt das
Konto gesperrt, statt das Guthaben zu überschreiben. Nach Inventartransaktionen
stößt die Mod außerdem die Speicherung des Spielers an.

Bitte den Server normal stoppen und die **gesamte Welt einschließlich
Spielerdaten und amazonmc-Ordner gemeinsam sichern/wiederherstellen**. Kontodatei
und Minecraft-Spielerdatei sind keine gemeinsame Datenbanktransaktion. Bei einem
harten Absturz genau während eines Verkaufs oder einer Abholung ist deshalb
eine Abweichung möglich. Kontodateien nicht während des laufenden Spiels ändern.

Die Mod nutzt keine eigenen externen Netzwerkdienste und enthält keine Telemetrie.
Preise und Sortiment sind in `Catalog.java`, Startguthaben und Zeiten in
`Ledger.java` festgelegt. Änderungen erfordern einen neuen Build.

## Selbst bauen

Voraussetzungen: JDK 21, Internet für den ersten Build.

```powershell
.\gradlew.bat build
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew build
```

Die spielbare JAR liegt danach unter `build/libs/`. Die Datei mit
`-sources.jar` ist nur für Entwickler. Gradle 9.2.1 und Loom 1.14.10 sind
fest vorgegeben; der Gradle-Download wird per SHA-256 geprüft.

Tests erneut ausführen: `gradlew.bat test --rerun-tasks`.

Falls unter Windows ein Java-Fehler `Unable to establish loopback connection`
auftritt, kann ein normaler temporärer Ordner im entpackten Projekt helfen:

```powershell
New-Item -ItemType Directory -Force .\tmp | Out-Null
$tmp = (Resolve-Path .\tmp).Path
$env:TEMP = $tmp
$env:TMP = $tmp
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$tmp -Djava.io.tmpdir=$tmp"
.\gradlew.bat --no-daemon build
```

Einen möglichst kurzen Projektpfad ohne Leerzeichen verwenden. Diese Einstellungen
gelten nur für dieses Terminal und verändern keine globalen Java-Einstellungen.

## Prüfen vor dem Einsatz in einer wichtigen Welt

Stand 27.08.2026: Build erfolgreich mit JDK 21, Minecraft 1.21.11, Fabric Loader
0.19.3 und Fabric API 0.141.6+1.21.11. **21 automatisierte Tests bestanden**:
13 für Konten, Bestellungen, Bonus, Neustarts und Speicherfehler; 8 für
Inventarübergaben und Katalogkonsistenz. Access Widener und remappte JAR geprüft.
Beim Entwicklungs-Serverstart hat Fabric AmazonMC erfolgreich initialisiert.
Der Start endete anschließend erwartungsgemäß an der noch nicht akzeptierten
Minecraft-EULA. Keine EULA wurde automatisch akzeptiert; keine Welt wurde
gestartet. **Noch kein vollständiger Spieltest oder Modpack-Kompatibilitätstest.**

Die automatischen Tests ersetzen keinen vollständigen Spieltest mit deinem
Modpack. Zuerst in einer Testwelt prüfen:

1. `/amazon` öffnen, Brot bestellen, Abbuchung kontrollieren.
2. Vor Ablauf von 30 Sekunden auf das Paket klicken: keine Ausgabe.
3. Nach Ablauf abholen: genau die bestellte Menge, kein zweites Abholen.
4. Mit vollem Inventar abholen: Paket bleibt erhalten.
5. Menü-Icons mit Shift, Zahlen, Q und Ziehen testen: keine Icons entnehmbar.
6. Bonus zweimal versuchen: nur einmal 100 Coins.
7. Verkauf über Menü prüfen, danach Ausloggen/Neustart: Guthaben und Pakete erhalten.

## Technische Quellen

- [Fabric zu Minecraft 1.21.11](https://www.fabricmc.net/2025/12/05/12111.html)
- [Fabric: Befehle für 1.21.11](https://docs.fabricmc.net/1.21.11/develop/commands/basics)
- [Offizielles Fabric-Beispielprojekt](https://github.com/FabricMC/fabric-example-mod/tree/1.21.11)

Lizenz des Mod-Codes: MIT, siehe `LICENSE`.

# AmazonMC – Paper 1.21.11

Ein Versandhaus direkt in Minecraft: Artikel ansehen, mit Spielgeld bestellen
und nach 30 Sekunden im persönlichen Paketfach abholen. Deutsche Menüs.

**Server-Plugin für Paper 1.21.11 und Java 21.** Kein Fabric, keine Client-Mods,
kein Ressourcenpaket und kein Vault nötig. Keine echten Einkäufe, kein Amazon-Konto
und keine Verbindung zu Amazon. Kein offizielles Produkt von Amazon, Mojang oder Microsoft.

Die ursprüngliche Fabric-Version bleibt im Branch
[`fabric-1.21.11`](https://github.com/bens-c/amazonmc/tree/fabric-1.21.11) erhalten.

## Installation

1. Server stoppen und Welt, Spielerdaten und Plugins sichern.
2. Einen **Paper-Server für Minecraft Java 1.21.11** mit **Java 21** verwenden.
   Siehe [Paper-Installation](https://docs.papermc.io/paper/getting-started/).
3. `amazonmc-paper-1.21.11-2.0.0.jar` in den `plugins`-Ordner des Servers legen.
   Nicht die `-sources.jar` und keine Fabric-JAR verwenden.
4. Server starten, beitreten und **`/amazon`** eingeben.

Spieler benötigen nur einen passenden Minecraft-Java-Client. Das Plugin gehört
nicht in den lokalen `mods`-Ordner und läuft nicht in einer normalen Einzelspielerwelt.
Spigot, Folia und andere Minecraft-Versionen sind nicht als Ziel getestet.

## Funktionen

- **250 Start-Coins** je Spieler und Server, über alle Welten gemeinsam.
- **28 Artikel**: Bauen, Rohstoffe, Essen und Ausrüstung.
- Linksklick auf einen Artikel, **1–16 Bündel** wählen, grün bestätigen.
  Erst die Bestätigung bucht Coins ab.
- **Lieferung nach 30 Sekunden**; Offline-Zeit zählt mit. Eine Chatnachricht
  meldet abholbereite Pakete, solange du online bist.
- Persönliches Paketfach über das Fass-Symbol oder `/amazon pakete`.
  Maximal **56 offene Bestellungen**, 28 pro Seite.
- Ein angekommenes Paket anklicken, um den Inhalt ins Inventar zu legen.
  Bei zu wenig Platz bleibt das komplette Paket erhalten. Rüstung und Nebenhand
  zählen nicht als freier Lieferplatz.
- **100 Bonus-Coins alle 24 Stunden**, zuerst sofort abholbar.
- Items aus der Haupthand verkaufen: im Menü über den Smaragd mit Bestätigung,
  per Befehl sofort. Nur unveränderte Standard-Items mit Ankaufspreis.
- Solange der Shop offen ist, sind Inventar-Klicks und Ziehen gesperrt.
  Zum Umsortieren oder Wechseln des gehaltenen Items das Menü schließen.

Pakete sind Einträge im Menü, keine neuen Blöcke oder fliegenden Lieferdrohnen.
Coins sind nur Spielgeld dieses Plugins, keine kaufbaren Minecraft-Minecoins.

## Befehle und Berechtigung

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

Englische Unterbefehle: `orders`, `balance`, `daily`, `sell`, `help`.
**`amazonmc.use`** ist standardmäßig für alle Spieler aktiv. Keine OP-Rechte nötig.
Ein Berechtigungsplugin kann den Zugriff entziehen. Die Konsole kann nicht einkaufen.
Benannte, verzauberte oder anderweitig angepasste Items werden nicht angekauft.
Preise stehen im Shop; nicht alle Artikel sind verkaufbar.

## Speicher, Sicherungen und Fabric-Migration

Konten liegen unter `plugins/AmazonMC/accounts/<UUID>.json`. Gespeichert werden
Coins, Bonuszeit und Bestellungen. Änderungen werden sofort über eine temporäre
Datei gespeichert; beschädigte Konten werden gesperrt und nicht zurückgesetzt.
Nach Verkäufen und Abholungen wird zusätzlich die Spielerspeicherung angestoßen.

Server normal stoppen und **Welten samt Spielerdaten und `plugins/AmazonMC`
gemeinsam sichern und wiederherstellen**. Konto-JSON und Minecraft-Spielerdaten
bilden keine gemeinsame Datenbanktransaktion: Ein harter Absturz mitten in einer
Inventartransaktion kann eine Abweichung verursachen. Dateien nicht im laufenden
Betrieb bearbeiten. Für einen einzelnen Server gedacht, keine geteilte Proxy-Economy.

Das JSON-Format ist mit AmazonMC Fabric 1.0.0 kompatibel. Es gibt **keine automatische
Migration**. Für eine manuelle Übernahme beide Server stoppen und vollständig sichern.
Die gewünschten `<UUID>.json` aus `<Fabric-Welt>/amazonmc/accounts/` nach
`plugins/AmazonMC/accounts/` kopieren; den Zielordner bei Bedarf erstellen.
Bestehende Zielkonten nicht überschreiben oder Konten aus mehreren Welten ungeprüft
zusammenführen. Die Spieler-UUIDs müssen gleich bleiben, etwa durch unveränderten
Online-Modus und korrekt eingerichtetes Proxy-Forwarding. Anschließend Guthaben und
Pakete auf einem Testserver prüfen. Die alte Fabric-Mod nicht parallel betreiben.

Das Plugin verwendet keine externen Netzwerkdienste und enthält keine Telemetrie.
Sortiment und Preise sind in `Catalog.java`, Guthaben und Zeiten in `Ledger.java`
festgelegt. Änderungen erfordern einen neuen Build; es gibt noch keine Preis-Konfiguration.

## Selbst bauen

Voraussetzungen: **JDK 21**, Internet für den ersten Build. Der mitgelieferte Wrapper
verwendet Gradle 9.2.1 und prüft dessen Download per SHA-256.

Windows:

```powershell
.\gradlew.bat clean build
```

Linux/macOS:

```sh
./gradlew clean build
```

Die installierbare JAR liegt unter `build/libs/amazonmc-paper-1.21.11-2.0.0.jar`.
Die `-sources.jar` enthält nur Quellcode. Tests: `./gradlew test --rerun-tasks`.
Paper API und Gson werden vom Server bereitgestellt; MockBukkit und JUnit werden
nur für Tests benötigt und nicht in die Plugin-JAR gepackt.

Falls Java unter Windows `Unable to establish loopback connection` meldet:

```powershell
New-Item -ItemType Directory -Force .\tmp | Out-Null
$buildTemp = (Resolve-Path .\tmp).Path
$env:TEMP = $buildTemp
$env:TMP = $buildTemp
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$buildTemp -Djava.io.tmpdir=$buildTemp"
.\gradlew.bat --no-daemon clean build
```

Diese Einstellungen gelten nur für das aktuelle Terminal. Ein kurzer Projektpfad hilft.

## Tests und Grenzen

Stand 27.08.2026: **Clean-Build erfolgreich, 34 Tests bestanden, keine übersprungen.**
Noch kein Live-Spieltest auf einem Paper-Server durchgeführt. Die Tests prüfen
Konten und Speicherung, Inventarübergaben sowie Plugin-Laden,
Befehle, Kaufbestätigung, Menü-Ereignisse, Berechtigungen, Verkauf und Paketabholung
mit MockBukkit. Der Aufruf der Spielerspeicherung wird dabei nur erfasst; echte
Spielerdateien werden im Test nicht geschrieben. MockBukkit simuliert den Server; das ersetzt keinen Spieltest auf
Paper mit deinen weiteren Plugins. Die Minecraft-EULA wird nicht automatisch akzeptiert.

Vor dem Einsatz in einer wichtigen Welt auf einem Testserver prüfen:

1. `/amazon` öffnen, Brot bestellen, Abbuchung prüfen.
2. Vor 30 Sekunden abholen: keine Ausgabe; danach genau die bestellte Menge.
3. Bei vollem Inventar abholen: Paket bleibt erhalten.
4. Shift-Klicks, Zahlen, Q, Doppelklick und Ziehen: keine Menü-Icons entnehmbar.
5. Bonus zweimal versuchen: nur einmal 100 Coins.
6. Verkauf bestätigen, ausloggen und Server neu starten: Konten und Pakete erhalten.
7. `amazonmc.use` entziehen: Zugriff und bereits vorgemerkte Klicks blockiert.

## Technische Quellen

- [Paper: Projekt einrichten](https://docs.papermc.io/paper/dev/project-setup/)
- [Paper: plugin.yml](https://docs.papermc.io/paper/dev/plugin-yml/)
- [Paper 1.21.11: InventoryClickEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryClickEvent.html)
- [MockBukkit](https://github.com/MockBukkit/MockBukkit)

Lizenz: MIT, siehe `LICENSE`.

# Chess_Analyse

Ein Schach-Analyse-App für Android mit Chess.com-Integration und Stockfish-Engine.

## Stockfish Engine Einrichtung

Die App nutzt eine native Stockfish-Engine (`libstockfish.so`), um Stellungen direkt auf dem Gerät zu analysieren.

### Schritt 1: Ordnerstruktur erstellen
Lege unter `app/src/main/jniLibs/` die passenden ABI-Ordner an:
- `app/src/main/jniLibs/arm64-v8a/` (für Physische Smartphones / ARM64)
- `app/src/main/jniLibs/x86_64/` (für Android Emulator auf PC)

### Schritt 2: `libstockfish.so` platzieren
1. Lade eine für Android kompilierte Stockfish-Binary (z. B. Stockfish 16/17 für Android/ARM64) herunter oder kompiliere Stockfish mit dem Android NDK.
2. Benenne die Datei in `libstockfish.so` um.
3. Platziere die Datei `libstockfish.so` im entsprechenden Ordner:
   `app/src/main/jniLibs/arm64-v8a/libstockfish.so`

### Schritt 3: App neu bauen und installieren
Baue die App in Android Studio neu (`Build > Rebuild Project`) und installiere sie auf dem Smartphone.
Durch `android:extractNativeLibs="true"` wird `libstockfish.so` beim Installieren auf dem Gerät im nativen Bibliotheksverzeichnis entpackt und ausführbar gemacht.

# Moteur DAB+ (DABplay+)

Ces modules viennent de [DABplay+](https://github.com/jvspier/dabplayplus) de jvspier, commit `f935586`
(29/10/2026, version 1.4.0), sous licence **MIT** ([LICENSE](LICENSE)) : ils pilotent directement une clé DAB+ USB
et décodent le DAB+ sur l'autoradio, en Kotlin pur (aucun code natif).

| Module | Rôle |
|---|---|
| `dab-core` | FIC / FIG, super-trames DAB+, PAD (DLS, DL Plus, diaporama MOT), décodeur MPEG Layer II, annonces |
| `tuner-api` | interfaces `Transport`, `RawTuner`, `ModuleTuner` |
| `driver-dtr100` | clés wRadio **C100 / DTR-100** (`16c0:05dc` : XTRONS USBDAB01, Joying, Pumpkin, Dasaita…), la seule testée en vrai par l'auteur |
| `driver-si4684` | modules **Si4684** (`1a86:5537` : Eonon A503, XFA-DAB220), non testés |
| `driver-siano` | clés **Siano SMS2160** (`187f:0800`, `0700`, `0802`), non testées |
| `engine` | `DabEngine` : recherche, lecture, suivi de la station, annonces ; ports `Dongle`, `AudioOut`, `Focus`, `EngineLog` |
| `usb-android` | transport USB Android (`UsbDeviceConnection.bulkTransfer`) |
| `audio` | sortie audio (AAC par `MediaCodec`, MP2, PCM) sur `AudioTrack` |
| `data` | liste des stations (`StationStore`, JSON) |
| `logos` | seulement `ImageSniff` (contrôle des images du diaporama) |

Changements pour LibreHU : le module `logos` ne garde que `ImageSniff.kt` (la recherche de logos par okhttp / dnsjava
est retirée), les alias du catalogue de versions sont ceux de LibreHU FM. Le reste est tel quel : pour suivre l'amont,
recopier les dossiers depuis `android/` du dépôt d'origine et refaire ces deux changements. Le branchement à
l'appli (clé, permission USB, source « DAB », écrans) est dans `app/src/main/java/org/librehu/fm/dab/`.

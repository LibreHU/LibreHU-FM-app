# LibreHU FM

Radio FM pour autoradios Autochips AC8257 (Jancar UJC201) et autres appareils MediaTek, au style des interfaces
voiture (thème sombre, grosses cibles tactiles), avec **widget** pour le lanceur
[LibreHU-Launcher](https://github.com/LibreHU/LibreHU-Launcher-App).

- Puce FM interne MediaTek (combo **MT6631**) pilotée par `/dev/fm`, via le JNI FM d'AOSP (Apache 2.0, voir
  [app/src/main/cpp/fmr/README.md](app/src/main/cpp/fmr/README.md)). Les codes ioctl sont les mêmes que ceux de
  l'app radio Jancar d'origine.
- Son : source de capture `RADIO_TUNER` recopiée vers un flux média (méthode « render » d'AOSP FMRadio et de l'app
  Jancar).
- **Logo de la station** : recherché par le nom RDS dans [Radio Browser](https://www.radio-browser.info)
  (base ouverte), seulement si le nom correspond, puis gardé en cache par fréquence ; affiché dans l'app, le widget,
  la notification et la carte média du lanceur. Nécessite Internet la première fois.
- Recherche ▲▼, pas de 0,1 MHz, balayage de la bande, favoris, RDS (nom de station, radiotexte).
- `MediaSession` : touches au volant, notification, carte « média » du lanceur.
- Widget : station, radiotexte, précédente / lecture / suivante.

## Branches

Cette branche : **`ivi`** (Jancar ivi-services).


| Branche | Intégration autoradio |
|---|---|
| `main` | aucune (Android générique) |
| `ivi` | Jancar **ivi-services** : `IRadio.open/close` (source radio active, alimentation d'antenne) |
| `librehu-service` | [LibreHU-service](https://github.com/LibreHU/LibreHU-service) : alimentation d'antenne |

## Installation (obligatoirement en app privilégiée)

`/dev/fm` appartient au groupe `media` (permission `ACCESS_BROADCAST_RADIO`) et la capture `RADIO_TUNER` exige
`CAPTURE_AUDIO_OUTPUT` : deux permissions réservées aux apps privilégiées. Avec root ou en reconstruisant la ROM :

```
adb root && adb remount
adb shell mkdir -p /system/priv-app/LibreHU-FM
adb push app-debug.apk /system/priv-app/LibreHU-FM/LibreHU-FM.apk
adb push install/privapp-permissions-org.librehu.fm.xml /system/etc/permissions/
adb reboot
```
Au premier lancement, accepter la permission micro (`RECORD_AUDIO`, demandée par Android pour toute capture).

Non testé sur l'appareil à ce stade : la séquence est celle d'AOSP et de l'app Jancar, mais le comportement réel
(niveau audio, RDS) reste à valider.

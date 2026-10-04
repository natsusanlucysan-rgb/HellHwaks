# Hells Hawks 2.1

Mod cliente para Fabric 1.21.1 (Minecraft Java) con un HUD social de chat pensado para el servidor Diosesmon / Cobblemon.

## Qué incluye
- Panel con pestañas: General, Susurros, Clan, Alianza, TPA y Combates (con ajuste de línea y rueda del ratón).
- Historial persistente de hasta 800 mensajes (`config/hellhawks.json`).
- Centro de notificaciones (botón NOTIF), avisos en pantalla y sonido, ambos desactivables.
- Botón para aceptar TPA, marcas de tiempo opcionales.
- Tecla **K** para abrir (se puede cambiar en Controles). Enter envía el mensaje.

## Requisitos
Minecraft 1.21.1, Fabric Loader >= 0.16.7, Fabric API, Java 21.

## Compilar
Con Gradle 8.8 o superior instalado y Java 21:

    gradle build

El mod queda en `build/libs/hellhawks-2.1.0.jar` (no uses el `-sources`).
Instálalo en la carpeta `mods` junto con Fabric API.

Sin instalar nada: sube la carpeta a un repositorio de GitHub y ejecuta la acción "build"
(`.github/workflows/build.yml`); el .jar se descarga desde "Artifacts".

## Comandos del servidor
Los comandos están en `config/hellhawks.json` (se crea al jugar una vez), sección `commands`:
`whisper`, `clan`, `ally`, `tpaccept`. `{player}` y `{msg}` se sustituyen automáticamente.
Por defecto: `msg {player} {msg}`, `clan chat {msg}`, `ally chat {msg}`, `tpaccept`.

## Detección de mensajes
Los patrones están en `HellHawksParser.java`. El clan se detecta por la etiqueta `[Clan]` al inicio
de la línea y la alianza por `[Alianza]`/`[Ally]`. Si Diosesmon usa otro formato, ajusta esos patrones.

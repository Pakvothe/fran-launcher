# Fran Launcher

Un launcher ultraliviano para tablets y celulares Android: un reloj grande, tus apps favoritas ordenadas por cuánto las usás, y nada más. Sin recolección de datos, sin publicidad, sin cuentas, sin permiso de internet.

Hecho por [Pakvothe](https://github.com/Pakvothe). Hermano de [Fran Launcher TV](https://github.com/Pakvothe/fran-launcher-tv).

![Fran Launcher en una tablet y un celular](docs/demo.gif)

[English](README.md) · **[Descargar el APK](https://github.com/Pakvothe/fran-launcher/releases/latest)** (121 KB, Android 10+)

## Por qué

Los launchers de fábrica tienen cientos de megas en memoria para mostrar widgets, feeds y sugerencias que no pediste. Fran Launcher hace menos a propósito: todo se dibuja localmente, en una sola View nativa, y no trabaja mientras no lo tocás.

Medido en dos aparatos contra Niagara Launcher y el de fábrica (RAM propia = Java + nativa + código; RAM a los 20 s de abrir; arranque en frío = mediana de 3):

**Honor Pad (NDL-W09, Android 16, 1200×1920)**

| | Fran Launcher | Niagara | Honor (fábrica) |
|---|---|---|---|
| RAM propia | **12,2 MB** | 50,1 MB | 201,7 MB |
| RAM total (PSS) | **56,6 MB** | 110,8 MB | 263,9 MB |
| Arranque en frío | **425 ms** | 638 ms | n/d (se relanza solo) |
| CPU en reposo | ~0% | ~0% | ~0% |

**Samsung Galaxy S24 Ultra (Android 17, 1440×3120)**

| | Fran Launcher | Niagara | One UI (fábrica) |
|---|---|---|---|
| RAM propia | **28,1 MB** | 54,5 MB | 363,7 MB |
| RAM total (PSS) | **142,8 MB** | 189,9 MB | 481,9 MB |
| Arranque en frío | 92 ms | 113 ms | 54 ms (queda precargado) |
| CPU en reposo | ~0% | ~0% | ~0% |

La mayor parte de la RAM total son los búferes gráficos de la ventana, que dependen de la resolución de la pantalla y no del launcher.

## Funciones

- **Reloj grande** centrado, con la fecha y el momento del día. Tocá la hora para las alarmas y la fecha para el calendario.
- **Favoritas por uso**: la que más usás queda abajo, al lado del pulgar, con el nombre más grande; las menos usadas se achican hacia arriba. Muestra el tiempo de hoy ("38 min hoy") y un contador de notificaciones. El orden tiene histéresis, así que la lista no salta a cada rato.
- **Ver más**: si tus favoritas no entran, un "+N más" abre una lista que se desplaza.
- **Cajón de apps**: deslizá hacia arriba. Búsqueda, lista A-Z con índice de letras y una letra gigante al recorrerlo. Mantené apretada cualquier app: sumar/sacar del inicio, ocultar, info, desinstalar.
- **Fondos**: orbes suaves que siguen la hora del día (por defecto), tu fondo de pantalla del sistema, blanco o negro.
- **Lo que suena**: título, artista y carátula, con play/pausa. Opcional; necesita acceso a las notificaciones, que solo se usa para leer la sesión de medios y contar notificaciones.
- **Reposo**: tras unos segundos sin tocar, el reloj crece y se centra. Configurable o apagado.
- **Responsive**: tablets en vertical y horizontal (se puede fijar la rotación), celulares siempre en vertical.
- **Idiomas**: español e inglés.

## Cómo está hecho

- **Java puro**, `minSdk 29`, sin Kotlin, sin AndroidX, sin dependencias de terceros.
- **Una Activity, una View, un árbol de `RenderNode`s**: sin layouts XML, sin `RecyclerView`, sin `Fragment`. Desplazar y apretar son solo propiedades de nodos ya grabados.
- **Cero trabajo en reposo**: sin polling ni animaciones quietas. El reloj se redibuja una vez por minuto.
- **Arranca desde un snapshot binario**: la configuración, la lista de apps y el atlas de íconos se leen en crudo (`FileChannel.map`), sin decodificar imágenes. Las apps se vuelven a leer solo cuando se instala, desinstala o actualiza algo.
- **Búsqueda sin `EditText`**: la View habla con el teclado por su propia `InputConnection`.
- **Lógica testeable en la JVM**: el ranking por uso y el formato del snapshot viven en el módulo `core`, sin APIs de Android.

```
launcher/
  app/   App Android (HomeActivity, Stage, capas, datos, medios)
  core/  Lógica en Java plano + tests JUnit
```

## Instalar y usar como inicio

Bajá el APK de [Releases](https://github.com/Pakvothe/fran-launcher/releases/latest) (o compilalo), instalalo, apretá Home y elegí Fran Launcher. O por adb:

```sh
adb install -r fran-launcher.apk
adb shell cmd package compile -m speed -f pakvothe.franlauncher.tablet
```

Permisos opcionales, desde los Ajustes del launcher (mantené apretado un lugar vacío del inicio):

- **Ordenar por uso** → Acceso al uso.
- **Lo que suena y notificaciones** → Acceso a las notificaciones.

## Compilar

```sh
cd launcher
./gradlew :core:test :app:assembleRelease
```

## Licencia

MIT. La fuente Outfit está bajo la SIL Open Font License.

# 🗿 Achilles — mod para Minecraft Forge 1.20.1

Estatuas de un antiguo guerrero (Aquiles) repartidas por el mundo, con una **zona sagrada** a su alrededor,
y un **mini Aquiles** adorable y torpe que intenta ser el héroe de la leyenda.

## Las estatuas

- **Aparecen solas** en el Overworld: una por región de 16×16 chunks, siempre en la zona central de la región.
  Así dos estatuas quedan separadas **más de ~150 bloques**, y como el radio máximo es 58 bloques, **las zonas nunca chocan**,
  suban los niveles que suban. Cada estatua se genera sobre una plataforma de ladrillos con pilares rotos y 1-2 mini Aquiles de guardia.
- **Indestructibles**: ni jugadores (ni en creativo), ni explosiones, ni Wither/Dragón, ni pistones.
- **Zona de inmunidad** (radio base 8 bloques):
  - Los mobs hostiles no pueden aparecer dentro, son empujados fuera al acercarse y los que se cuelan se desvanecen. Los jefes solo son repelidos.
  - Los jugadores dentro no reciben daño (salvo vacío y `/kill`) y recuperan vida cada segundo.
- **Propiedad**: el primer jugador que interactúa (clic derecho) con una estatua sin dueño la reclama.
  Mientras no tiene dueño, protege y cura a todo el mundo. Cuando tiene dueño, **solo entran él y los jugadores a los que dé permiso**;
  el resto es expulsado del área. Los administradores en creativo (permiso 2) pueden entrar.
- **Mejoras** (clic derecho como propietario → pantalla): cada punto se invierte en **Radio** o en **Curación**, hasta nivel 100 en cada una, por estatua.
  Los puntos son del jugador y se reparten entre todas sus estatuas.

| | Nivel 0 | Nivel 100 |
|---|---|---|
| Radio | 8 bloques | 58 bloques (+0,5 por nivel) |
| Curación | 0,5 vida/s | 10,5 vida/s (+0,1 por nivel) |

Extras de curación: nivel 25+ da Regeneración, nivel 50+ limpia efectos negativos, nivel 75+ da Resistencia y nivel 100 también sacia el hambre. Las explosiones tampoco destruyen bloques dentro de una zona protegida.
Los números están en `data/StatueStats.java`.

En la pantalla de la estatua también se dan o quitan permisos escribiendo el nombre de un jugador (hasta 20).

## Puntos por encontrar estatuas (1.6.0)

Cada jugador gana **5 puntos** la primera vez que *encuentra* una estatua (acercándose a menos de 24 bloques o al radio de su zona, lo que sea mayor, o tocándola). Cada estatua da puntos una sola vez por jugador. Los puntos se gastan en las estatuas que posees.

## Mini Aquiles mejorable (1.6.0)

En la pantalla de la estatua hay una tercera mejora: **Mini Aquiles** (nivel 0-100). Cada nivel da +0,5 de vida y +0,1 de daño a los guardianes de esa estatua (nivel 100: 62 de vida y 11 de daño, sumado a los bonos de la curación). Se aplica al instante a los guardianes que ya existen.

## Santuario total (1.6.0)

- **Mobs del jugador protegidos**: mascotas (lobos, gatos, loros...), caballos domados, golems de hierro construidos por jugadores y mini Aquiles no reciben ningún daño dentro de la zona (salvo vacío y `/kill`), y se curan. Quien los ataque recibe el daño reflejado.
- **Lo de dentro no se puede romper**: sin permiso nadie puede picar, colocar, usar bloques ni interactuar con entidades de la zona; los mobs hostiles no destruyen ni mueven bloques (enderman, zombis con puertas, wither, dragón, creepers...); las explosiones no rompen bloques ni dañan nada de dentro; ningún pistón de fuera puede empujar, tirar ni destruir bloques de la zona; el fuego que aparezca se apaga al instante; no se pisotean los cultivos; y los objetos tirados dentro no se queman ni explotan.
- Los propietarios y jugadores con permiso sí pueden construir y picar dentro de su zona con normalidad.
- Límites: el agua y la lava que fluyan desde fuera no se detienen.

## Escudo reflectante (1.5.0)

- **Proyectiles**: flechas, tridentes, bolas de fuego, calaveras del Wither, pociones, perlas de Ender, bolas de nieve, etc. que vayan a **entrar** en la zona rebotan en su superficie y salen disparados **de vuelta hacia quien los lanzó** (o espejados si no se sabe quién fue). Los proyectiles de jugadores con permiso atraviesan la zona con normalidad, y los cohetes de fuegos artificiales se ignoran.
- **Daño**: todo daño dirigido a algo protegido (jugadores con permiso, mini Aquiles, animales, aldeanos...) se cancela y se **devuelve al atacante** (hasta 40 de daño por golpe). Los jugadores con permiso pueden dañar a los seres no hostiles de dentro; entre ellos no hay reflejo.
- Con la estatua sin dueño todo el mundo cuenta como "con permiso", así que solo se reflejan los ataques de mobs.

## La guardia de Aquiles

La progresión de una estatua también afecta a sus guardianes:
- Una estatua mantiene al menos **1 mini Aquiles** cerca.
- Radio nivel 25/50/75 desbloquea progresivamente hasta **4 guardianes**.
- Curación nivel 25/50/75 mejora su resistencia y daño en tres escalones.
- Los guardianes recuerdan su puesto, vuelven después del combate y no se alejan indefinidamente.
- Si la estatua está cargada, el sistema revisa periódicamente que su guardia siga presente.

## El mini Aquiles

Guerrero diminuto con casco de cresta roja, peto de bronce, grebas, escudo, espadita y capa.
Practica con la espada, **se tropieza y se queda tumbado**, y se cree el héroe: ataca monstruos (menos creepers)… aunque pega con cosquillas.
Sonidos propios (¡piii!, ¡eek!, tropiezo, clang de juguete…) sintetizados en `tools/generate_assets.py`.
Aparece en llanuras, bosques, sabana y prados, y cerca de las estatuas. Los guardianes de una estatua son persistentes y están ligados a su puesto. Huevo generador en la pestaña creativa.

## Zona visible

La zona de protección se muestra automáticamente durante unos segundos a intervalos regulares. No registra ninguna tecla nueva, para evitar conflictos con otros mods.

## Compilar

Requisitos: **JDK 17**, y para generar assets **Python 3 + Pillow + numpy + ffmpeg** (los assets ya vienen generados en el repo;
solo se vuelven a generar si borras `src/main/resources/assets`).

1. Copia `gradlew`, `gradlew.bat` y `gradle/wrapper/gradle-wrapper.jar` del
   [MDK oficial de Forge 1.20.1](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html), o usa
   el workflow de GitHub Actions incluido (`.github/workflows/build.yml`), que compila el jar sin wrapper.
2. `./gradlew build` → jar en `build/libs/`. Para probar: `./gradlew runClient`.

## Administradores

La Estatua de Aquiles está en la pestaña creativa para colocarla a mano (2 de alto, mira hacia ti al colocarla).

## Licencia

MIT.

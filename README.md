# Planificación plurianual de un plantel de fútbol con Algoritmos Evolutivos

Proyecto final del curso **Algoritmos Evolutivos** (FING, UdelaR, edición 2026).
Integrantes: Juan Ignacio Apolo y Alen Cukier.

> **Alen:** este README es el traspaso. Explica qué está hecho, qué decisiones se
> tomaron y por qué, qué falta, y te pide algo concreto antes de seguir:
> **validar todo** (sección [Lo primero: validación](#lo-primero-validación)).
> No avances con lo pendiente hasta que la validación esté hecha y anotada.
>
> **Juan (22/11, Alen):** la validación está hecha y anotada en
> [`docs/VALIDACION.md`](docs/VALIDACION.md): todo se reproduce exactamente.
> Desde ahí el trabajo se coordina con estos archivos (leelos antes de seguir):
>
> | archivo | para qué |
> |---|---|
> | [`CLAUDE.md`](CLAUDE.md) | reglas para nuestros dos Claude (y para nosotros) |
> | [`docs/PENDIENTES.md`](docs/PENDIENTES.md) | tablero de tareas con responsable + bitácora + correcciones para el informe final |
> | [`docs/DECISIONES.md`](docs/DECISIONES.md) | tus decisiones D1–D11 y M1–M8, más las nuevas D-A* |
> | [`REFERENCIAS.md`](REFERENCIAS.md) | qué parte del informe está en qué archivo |
>
> Cambio grande: **la biblioteca del proyecto es jMetal 5.10** (la del Tema 6).
> Tu simulador y tus operadores siguen siendo el código del problema; jMetal
> pone el motor. Tu `AlgoritmoEvolutivo` queda como implementación de
> referencia para validar la versión jMetal (ver D-A1).

---

## El problema en una página

Dado el plantel actual de un club, un mercado de candidatos, un presupuesto y un
tope salarial, se busca un plan completo para **3 temporadas = 6 ventanas de
pases**. Para cada jugador el plan elige una sola acción en todo el horizonte:

- **propios:** mantener, renovar, vender, ceder a préstamo, liberar;
- **mercado:** ignorar, comprar, incorporar a préstamo.

Cada gen es la tupla `(acción, ventana, duración, multiplicador salarial,
cláusula, prioridad de minutos µ)`, con codificación mixta.

El fitness **no tiene fórmula cerrada**: se obtiene simulando las tres temporadas
N veces con lesiones, evolución del nivel de cada jugador según su edad y sus
minutos, negociaciones que pueden fracasar y clubes rivales que pueden llevarse
jugadores pagando la cláusula. Esa es la justificación central del AE (junto con
la NP-dificultad por restricción a mochila multidimensional y la epistasis entre
genes). El informe inicial está en `docs/Informe_Inicial_v2.pdf`.

---

## Estado actual

### Lo que está hecho

| componente | estado |
|---|---|
| Simulador (evolución de atributos, lesiones, valor, salarios, rivales) | hecho y validado |
| Decodificador cronológico, reparación y función de fitness | hecho y validado |
| AE generacional con elitismo, escenarios comunes y selección final en dos etapas | hecho y validado |
| Referencias: statu quo, búsqueda aleatoria, greedy miope, óptimo exacto en micro-instancias | hechas |
| Tests estadísticos: Shapiro-Wilk, Lilliefors, ANOVA, Kruskal-Wallis, Friedman, Wilcoxon (exacto), t de Welch, Mann-Whitney, Holm, Bonferroni | hechos y validados contra scipy (32 chequeos) |
| Diseño factorial 3×3×3 de configuración paramétrica | hecho; **falta correrlo completo** |
| Variantes del temario: gap generacional, estado estacionario, sharing, CHC, memético | hechas y comparadas |
| **Adaptador jMetal 5.10** (`Problem`, `Solution`, operadores) y **AE generacional sobre jMetal** | hecho; equivalencia con el AE propio validada con 50 semillas (Alen) |
| **NSGA-II** (jMetal) + hipervolumen relativo, spread, GD; comparación contra agregación | hecho y validado (Alen); falta la corrida completa |
| Comparación AE vs referencias (30 corridas, tests, robustez 10.000, eficiencia) | programa hecho (Alen); falta la corrida completa |
| Límite de tiempo de reloj | hecho, apagado por defecto (D-A11) |

### Resultados de referencia

Instancia sintética `demo` (25 propios, 50 candidatos). Todo fitness se
**revalúa con N=200 sobre escenarios de validación** que nunca se usan durante
la búsqueda.

| método | f(x) | comentario |
|---|---|---|
| statu quo (no operar) | 211,60 | |
| búsqueda aleatoria, 8.000 evaluaciones | 211,60 | ningún plan al azar supera al statu quo |
| greedy miope | 219,35 | optimiza solo la temporada siguiente |
| **AE, 8.000 evaluaciones** | **233,28** | +10,2% sobre statu quo, +6,4% sobre greedy |
| AE, 24.000 evaluaciones (media de 6 semillas) | 234,70 | |

- La ganancia del AE es **deportiva**, no contable: 82,4 / 81,3 / 82,3 puntos
  por temporada contra 75,4 / 75,8 / 70,8 del statu quo, con patrimonio final
  parecido (542M contra 516M).
- **Micro-instancia** (4 propios, 2 candidatos, 1.185.921 planes enumerados):
  el AE alcanza el óptimo exacto. GAP medio de 10 corridas: 0,14% con 500
  evaluaciones, 0,06% con 1.000 y 0,03% con 2.000.

### Comparación de variantes del temario

10 semillas por variante, 8.000 evaluaciones (incluyen reevaluaciones y búsqueda
local), Wilcoxon pareado por semilla contra la base con post-hoc de Holm (p exacto,
ver DECISIONES D-A5; antes Bonferroni sobre la aproximación normal, que daba 0,025).
Datos crudos en `resultados/resultados_variantes.csv`.

| variante | media | dif. vs base | diversidad final | veredicto |
|---|---|---|---|---|
| **base** (generacional + elitismo) | **230,81** | — | 14,0 | — |
| memético (búsqueda local cada 5 gen.) | 230,82 | +0,01 | 14,3 | sin diferencia |
| sharing (σ = 6 genes) | 228,35 | −2,46 | 23,9 | peor (p Holm = 0,0098) |
| gap generacional 50% | 228,05 | −2,76 | 7,4 | peor (p Holm = 0,0098) |
| CHC | 218,34 | −12,47 | 31,2 | peor (p Holm = 0,0098) |
| estado estacionario | 214,43 | −16,38 | 5,1 | peor (p Holm = 0,0098) |

Con 24.000 evaluaciones, sharing contra base: −1,08, ya no significativo
(p exacto = 0,44). **Conclusión: el AE generacional con elitismo queda como algoritmo
definitivo.** La explicación de cada resultado está en los comentarios de
`AlgoritmoEvolutivo.java` y es material directo para el informe final.

---

## Decisiones de modelado (no cambiarlas sin dejar escrito por qué)

Casi todas existen porque el AE encontró y explotó un agujero del simulador.

1. **El crecimiento del nivel depende de los minutos; el declive, no.** Si no,
   conviene guardar veteranos en el banco para congelarles el nivel.
2. **Cada temporada tiene dos mitades y una ventana antes de cada una.** Antes la
   última ventana caía después de la última temporada: renovar a todo el plantel
   ahí sumaba fitness sin jugar un partido.
3. **Los salarios se pagan de la caja** en cada media temporada, no solo cuentan
   contra el tope.
4. **Renovar cuesta**: el jugador exige el salario de su nivel *actual*, hay un
   incremento mínimo del 10% y una prima de firma (la mitad del valor que crea
   alargar el contrato).
5. **El patrimonio final es neto**: al valor de mercado de cada jugador se le
   resta el sobrecosto de su contrato.
6. **La cesión dura hasta fin de temporada y el jugador vuelve**, con los minutos
   que jugó afuera.
7. **Clubes rivales reactivos**: ofertan por tus jugadores y ejecutan la cláusula
   si llegan; fichan candidatos del mercado; renuevan a los suyos. Por eso la
   cláusula es una decisión real (baja = menos salario pero más riesgo).
8. **La fuerza del equipo usa el banco** (los 7 siguientes al once), no la media
   de todo el plantel: con la media, liberar suplentes malos subía los puntos.
9. **β = 0,05** (peso del patrimonio frente a los puntos). Calibrado por barrido
   (`SensibilidadBeta`); entre 0,03 y 0,10 los planes rinden parecido.
10. **Préstamos entrantes**: con cargo y tope de 3 simultáneos.
11. **Mínimos por puesto escalados por instancia**, para que una micro-instancia
    no arrastre una penalización constante.

## Reglas metodológicas (mantenerlas siempre)

- **Escenarios comunes**: el azar se sortea por (jugador, período, evento), así
  que el mismo escenario le toca igual a todos los planes. Toda la población de
  una generación se evalúa sobre los mismos escenarios, que rotan entre
  generaciones.
- **Reevaluación** de todo sobreviviente cuando rotan los escenarios.
- **Selección final en dos etapas**: se remiden los 10 mejores candidatos sobre
  los mismos escenarios y con más réplicas.
- **Revaluación final** con N=200 y semillas que nunca se usan en la búsqueda.
- **Presupuesto en evaluaciones de fitness**, no en generaciones.
- **Instancias de configuración separadas de las de evaluación.**
- **Friedman y Wilcoxon bloqueados por semilla**: la semilla s de dos
  configuraciones comparte población inicial y escenarios.
- Con menos de 10 muestras, no se concluye normalidad: se usan tests no
  paramétricos.

---

## Cómo compilar y correr

Java 21 y jMetal 5.10 (con sus dependencias de Apache Commons), declarados en
`pom.xml`. Las fuentes siguen en `src/`.

```bash
./compilar.sh      # compila en out/; usa los .jar de lib/ o los baja con Maven
./validar.sh       # compila y corre la validación completa (unos 4 minutos)
CP="$(cat out/classpath.txt)"   # classpath para correr cualquier programa
```

`lib/` no se commitea (la consigna prohíbe incluir el framework). Si no
tenés Maven, poné ahí los `.jar` de `jmetal-core` y `jmetal-algorithm` 5.10
y sus dependencias, o definí `JMETAL_CP`.

En la tabla de abajo, `java -cp out` se lee como `java -cp "$CP"`.

| comando | qué hace |
|---|---|
| `java -cp out ae.plantel.ValidarEstadistica` | tests estadísticos contra valores de scipy |
| `java -cp out ae.plantel.ValidarAgujeros` | regresión contra los agujeros ya corregidos |
| `java -cp out ae.plantel.ValidarPlan` | decodificador y fitness contra planes al azar |
| `java -cp out ae.plantel.EjecutarAE 8000 1` | AE vs statu quo vs búsqueda aleatoria |
| `java -cp out ae.plantel.Diagnostico 8000` | de dónde sale la utilidad del plan del AE |
| `java -cp out ae.plantel.GreedyMiope 8000` | referencia golosa miope |
| `java -cp out ae.plantel.OptimoExacto 4 2` | GAP contra enumeración exhaustiva |
| `java -cp out ae.plantel.SensibilidadBeta 6000` | barrido del peso del patrimonio |
| `java -cp out ae.plantel.CompararVariantes correr <variante> <desde> <hasta> <evals>` | variantes: `base`, `gap50`, `estacionario`, `sharing`, `chc`, `memetico` |
| `java -cp out ae.plantel.CompararVariantes analizar <evals>` | análisis estadístico de las variantes |
| `java -cp out ae.plantel.Experimentos 120000 30 2` | barrido factorial completo (para ClusterUY), con `AGPlantel` y N = 10 (D-A13) |
| `java -cp out ae.plantel.jmetal.ValidarEquivalenciaJMetal 50 8000` | AE sobre jMetal vs AE de referencia (unos 11 min) |
| `java -cp out ae.plantel.jmetal.ValidarMO` | validación de la variante multiobjetivo |
| `java -cp out ae.plantel.jmetal.CompararReferencias 120000 30` | AE vs aleatoria, greedy y statu quo en las 3 instancias de evaluación |
| `java -cp out ae.plantel.jmetal.ExperimentoMO 120000 30 eval-chica` | NSGA-II vs agregación con barrido de β, con métricas |
| `java -cp out ae.plantel.jmetal.GapMicroInstancias 30` | GAP vs óptimo exacto en 3 micro-instancias difíciles |
| `java -cp out ae.plantel.jmetal.CompararOperadores 20 8000` | torneo vs proporcional con escalado; uniforme vs bloques |
| `java -cp out ae.plantel.jmetal.EstudioReplicas demo 10` | estudio del número de réplicas N (D-A13) |
| `java -cp out ae.plantel.jmetal.ExperimentoIslas 20 24000 4` | islas: determinismo, speedup y calidad |

Costo de referencia: 5 a 7 ms por evaluación con N=30, o sea unos 15 segundos
por corrida de 8.000 evaluaciones y alrededor de un minuto por corrida de
40.000.

## Estructura del código

| archivo | qué hace |
|---|---|
| `Parametros.java` | todos los coeficientes del modelo, en un solo lugar |
| `Modelo.java` | enums, `JugadorBase`, `EstadoJugador`, `Instancia` |
| `Aleatorio.java`, `Escenario.java` | generador (SplitMix64) y sorteos por clave |
| `Evolucion.java` | evolución del nivel, lesiones, salario de referencia, valor de mercado |
| `Temporada.java` | reparto de minutos y fuerza del equipo |
| `Evaluador.java` | decodificador, reparación, rivales, economía y fitness |
| `Cromosoma.java`, `Operadores.java` | representación y operadores |
| `AlgoritmoEvolutivo.java` | el AE de referencia y sus variantes |
| `Estadistica.java` | tests estadísticos sin dependencias |
| `GeneradorInstancias.java` | instancias sintéticas |
| `jmetal/Gen.java`, `jmetal/SolucionPlantel.java` | la representación como `Solution` de jMetal |
| `jmetal/ProblemaPlantel.java`, `jmetal/ProblemaPlantelMO.java` | el problema como `Problem` de jMetal (mono y multiobjetivo) |
| `jmetal/OperadoresJMetal.java` | cruzamiento, mutación y torneo con las interfaces de jMetal |
| `jmetal/AGPlantel.java` | AE generacional sobre `AbstractGeneticAlgorithm` (motor de los experimentos) |
| `jmetal/NSGAIIPlantel.java` | NSGA-II de jMetal con escenarios comunes |
| `jmetal/IndicadoresMO.java` | hipervolumen relativo, spread y GD (indicadores de jMetal) |
| `jmetal/CompararReferencias.java`, `jmetal/ExperimentoMO.java` | experimentos finales |
| resto | programas de validación y de experimentos |

---

## Lo primero: validación

Antes de tocar nada de lo pendiente, **validá todo lo que está hecho**. La idea
es que lo revises con ojos frescos y sin dar nada por bueno. Anotá cada
discrepancia o duda como un *issue* del repositorio.

1. **Correr `./validar.sh`** y comparar contra los valores esperados de este
   README y de la cabecera de `ValidarAgujeros.java`. Los números tienen que
   coincidir exactamente: el simulador es determinista dada la semilla. Si algo
   cambia, es un error o una diferencia de entorno que hay que entender.
2. **Revisar el simulador contra el informe** (`docs/Informe_Inicial_v2.pdf`):
   que la ecuación de evolución, el modelo de lesiones, el valor de mercado y la
   función de fitness hagan lo que dice el informe. Donde difieran, decidir cuál
   está bien.
3. **Revisar cada decisión de modelado de la lista de arriba**: ¿es razonable?,
   ¿hay otra forma de explotar el simulador que no se nos ocurrió? La forma de
   buscarla es la de `ValidarAgujeros`: armar a mano un plan "tramposo" y ver si
   le rinde sin subir los puntos de liga.
4. **Revisar la metodología**: escenarios comunes, reevaluación, selección
   final, separación de instancias, uso de los tests (Wilcoxon pareado,
   Bonferroni, Friedman bloqueado).
5. **Recalcular los tests por fuera**, con R o scipy, a partir de
   `resultados/resultados_variantes.csv`.
6. **Chequear el temario**: todo lo usado tiene que poder justificarse con las
   diapositivas del curso. Dos cosas a mirar con cuidado:
   - los escenarios comunes y la selección final en dos etapas no están en las
     diapositivas como tales; son metodología de evaluación sobre un fitness
     ruidoso, que el Tema 5 sí contempla, y hay que declararlas con su cita;
   - el cruzamiento de los campos reales es aritmético (sí está en el temario);
     antes era BLX-α, que no está.

---

## Lo que falta

### 1. NSGA-II (punto pendiente del informe)

Variante multiobjetivo con **f₁ = rendimiento deportivo esperado** y
**f₂ = costo neto del plan**, en lugar de agregarlos con β. El `Evaluador` ya
calcula ambos (`Fitness.puntosMedios` y `Fitness.costoNetoMedio`).

El argumento para el informe: elegir un único β es inventar un tipo de cambio
entre millones y puntos de liga que ningún dato justifica. El frente de Pareto
devuelve las alternativas y deja la decisión al director deportivo.

Reportar, según el Tema 12: hipervolumen, *spread* y distancia generacional;
frente de referencia construido con la unión de las soluciones no dominadas de
todas las ejecuciones (mínimo 30); comparación estadística **por métrica**.
Implementación a mano (ordenamiento por frentes no dominados + distancia de
crowding) o con jMetal vía Maven.

### 2. Datos reales

Hoy las instancias son sintéticas. Hay que:

- cargar datos públicos (por ejemplo FIFA/SoFIFA para nivel, potencial, edad,
  salario, valor y posición; Transfermarkt para vencimientos de contrato)
  respetando el esquema `JugadorBase`;
- **recalibrar** `valorMercado` y `salarioReferencia` contra esos datos, y los
  parámetros de los clubes rivales, que hoy son supuestos razonables pero
  inventados;
- armar las tres instancias de evaluación del informe (25 propios; mercados de
  50, 150 y 400 candidatos);
- reconstruir el **plan real que ejecutó el club**, que es la quinta referencia
  de comparación y la única que falta.

### 3. Barrido final en ClusterUY

`Experimentos 120000 30 2` (N = 10, mismo costo que 40.000 con N = 30; ver D-A13):
27 configuraciones × 2 instancias × 30 ejecuciones.
Son unas 100 horas-núcleo, unas 3 o 4 horas con 30 núcleos. El código ya
paraleliza las corridas (maestro-esclavo sobre ejecuciones independientes) y
deja un CSV con los datos crudos.

### 4. Informe final: correcciones respecto del informe inicial

La lista completa y actualizada está en
[`docs/PENDIENTES.md`](docs/PENDIENTES.md#correcciones-para-el-informe-final-acumuladas).
Resumen:

- La implementación es en **Java 21 con jMetal 5.10**, no en Python/DEAP.
- ~~El test de normalidad es Lilliefors~~ → desde D-A5 el principal es
  **Shapiro-Wilk**, como promete el informe (Lilliefors queda de control).
- El "N creciente" se reemplazó por la **selección final en dos etapas**.
- El cruzamiento de los campos reales pasó a **aritmético**.
- Agregar las decisiones de modelado de arriba y la comparación de variantes.

## Limitaciones conocidas

- Los parámetros de los rivales y las curvas de valor y salario son supuestos
  hasta calibrarlos con datos reales.
- Las instancias de configuración y evaluación son sintéticas.
- La comparación de variantes se hizo sobre una sola instancia.
- Cada evaluación cuesta unos 6 ms porque simula a todo el mercado en cada media
  temporada; es necesario para que los escenarios sean comparables entre planes
  y para modelar a los rivales.

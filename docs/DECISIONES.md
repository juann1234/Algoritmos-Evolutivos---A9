# Decisiones del proyecto

Toda decisión que cambie qué se modela, cómo se busca o cómo se evalúa va
acá, numerada, con su porqué. **No se borra una decisión**: si se revierte,
se agrega una nueva que la reemplaza y se marca la vieja como
"reemplazada por D-xx".

- **D1–D11**: decisiones de modelado (Juan, hasta el commit `d697d60`).
- **M1–M8**: reglas metodológicas (Juan, idem).
- **D-A1 en adelante**: decisiones de implementación de Alen (desde el
  22/11/2026).

---

## Decisiones de modelado (Juan)

Casi todas existen porque el AE encontró y explotó un agujero del simulador.
Cada una tiene su sonda de regresión en `ValidarAgujeros.java`.

| # | decisión | por qué |
|---|---|---|
| D1 | El crecimiento del nivel depende de los minutos; el declive, no. | Si el declive dependiera de los minutos, convendría guardar veteranos en el banco para congelarles el nivel. Está en el informe (ec. 1). |
| D2 | Cada temporada tiene dos mitades y una ventana antes de cada una. | Antes la última ventana caía después de la última temporada: renovar a todo el plantel ahí sumaba fitness sin jugar un partido. |
| D3 | Los salarios se pagan de la caja en cada media temporada. | Un contrato caro tiene que costar plata, no solo ocupar lugar bajo el tope. |
| D4 | Renovar cuesta: salario del nivel actual, +10% mínimo y prima de firma (mitad del valor creado por alargar el contrato). | Sin esto, renovar era fabricar patrimonio gratis. |
| D5 | El patrimonio final es neto: valor de mercado menos sobrecosto del contrato. | Un contrato largo y caro firmado al final es una deuda, no un activo. |
| D6 | La cesión dura hasta fin de temporada y el jugador vuelve, con los minutos que jugó afuera. | Es para lo que sirve ceder a un juvenil. |
| D7 | Clubes rivales reactivos: ofertan y ejecutan cláusulas, fichan candidatos, renuevan a los suyos. | Hace que la cláusula sea una decisión real. (Era "extensión no comprometida" en el informe; está hecha.) |
| D8 | La fuerza del equipo usa el banco (los 7 siguientes al once), no la media del plantel. | Con la media, liberar suplentes malos subía los puntos. |
| D9 | β = 0,05, calibrado por barrido (`SensibilidadBeta`). | Entre 0,03 y 0,10 los planes rinden parecido. |
| D10 | Préstamos entrantes con cargo y tope de 3 simultáneos. | — |
| D11 | Mínimos por puesto escalados por instancia. | Que una micro-instancia no arrastre una penalización constante. |

## Reglas metodológicas (Juan)

| # | regla |
|---|---|
| M1 | Escenarios comunes: el azar se sortea por (jugador, período, evento); toda la generación se evalúa sobre los mismos escenarios, que rotan entre generaciones. |
| M2 | Reevaluación de todo sobreviviente cuando rotan los escenarios. |
| M3 | Selección final en dos etapas: se remiden los 10 mejores candidatos con más réplicas sobre los mismos escenarios. |
| M4 | Revaluación final con N=200 y semillas que la búsqueda nunca usa. |
| M5 | Presupuesto en evaluaciones de fitness, no en generaciones. |
| M6 | Instancias de configuración separadas de las de evaluación. |
| M7 | Friedman y Wilcoxon bloqueados por semilla. |
| M8 | Con menos de 10 muestras no se concluye normalidad: tests no paramétricos. |

---

## Decisiones de implementación (Alen, desde el 22/11/2026)

### D-A1 · jMetal 5.10 como framework de los algoritmos
**Fecha:** 22/11/2026 · **Estado:** vigente

**Qué:** los algoritmos se implementan sobre **jMetal 5.10**, que es la
versión que enseña el curso (Tema 6, "Implementación de AEs usando jMetal").
Hay un adaptador en `src/ae/plantel/jmetal/`:
`ProblemaPlantel`/`ProblemaPlantelMO` (interfaz `Problem`), `SolucionPlantel`
(`Solution`), operadores envueltos (`CrossoverOperator`, `MutationOperator`,
`SelectionOperator`), el AE generacional sobre el esqueleto
`AbstractGeneticAlgorithm` y NSGA-II sobre la clase `NSGAII` de jMetal.

**Qué no cambia:** el simulador, el decodificador, la reparación y los
operadores de Juan siguen siendo el código del problema. jMetal pone el
motor; el problema es nuestro. Es exactamente la separación
metaheurística/problema que pide el Tema 6.

**Qué pasa con `AlgoritmoEvolutivo.java`:** queda como **implementación de
referencia**. Sirve para validar la de jMetal (mismas semillas, misma
distribución de resultados) y conserva las variantes ya comparadas (gap,
sharing, CHC, memético). Los experimentos finales se corren con la versión
jMetal.

**Alternativas descartadas:** (a) reescribir todo en jMetal y borrar el AE
propio: se perdía una implementación validada y la comparación de
variantes; (b) usar jMetal solo para NSGA-II: no cumple el pedido de que la
biblioteca del proyecto sea jMetal.

### D-A2 · Los operadores usan el `Aleatorio` de la corrida, no `JMetalRandom`
**Fecha:** 22/11/2026 · **Estado:** vigente

`JMetalRandom` es un singleton global, no sincronizado. `Experimentos` corre
ejecuciones en paralelo (maestro-esclavo, Tema 11): con el singleton, el
resultado de cada corrida dependería del orden en que los hilos piden
números, y una semilla dejaría de reproducir su corrida. Por eso los
operadores implementan las interfaces de jMetal pero reciben el `Aleatorio`
(SplitMix64) de su corrida. Por la misma razón la selección por torneo es
propia (`TorneoPlantel`) y no la `NaryTournamentSelection` de jMetal, que
usa el singleton.

### D-A3 · La población inicial NO se siembra con soluciones golosas
**Fecha:** 22/11/2026 · **Estado:** vigente · **Afecta al informe**

El informe inicial (§III-C) dice "sembrando la población con soluciones
golosas y con el plan statu quo". El código siembra con el statu quo y
perturbaciones suyas (30%), y el resto es aleatorio sesgado a la inacción.
Se mantiene así porque construir el plan goloso cuesta unas **5.700
evaluaciones** (`GreedyMiope`), el 71% de un presupuesto de 8.000 y el 14% de
uno de 40.000, y el greedy además es una de las referencias contra las que se
compara el AE: sembrarlo mezclaría las dos cosas. **En el informe final hay
que decirlo así.**

### D-A4 · Build con Maven (`pom.xml`) y `lib/` fuera del repo
**Fecha:** 22/11/2026 · **Estado:** vigente

`pom.xml` declara `jmetal-core` y `jmetal-algorithm` 5.10 desde Maven
Central y usa `src/` como carpeta de fuentes, así que no hubo que mover
ningún archivo de Juan. `compilar.sh` usa Maven si está disponible; si no,
los `.jar` que haya en `lib/` (que no se commitea). En el entorno del Claude
de Alen Maven Central está bloqueado: ahí los `.jar` se compilaron desde el
código fuente de jMetal en el tag `jmetal-5.10` (GitHub) más Apache Commons
en las versiones que pide jMetal. La consigna prohíbe incluir el código del
framework, por eso nada de eso entra al repo.

**Pendiente de verificar:** `mvn package` en una máquina con acceso a Maven
Central (ver PENDIENTES).

### D-A5 · Shapiro-Wilk como test de normalidad principal; Holm como post-hoc
**Fecha:** 22/11/2026 · **Estado:** vigente · **Reemplaza en parte a la nota del README "el test de normalidad es Lilliefors"**

El informe inicial promete Shapiro-Wilk y las diapositivas de StatTests lo
usan; el Tema 12 acepta Kolmogorov-Smirnov, Lilliefors o Anderson-Darling.
Se implementa Shapiro-Wilk (algoritmo de Royston, validado contra scipy) y
pasa a ser el principal. Lilliefors queda disponible. Así no hay que
corregir esa promesa en el informe final.

Para comparaciones múltiples se usa **Holm** (StatTests, lámina 22) en lugar
de Bonferroni: controla el mismo error de familia y es más potente.

Wilcoxon pasa a usar el **p exacto** cuando hay 25 pares o menos sin empates;
con más, la aproximación normal. Con n = 10 la aproximación normal daba
p = 0,025 donde el exacto da 0,0098 (ver VALIDACION §5).

### D-A6 · Formulación multiobjetivo: f₁ y f₂ elegidos para que el mono-objetivo sea un caso particular
**Fecha:** 22/11/2026 · **Estado:** vigente

- f₁ = E[Σₜ γᵗ Rₜ] − E[penalización] (utilidad deportiva, a maximizar);
- f₂ = E[costo neto] = E[gasto en pases + salarios − patrimonio en jugadores] (a minimizar).

Como la caja final es una constante menos el costo neto, el fitness
mono-objetivo cumple, sin contar el término de robustez,
**f(x) + ρ·σ̂(x) = f₁(x) − β·f₂(x) + C**, con C constante de la instancia.
Consecuencias: (a) el plan del AE mono-objetivo tiene que caer cerca del
frente de NSGA-II, en el punto donde la pendiente del frente es β; (b) esa
identidad sirve como test del adaptador multiobjetivo. Se descuentan los
puntos con γ para que la identidad sea exacta.

### D-A7 · "Localizaciones candidatas" no forma parte de este problema
**Fecha:** 22/11/2026 · **Estado:** vigente

El término aparece en uno de los proyectos de ejemplo de años anteriores
(oleoducto de costo mínimo, Bello-Picca), no en el nuestro. Lo más parecido
en nuestro problema es el **conjunto de candidatos del mercado** (jugadores
que se pueden incorporar). No hay nada que implementar por ese lado.

### D-A8 · Flujo de trabajo en Git
**Fecha:** 22/11/2026 · **Estado:** vigente

Ramas por persona (`alen/...`), un commit por módulo validado, PR a `main`
para que el otro revise. Commits de Alen sin línea de co-autoría (pedido
explícito).

### D-A9 · NSGA-II se compara contra "agregación con barrido de β"
**Fecha:** 22/11/2026 · **Estado:** vigente · **No estaba en el informe inicial**

El Tema 12 pide comparar las técnicas multiobjetivo por métrica, contra el
frente de referencia. El informe no decía contra qué se compara NSGA-II. Se
eligió el enfoque **no basado en Pareto** del Tema 10 ("agregación o
combinación lineal de objetivos"): el mismo AE mono-objetivo (`AGPlantel`)
corrido con β ∈ {0; 0,02; 0,05; 0,10; 0,20; 0,50}, cada uno con 1/6 del
presupuesto, y su conjunto no dominado como frente. Es lo que haría quien no
usa un MOEA, y por D-A6 es justo: el mono-objetivo ES una agregación lineal
de f₁ y f₂. A igual presupuesto total se mide si NSGA-II aporta. Programa:
`jmetal/ExperimentoMO`.

### D-A10 · Reevaluación de padres en NSGA-II y revaluación del frente final
**Fecha:** 22/11/2026 · **Estado:** vigente

En NSGA-II el reemplazo es (μ+λ): padres e hijos compiten. Con escenarios que
rotan (M1), los padres se reevalúan sobre los escenarios de los hijos antes
de competir (M2); cuesta P evaluaciones por generación, que se descuentan del
presupuesto (cada generación cuesta 2P). El frente que se reporta es el
conjunto no dominado de la población final **revaluado con N = 200 sobre los
escenarios de validación**, otra vez filtrado por no dominancia, y sin puntos
repetidos: dos planes que difieren solo en genes que no se expresan (por
ejemplo, la prioridad de un candidato que no se compra) dan los mismos
objetivos y cuentan como un solo punto.

### D-A11 · Límite de tiempo de reloj apagado por defecto
**Fecha:** 22/11/2026 · **Estado:** vigente

El informe promete "40.000 evaluaciones más un límite de tiempo de reloj"
(§III-C). Está implementado (`Config.segundosMax`) en el AE de referencia, en
`AGPlantel` y en `NSGAIIPlantel`, pero vale 0 (apagado) por defecto: con
límite de tiempo, cuántas generaciones entran depende de la máquina y una
semilla deja de reproducir su corrida. Se usa solo como red de seguridad en
ClusterUY. El criterio que manda es el de evaluaciones.

### D-A12 · Cómo se decide la equivalencia entre el AE propio y el de jMetal
**Fecha:** 22/11/2026 · **Estado:** vigente

Con la misma semilla las dos implementaciones arrancan de la misma población
y usan los mismos escenarios, pero consumen el azar en otro orden (jMetal
hace todas las selecciones de la generación antes de cruzar), así que no dan
el mismo plan. Se valida en distribución, con el protocolo del curso
(StatTests lámina 9): Shapiro-Wilk en las dos muestras; si son normales, t
de Student (Welch); si no, Mann-Whitney; además el criterio del Tema 12
(|diferencia de medias| ≤ máx. desvíos). Se exige además que la misma
semilla reproduzca la misma corrida aunque se corra en paralelo, y que las
dos gasten exactamente las mismas evaluaciones y generaciones.

### D-A14 · Modelo de islas sincrónico, con escenarios comunes entre islas
**Fecha:** 22/11/2026 · **Estado:** vigente

`jmetal/IslasPlantel`: la población total se reparte en k islas (por defecto
4), cada una un AE generacional con elitismo y los operadores del proyecto;
cada 5 generaciones cada isla manda copias de sus 2 mejores a la siguiente
del anillo, donde reemplazan a las 2 peores (Tema 11). Tres decisiones:
(a) **sincrónico**: todas las islas terminan la generación antes de migrar,
así una semilla da el mismo resultado con 1 o k hilos y el speedup mide solo
tiempo; (b) **los mismos escenarios en todas las islas** en cada generación,
para que un migrante llegue con un fitness comparable al de los locales;
(c) **presupuesto global** sumando todas las islas, con la misma selección
final en dos etapas. jMetal 5.10 no trae modelo de islas: se arma con sus
componentes y un ciclo propio.

### D-A15 · Para medir GAP, el AE optimiza sobre los escenarios fijos de la enumeración
**Fecha:** 22/11/2026 · **Estado:** vigente · **Corrige un sesgo de la medición anterior**

El "óptimo exacto" es el plan que maximiza el promedio sobre N = 30
escenarios **fijos** (semilla 424.242). El AE normal busca con escenarios que
rotan y elige su plan final con otros: optimiza el valor esperado, que no es
lo mismo. Medido así, el GAP mezclaba dos objetivos, y se notaba: con más
presupuesto el AE **no** llegaba más seguido al óptimo (21/30 con 500
evaluaciones, 18/30 con 8.000 en la micro-instancia 55). Con
`Config.semillaEscenariosFija` el AE busca y hace la selección final sobre
esos mismos 30 escenarios, sin rotar; ahí los aciertos crecen con el
presupuesto como corresponde (25 → 30 de 30). Solo se usa para el GAP; el
resto de los experimentos sigue con escenarios rotativos.

Resultado (`jmetal/GapMicroInstancias`, 3 micro-instancias de 5 jugadores
con el statu quo a 5,7%, 13,8% y 5,9% del óptimo, 30 corridas): con 8.000
evaluaciones (7,4% del espacio de 107.811 planes) el AE encuentra el óptimo
exacto en **30 de 30** corridas en las tres; con 4.000, en las micro 56 y 57.

Nota para leer `validar.sh` (paso 7): ahí `OptimoExacto` sigue corriendo el
AE con escenarios rotativos, como Juan lo diseñó, y la línea "AE jMetal"
puede decir "óptimo en 0 de 10" con un GAP de 0,03%. No es una falla: es
exactamente el sesgo que describe esta decisión. El número que va al informe
es el de `GapMicroInstancias`.

### D-A13 · Número de réplicas: N = 10 en los experimentos finales, con presupuesto equivalente en simulaciones
**Fecha:** 22/11/2026 · **Estado:** vigente · **Afecta al informe** (§IV-A lo anticipaba: "se estudiará aparte el número de réplicas N")

Estudio en `jmetal/EstudioReplicas` (salida en `resultados/replicas_demo.txt`):

1. **Ordenamiento** contra una referencia de N = 1.000. Con planes diversos
   (población inicial) hasta N = 5 ordena casi perfecto (ρ = 0,99); con planes
   convergidos (población final), el orden se degrada al bajar N
   (N = 30: ρ = 0,95, τ = 0,83; N = 10: ρ = 0,86, τ = 0,70; N = 5: ρ = 0,81,
   τ = 0,65). El criterio fijado de antemano (ρ ≥ 0,9 en diversos y τ ≥ 0,5
   en convergidos) resultó demasiado laxo: lo cumple N = 5. Con eso solo no
   se puede decidir, porque un N chico ordena peor pero deja hacer más
   generaciones.
2. **Lo que decide: el AE a igual costo** (mismo total de simulaciones,
   evaluaciones × N constante), Wilcoxon pareado por semilla contra N = 30,
   con Holm:

   | N | 240.000 simulaciones (10 semillas) | 720.000 simulaciones (6 semillas) |
   |---|---|---|
   | 5 | 234,48 (p Holm 0,0078) | 237,12 (0,125) |
   | 10 | 233,98 (0,0078) | 236,57 (0,125) |
   | 20 | 232,14 (0,0078) | 235,27 (0,125) |
   | **30 (actual)** | 229,53 | 234,02 |
   | 50 | 228,35 (0,11) | 233,74 (0,69) |

   Con 240.000 simulaciones N = 5, 10 y 20 son significativamente mejores que
   N = 30. Con 720.000 la dirección se mantiene en las 6 semillas, pero con
   n = 6 y 4 comparaciones el menor p de Holm posible es 0,125.

**Aprobada por Juan el 23/11/2026 (PENDIENTES P26)**, después de verificarla
con el AE original (+5,56 y +3,15 a igual costo, p Holm 0,0117).

**Decisión:** los experimentos finales usan **N = 10 con 120.000
evaluaciones** (`Config.protocoloFinal()`), que son las **mismas 1,2 millones
de simulaciones** que las 40.000 evaluaciones con N = 30 del informe inicial:
igual costo de cómputo y planes mejores. Se elige 10 y no 5 porque la
diferencia entre ambos es chica (≈ 0,5) y no testeada, y N = 10 ordena mejor
las poblaciones convergidas, que es donde el ruido más pesa. La selección
final sigue remidiendo con 100 réplicas.

**Qué no cambia:** los valores por defecto de `Config` (N = 30) y todos los
números de referencia de `validar.sh`. Usan el protocolo final
`CompararReferencias`, `ExperimentoMO`, `ExperimentoIslas` y `Experimentos`
(el factorial).

**Juan:** si no estás de acuerdo, se revierte cambiando `N_FINAL` y
`EVALUACIONES_FINALES` en `AlgoritmoEvolutivo` (y se agrega una D-A que lo
explique).

### D-A16 · La instancia "demo" es eval-chica: los estudios de diseño se repiten en las de configuración
**Fecha:** 22/11/2026 · **Estado:** vigente · **Corrige un sesgo metodológico**

`GeneradorInstancias.generar("demo", 25, 50, 101)` produce exactamente la
misma instancia que `eval-chica` (mismo generador, misma semilla). Sobre ella
se hicieron la comparación de variantes (Juan), la de operadores y el estudio
de N. El Tema 12 pide ajustar sobre instancias de configuración distintas de
las de evaluación, para no sesgar. Por eso se repitieron el estudio de N y el
de operadores sobre **conf-joven** y **conf-veterano**
(`resultados/diseno_conf-*.txt`):

| estudio | conf-joven | conf-veterano | ¿se sostiene la decisión? |
|---|---|---|---|
| N a igual costo (240.000 simulaciones) | N=10: 226,82 vs N=30: 222,38 (p Holm 0,0078) | N=10: 220,36 vs N=30: 216,19 (p Holm 0,0078) | sí, D-A13 |
| proporcional vs torneo | −3,80 (p Holm < 0,0001) | −2,03 (p Holm 0,0001) | sí, se queda torneo |
| bloques vs uniforme | −3,49 (p Holm < 0,0001) | −1,97 (p Holm 0,0001) | sí, se queda uniforme (en demo no difería; acá es peor) |

Las decisiones de diseño valen en instancias que la evaluación no usa.

**Actualización 23/11/2026 (Juan):** la comparación de variantes (gap,
estado estacionario, sharing, CHC, memético) también se repitió sobre
conf-joven y conf-veterano (P25, `resultados/variantes_configuracion.txt`).
Ninguna supera a la base; sharing y memético quedan neutros y el resto es
peor. Se mantiene el AE generacional con elitismo.

### D-J1 · N creciente: implementado, evaluado y no adoptado
**Fecha:** 23/11/2026 · **Autor:** Juan · **Estado:** vigente · **Afecta al informe** (§III-B prometía "aumento progresivo de N")

Se implementó en el AE original: `Config.replicasMin`/`replicasMax` hacen
crecer N linealmente con la fracción del presupuesto de simulaciones
consumida; toda una generación se evalúa con el mismo N, así que los
escenarios comunes se mantienen. Con `replicasMax = 0` (por defecto) el AE se
comporta exactamente como antes (verificado: reproduce las filas guardadas).

Se comparó un N de 5 a 30 contra N = 10 fijo, a igual total de simulaciones,
en las dos instancias de configuración (10 semillas, Wilcoxon pareado + Holm):
−0,67 (p 0,084) y −0,35 (p 0,28), sin diferencia. Ambos le ganan a N = 30
fijo.

**Decisión:** se mantiene **N = 10 fijo** (D-A13), por ser más simple e igual
de bueno. No se porta a `AGPlantel`. En el informe final: el N creciente se
probó y no mejora a N = 10 fijo, porque la selección final en dos etapas ya
resuelve el ruido de la etapa convergida.

**Alternativas no probadas:** otros calendarios (por ejemplo 10 → 30 o
5 → 15). No se probaron a propósito, para no elegir un calendario a fuerza de
mirar muchos sobre las mismas instancias.

### D-J2 · Calibración del simulador con datos reales
**Fecha:** 23/11/2026 · **Autor:** Juan · **Estado:** vigente · **Afecta al informe** (§IV-A: instancias y parámetros con datos públicos)

Fuentes: *EA Sports FC 24 Complete Player Dataset* (Kaggle, una foto por
edición de FIFA 15 a FC 24) y *Football Data from Transfermarkt* (Kaggle).
Script reproducible: `herramientas/calibrar.py <carpeta>`. Resultados en
`Parametros.calibrados()`; los valores por defecto quedan para las
instancias sintéticas, así que ningún número de referencia cambia.

| parte | contra qué | resultado |
|---|---|---|
| salario de referencia | FIFA 21, 5 grandes ligas, nivel ≥ 65 (n = 2.968) | base 0,0888, escala 0,1727 (R² 0,60). El modelo original subestimaba mucho a las estrellas: nivel 90 cobraba 3,3M y el dato es 13M |
| valor de mercado (nivel, edad) | FIFA 21, contratos de 3+ años (n = 1.569) | R² 0,95 (los coeficientes originales daban 0,57 sobre los mismos datos) |
| factor contractual | **validado** con 1.617 traspasos reales de Transfermarkt | precio/valor con 1 año de contrato 0,67 (modelo 0,63); con 2 años 0,79 (modelo 0,82) |
| evolución del nivel | 35.146 cambios de rating entre ediciones con minutos de liga de Transfermarkt | crecimientoMax 9,30, declive mucho más suave (−0,9 en tres temporadas desde los 30, contra −3,8), ruido 2,25 |

Lesiones y parámetros de los rivales siguen siendo supuestos (no hay datos).

### D-J3 · El efecto de los minutos sobre el nivel no es identificable con estos datos
**Fecha:** 23/11/2026 · **Autor:** Juan · **Estado:** vigente · **Afecta al informe**

Los minutos se asocian con la mejora del rating a todas las edades (un
jugador de 30 con pocos minutos baja 1,1; con muchos sube 0,6), pero la
asociación no es causal: el rating de septiembre resume la temporada que
terminó, así que una buena temporada sube a la vez minutos y rating
(simultaneidad). Con efectos fijos por jugador el efecto no baja sino que
sube (+3,9 → +5,0 en jóvenes), lo que confirma que el problema no es la
heterogeneidad entre jugadores. Por eso:
- se calibran con datos el nivel medio de crecimiento y declive por edad y el ruido;
- la sensibilidad a los minutos (`minutosMediaSaturacion` = 1200) queda como supuesto;
- se mantiene que el declive no depende de los minutos (D1): cierra el agujero
  de "congelar veteranos en el banco" y los datos no dan un efecto causal que lo contradiga.

### D-J4 · Instancias reales: FC Barcelona 2020-2023 y su plan real
**Fecha:** 23/11/2026 · **Autor:** Juan · **Estado:** vigente · **Afecta al informe** (§IV-A, §IV-B(1))

Script: `herramientas/preparar_instancias.py <carpeta>` → `instancias/`.
- **Punto de partida:** foto de FIFA 21 (23/09/2020). Horizonte 2020-21,
  2021-22 y 2022-23. Caso elegido porque el modelo tiene que poder
  representarlo: Messi se va libre en 2021 al no poder renovar por el tope
  salarial, y en 2022 el club ficha con dinero que no tenía.
- **Plantel propio (25):** el primer equipo de FIFA 21 más Pedri y Araujo.
- **Mercado:** los 16 fichajes reales que están en FIFA 21 más una muestra
  aleatoria con semilla fija de nivel 74 a 92 y hasta 33 años de siete ligas;
  instancias anidadas de 50, 150 y 400 candidatos.
- **Economía:** tope salarial = masa del plantel × 1,10 (el club estaba en el
  límite); presupuesto de traspasos 150M.
- **Plan real** (`plan_real_barcelona.csv`): 37 operaciones reconstruidas de
  Transfermarkt y de las fotos de FIFA 22 y 23, cada una con su justificación.
  Condiciones contractuales leídas de la foto posterior. Regla para jugadores
  con varias operaciones: un cedido que no volvió a jugar cuenta como venta o
  liberación en la ventana en que salió.
- **Limitaciones:** los canteranos que subieron después (Gavi, Balde, Nico
  González) no están en FIFA 21 y no los puede usar ni el plan real ni el AE;
  el plan real se evalúa con los precios y la incertidumbre del simulador, no
  con los montos reales.

### D-J5 · Correcciones del modelo de mercado que aparecieron con los datos reales
**Fecha:** 23/11/2026 · **Autor:** Juan · **Estado:** vigente

Al evaluar el plan real se caían 12 de 37 operaciones. El diagnóstico
(`Evaluador.traza`, `EvaluarCasoReal`) mostró cuatro problemas del modelo,
corregidos detrás de parámetros que solo activa `Parametros.calibrados()`:
1. **El multiplicador salarial se aplicaba sobre el salario actual** del
   jugador y no sobre el de referencia de su nivel, como define el informe
   (§I-A). Fichar a quien viene de una liga que paga poco era casi imposible
   (Dest: 90% de rechazo). → `ofertaSobreReferencia`.
2. **Un agente libre pagaba traspaso** (el 45% de su valor). → `agenteLibreSinTraspaso`.
3. **Una oferta a precio de mercado se aceptaba solo el 50% de las veces.**
   Se centra la aceptación en el percentil 10 del cociente salario real /
   referencia de 4.952 fichajes reales (0,61): una oferta a precio de mercado
   se acepta el 91% de las veces. → `umbralAceptacion`.
4. **El riesgo de que otro club fiche al candidato crecía con el nivel**; en
   los datos baja (35% anual a nivel 72, 12% a nivel 92) y el jugador sigue en
   el mercado con contrato nuevo. → `fichajeExternoReubica`.
Además, Riqui Puig renueva en V2 y no en V3 (su contrato vencía antes).
Después de las correcciones se caen 4,4 operaciones, por motivos legítimos:
sobre todo falta de caja para el mercado del verano de 2022, que el club real
financió con las "palancas" que el modelo no tiene.

### D-J6 · beta = 0,01 para la escala monetaria real
**Fecha:** 23/11/2026 · **Autor:** Juan · **Estado:** vigente · **Afecta al informe**

β = 0,05 se calibró sobre la escala sintética. Con valores reales el AE
volvía a cambiar partidos por patrimonio (+25% de fitness sobre el plan real,
pero con menos puntos). Barrido sobre barcelona-2020-chica (12.000
evaluaciones, 2 semillas): con β = 0 el AE gasta todo (patrimonio 324M);
con 0,005 y 0,01 domina al plan real en puntos y en patrimonio; con 0,02 ya
cede puntos; con 0,05 pierde frente al plan real en puntos. Se fija 0,01 en
`Parametros.calibrados()`, que además coincide con el argumento de escala
(valores reales unas cinco veces los sintéticos: 0,05 / 5).

### D-A17 · El barrido de β de la agregación es relativo al β del modelo
**Fecha:** 24/11/2026 · **Autor:** Alen · **Estado:** vigente

Con los parámetros calibrados β = 0,01 (D-J6), y el barrido fijo de D-A9
(hasta 0,50) habría puesto cuatro de los seis puntos en la zona donde el AE
solo junta patrimonio. El barrido pasa a ser β × {0; 0,4; 1; 2; 4; 10}: con
el β sintético (0,05) da exactamente el barrido original, así que los
resultados sintéticos no cambian; con el calibrado da {0; 0,004; 0,01; 0,02;
0,04; 0,10}, que cubre el mismo rango relativo en la escala monetaria real y
coincide con el barrido que hizo Juan para elegir β (0 a 0,05).

### D-A18 · Protocolo final sobre las instancias reales (P29)
**Fecha:** 24/11/2026 · **Autor:** Alen · **Estado:** vigente

`CompararReferencias` y `ExperimentoMO` aceptan las instancias reales por
nombre (`barcelona-2020-{chica,media,grande}`) y en ese caso usan
`Parametros.calibrados()`. Tres cambios para que la comparación sea justa:

1. **El greedy usa los parámetros de la instancia.** Antes tenía fijos los
   por defecto (`GreedyMiope.P`): sobre las reales habría optimizado otro
   modelo. Se agregó `construir(inst, evals, p)`; la firma vieja sigue igual,
   así que los números de referencia no cambian.
2. **El plan real es una referencia más**: entra en el % de mejora, el
   Wilcoxon de una muestra, el Friedman con Holm y la robustez con 10.000
   simulaciones. Además se reporta el desglose f₁ (utilidad deportiva) y f₂
   (costo neto) y en cuántas corridas el plan del AE **domina** al plan real
   (más f₁ y menos f₂), que es lo que permite decir que el AE no gana solo
   por cambiar puntos por plata.
3. **En el multiobjetivo, el plan real y el statu quo se ubican respecto de
   los frentes** (dominado o no por NSGA-II, por la agregación y por el frente
   de referencia).

Validación: la identidad de D-A6 sigue siendo exacta con los parámetros
calibrados y las correcciones de mercado de D-J5 (error 1e-13 en
barcelona-2020-chica y -grande, incluido el plan real); quedó como chequeo
en `ValidarMO`.

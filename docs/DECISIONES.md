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

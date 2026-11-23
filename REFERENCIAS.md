# Referencias: informe → código

Dónde está implementada cada parte del informe inicial
(`docs/Informe_Inicial_v2.pdf`). En el código, cada una lleva la etiqueta
`[Informe §...]` en su javadoc, así que también se puede buscar con
`grep -rn "Informe §III-B" src`.

Estado: ✅ implementado · 🟨 parcial · ⬜ pendiente (ver `docs/PENDIENTES.md`)

Todas las rutas son relativas a `src/ae/plantel/`.

## I. Descripción del problema

| sección del informe | archivo · clase/método | estado | notas |
|---|---|---|---|
| §I-A dominio de acciones según pertenencia | `Modelo.java` · `Accion`, `JugadorBase.accionesValidas` | ✅ | propios 0..4, mercado 0..2 |
| §I-A duración, salario y cláusula como decisiones | `Cromosoma.java` · campos `duracion`, `salario`, `clausula` | ✅ | |
| §I-A compromiso cláusula-salario | `Evaluador.java` · `exigenciaPorClausula` | ✅ | |
| §I-A una sola acción por jugador; sin canje ni recompra | `Cromosoma.java` (un gen por jugador) | ✅ | |
| §I-B ec. (1) evolución del nivel | `Evolucion.java` · `evolucionarNivel`, `crecimientoPorEdad`, `factorMinutos`, `declivePorEdad` | ✅ | el potencial actúa como techo (agregar al informe final) |
| §I-B lesiones (logística) | `Evolucion.java` · `probabilidadLesion`, `sortearLesionMitad` | ✅ | |
| §I-B valor de mercado | `Evolucion.java` · `valorMercado`, `valorNeto` | ✅ | |
| §I-C restricciones (presupuesto, tope, cupo, mínimos, cedidos, continuidad) | `Modelo.Instancia`; `Evaluador.java` · `aplicarVentana`, `reparar`, `deficitPuestos` | ✅ | |
| §I-C ec. (2) utilidad esperada | `Evaluador.java` · `simular` | ✅ | V₃ = caja + valor neto |
| §I-C γ, β | `Parametros.java` · `gamma`, `beta` | ✅ | β calibrado en `SensibilidadBeta` |

## II. Justificación del AE

| sección del informe | archivo · clase/método | estado | notas |
|---|---|---|---|
| §II-A NP-dificultad (reducción a mochila) | — | (argumento teórico) | |
| §II-A epistasis | `ValidarAgujeros.java` · S8 | ✅ (evidencia empírica) | con el plantel lleno, comprar exige vender |
| §II-B tamaño del espacio (≈ 10²⁹³) | `OptimoExacto.java` (cabecera) | ✅ | |
| §II-B objetivo de caja negra ruidoso | `Evaluador.java` · `simular` | ✅ | |

## III. Estrategia de resolución

| sección del informe | archivo · clase/método | estado | notas |
|---|---|---|---|
| §III-A representación (tupla a, w, d, s, c, μ) y Cuadro I | `Cromosoma.java` · clase, `describir` | ✅ | |
| §III-A representación como `Solution` de jMetal | `jmetal/Gen.java` (variable = tupla del gen), `jmetal/SolucionPlantel.java` | ✅ | D-A1 |
| §III-A decodificador cronológico | `Evaluador.java` · `simular`, `aplicarVentana` | ✅ | |
| §III-A reparación (cupo y tope) | `Evaluador.java` · `reparar` | ✅ | proxy nivel/salario |
| §III-A penalización (mínimos por puesto) | `Evaluador.java` · `deficitPuestos`, `LAMBDA_*` | ✅ | |
| §III-B simulador pasos (i)–(iv) | `Evaluador.java` · `jugarPeriodo`, `cerrarTemporada`, `aplicarVentana`; `Temporada.java` | ✅ | |
| §III-B ec. (3) fitness | `Evaluador.java` · `evaluar` | ✅ | |
| §III-B problema como `Problem` de jMetal | `jmetal/ProblemaPlantel.java` · `evaluate`, `rotarEscenarios` | ✅ | objetivo = −f(x) (jMetal minimiza) |
| §III-B common random numbers | `Escenario.java`, `Aleatorio.java`; `AlgoritmoEvolutivo.Corrida.rotarEscenarios`; `jmetal/AGPlantel.reproduction`, `jmetal/NSGAIIPlantel.reproduction` | ✅ | |
| §III-B reevaluación de la élite | `AlgoritmoEvolutivo.java` · `bucleGeneracional`; `jmetal/AGPlantel.replacement`; `jmetal/NSGAIIPlantel.replacement` (padres) | ✅ | |
| §III-B N creciente | — | reemplazado | por la selección final en dos etapas (M3) |
| §III-C inicialización | `Operadores.java` · `poblacionInicial` (usada también por `AGPlantel` y `NSGAIIPlantel`) | 🟨 | sin siembra golosa (D-A3) |
| §III-C selección por torneo de tamaño 3 | `Operadores.java` · `torneo`; `jmetal/OperadoresJMetal.Torneo` | ✅ | |
| §III-C selección proporcional con escalado | `jmetal/OperadoresJMetal.ProporcionalEscalada` (ruleta con punteros equiespaciados); `Config.seleccion` | ✅ | comparada en `jmetal/CompararOperadores`: peor que torneo |
| §III-C cruzamiento uniforme a nivel de gen + reales | `Operadores.java` · `cruzar`; `jmetal/OperadoresJMetal.Cruzamiento` | ✅ | aritmético en lugar de BLX-α |
| §III-C cruzamiento por bloques posicionales | `Operadores.cruzarPorBloques`; `Config.cruce` | ✅ | comparado en `jmetal/CompararOperadores`: sin diferencia significativa |
| §III-C mutación (cinco operadores) | `Operadores.java` · `mutar`, `intercambiar`; `jmetal/OperadoresJMetal.Mutacion` | ✅ | |
| §III-C reemplazo generacional con elitismo | `jmetal/AGPlantel.java` (motor de los experimentos); `AlgoritmoEvolutivo.java` · `bucleGeneracional` (referencia) | ✅ | equivalencia validada (`jmetal/ValidarEquivalenciaJMetal`) |
| §III-B aumento progresivo de N | `AlgoritmoEvolutivo.Config.replicasMin`/`replicasMax`; `CompararVariantes` (variante `ncreciente`) | ✅ | evaluado y no adoptado: empata con N = 10 fijo (D-J1) |
| §III-C comparación con estado estacionario | `AlgoritmoEvolutivo.java` (gap = 2/P); `CompararVariantes.java` | ✅ | repetida en instancias de configuración (P25) |
| §III-C parada: 40.000 evaluaciones | `AlgoritmoEvolutivo.Config.maxEvaluaciones`; `jmetal/AGPlantel.isStoppingConditionReached` | ✅ | |
| §III-C parada: límite de tiempo de reloj | `AlgoritmoEvolutivo.Config.segundosMax`; `AlgoritmoEvolutivo.Corrida.vencido`; `jmetal/AGPlantel` y `jmetal/NSGAIIPlantel` · `isStoppingConditionReached` | ✅ | apagado por defecto (rompe la reproducibilidad) |
| §III-C diversidad (sharing) | `AlgoritmoEvolutivo.java` · `compartido`; `Operadores.distancia` | ✅ | |
| §III-C escalado de fitness | `Operadores.escalar` (truncamiento sigma + escalado lineal, Tema 3) | ✅ | validado en `jmetal/ValidarOperadores` |
| §III-C islas con migración en anillo | `jmetal/IslasPlantel.java` | ✅ | sincrónico, escenarios comunes entre islas (D-A14) |
| §III-C NSGA-II (f₁ rendimiento, f₂ costo neto) | `jmetal/ProblemaPlantelMO.java`, `jmetal/NSGAIIPlantel.java` (clase `NSGAII` de jMetal) | ✅ | D-A6; validado en `jmetal/ValidarMO` |
| §III-C extensión: clubes rivales reactivos | `Evaluador.java` · `moverOtrosClubes` | ✅ | |

## IV. Evaluación experimental

| sección del informe | archivo · clase/método | estado | notas |
|---|---|---|---|
| §IV-A tres instancias (50, 150, 400 candidatos) | `GeneradorInstancias.java` · `evaluacion` | 🟨 | sintéticas; faltan datos reales (P17) |
| §IV-A instancias de configuración separadas | `GeneradorInstancias.java` · `configuracion` | ✅ | |
| §IV-A diseño factorial 3×3×3, 30 corridas | `Experimentos.java` | 🟨 | hecho; falta correrlo (P19) |
| §IV-A Shapiro-Wilk → ANOVA / Kruskal-Wallis, Friedman | `Estadistica.java` · `shapiroWilk`, `anova`, `kruskalWallis`, `friedman`; usado en `Experimentos.java` | ✅ | Lilliefors queda como control (D-A5) |
| §IV-A estudio del número de réplicas N | `jmetal/EstudioReplicas.java` | ✅ | D-A13 |
| §IV-B(1) plan real del club | — | ⬜ | P18 |
| §IV-B(2) óptimo exacto en micro-instancias | `OptimoExacto.java` · `enumerar`; `Operadores.proyectarAGrilla`; `jmetal/GapMicroInstancias.java` (3 micro-instancias de 5 jugadores, 30 corridas) | ✅ | escenarios fijos para el GAP (D-A15) |
| §IV-B(3) greedy miope | `GreedyMiope.java` | ✅ | |
| §IV-B(4) búsqueda aleatoria con igual presupuesto | `AlgoritmoEvolutivo.java` · `busquedaAleatoria` | ✅ | |
| §IV-B comparación NSGA-II vs agregación (Tema 10) | `jmetal/ExperimentoMO.java` | ✅ | no estaba en el informe: ver D-A9 |
| §IV-B(5) statu quo | `Cromosoma.java` · `statuQuo` | ✅ | |
| §IV-B % de mejora y GAP | `jmetal/CompararReferencias.java` (% de mejora, R corridas); `OptimoExacto` (GAP) | ✅ | falta correrla completa (P19) |
| §IV-B t / Mann-Whitney por referencia, Friedman + Holm | `Estadistica.java` · `tStudent`, `mannWhitney`, `wilcoxon`, `friedman`, `holm`, `difiereCriterioTema12`; usados en `jmetal/CompararReferencias.java` | ✅ | |
| §IV-C mejor, media, mediana, desvío; % factibles | `Experimentos.java`, `Evaluador.Fitness` | ✅ | |
| §IV-C robustez con 10.000 simulaciones | `jmetal/CompararReferencias.robustez` | ✅ | media, desvío, P5 y P95 de U |
| §IV-C evolución del fitness vs evaluaciones | `AlgoritmoEvolutivo.Resultado.historial*` | ✅ | |
| §IV-C hipervolumen, spread, GD; frente de referencia por unión | `jmetal/IndicadoresMO.java` (indicadores de jMetal), `jmetal/ExperimentoMO.java` | ✅ | HV validado contra cálculo a mano |
| §IV-C tiempo de ejecución | `AlgoritmoEvolutivo.Resultado.segundos`; media y desvío en `jmetal/CompararReferencias` | ✅ | |
| §IV-C evaluaciones hasta percentiles de la mejor | `jmetal/CompararReferencias.evaluacionesHasta` | ✅ | 50, 90 y 99% de la mejora |
| §IV-C speedup del esquema paralelo | `jmetal/ExperimentoIslas.java` | ✅ | aquí con 2 núcleos; barrido completo en ClusterUY |
| §IV-C documentar plataforma | `docs/VALIDACION.md` (entorno) | 🟨 | completar en el informe final |

## Validación (no está en el informe; sostiene los resultados)

| qué | archivo |
|---|---|
| tests estadísticos contra scipy | `ValidarEstadistica.java` |
| regresión de agujeros del simulador (S1–S9) | `ValidarAgujeros.java` |
| decodificador y fitness | `ValidarPlan.java` |
| recálculo externo de la comparación de variantes | `herramientas/recalcular_variantes.py` |
| equivalencia AE sobre jMetal vs AE de referencia | `jmetal/ValidarEquivalenciaJMetal.java` |
| variante multiobjetivo: identidad de D-A6, HV, métricas, NSGA-II | `jmetal/ValidarMO.java` |
| escalado, ruleta con punteros equiespaciados, cruzamiento por bloques | `jmetal/ValidarOperadores.java` |
| determinismo del modelo de islas con 1 y k hilos | `jmetal/ExperimentoIslas.java` (parte 1) |
| reporte de validación | `docs/VALIDACION.md` |
| resultados para el informe final | `docs/RESULTADOS.md`; gráficos con `herramientas/graficos.py` → `docs/figuras/` |
| corridas completas en ClusterUY | `herramientas/clusteruy.sh` |

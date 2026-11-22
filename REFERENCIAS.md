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
| §III-A representación como `Solution` de jMetal | `jmetal/SolucionPlantel.java` | ⬜ | P04 |
| §III-A decodificador cronológico | `Evaluador.java` · `simular`, `aplicarVentana` | ✅ | |
| §III-A reparación (cupo y tope) | `Evaluador.java` · `reparar` | ✅ | proxy nivel/salario |
| §III-A penalización (mínimos por puesto) | `Evaluador.java` · `deficitPuestos`, `LAMBDA_*` | ✅ | |
| §III-B simulador pasos (i)–(iv) | `Evaluador.java` · `jugarPeriodo`, `cerrarTemporada`, `aplicarVentana`; `Temporada.java` | ✅ | |
| §III-B ec. (3) fitness | `Evaluador.java` · `evaluar` | ✅ | |
| §III-B problema como `Problem` de jMetal | `jmetal/ProblemaPlantel.java` | ⬜ | P04 |
| §III-B common random numbers | `Escenario.java`, `Aleatorio.java`; `AlgoritmoEvolutivo.Corrida.rotarEscenarios` | ✅ | |
| §III-B reevaluación de la élite | `AlgoritmoEvolutivo.java` · `bucleGeneracional` | ✅ | se reevalúa toda la población |
| §III-B N creciente | — | reemplazado | por la selección final en dos etapas (M3) |
| §III-C inicialización | `Operadores.java` · `poblacionInicial` | 🟨 | sin siembra golosa (D-A3) |
| §III-C selección por torneo de tamaño 3 | `Operadores.java` · `torneo` | ✅ | |
| §III-C selección proporcional con escalado | — | ⬜ | P10 |
| §III-C cruzamiento uniforme a nivel de gen + reales | `Operadores.java` · `cruzar` | ✅ | aritmético en lugar de BLX-α |
| §III-C cruzamiento por bloques posicionales | — | ⬜ | P11 |
| §III-C mutación (cinco operadores) | `Operadores.java` · `mutar`, `intercambiar` | ✅ | |
| §III-C reemplazo generacional con elitismo | `AlgoritmoEvolutivo.java` · `bucleGeneracional` | ✅ | |
| §III-C comparación con estado estacionario | `AlgoritmoEvolutivo.java` (gap = 2/P); `CompararVariantes.java` | ✅ | |
| §III-C parada: 40.000 evaluaciones | `AlgoritmoEvolutivo.Config.maxEvaluaciones` | ✅ | |
| §III-C parada: límite de tiempo de reloj | — | ⬜ | P09 |
| §III-C diversidad (sharing) | `AlgoritmoEvolutivo.java` · `compartido`; `Operadores.distancia` | ✅ | |
| §III-C escalado de fitness | — | ⬜ | P12 |
| §III-C islas con migración en anillo | — | ⬜ | P13 |
| §III-C NSGA-II (f₁ rendimiento, f₂ costo neto) | `jmetal/ProblemaPlantelMO.java`, `jmetal/NSGAIIPlantel.java` | ⬜ | P05 |
| §III-C extensión: clubes rivales reactivos | `Evaluador.java` · `moverOtrosClubes` | ✅ | |

## IV. Evaluación experimental

| sección del informe | archivo · clase/método | estado | notas |
|---|---|---|---|
| §IV-A tres instancias (50, 150, 400 candidatos) | `GeneradorInstancias.java` · `evaluacion` | 🟨 | sintéticas; faltan datos reales (P17) |
| §IV-A instancias de configuración separadas | `GeneradorInstancias.java` · `configuracion` | ✅ | |
| §IV-A diseño factorial 3×3×3, 30 corridas | `Experimentos.java` | 🟨 | hecho; falta correrlo (P19) |
| §IV-A Shapiro-Wilk → ANOVA / Kruskal-Wallis, Friedman | `Estadistica.java` | 🟨 | hoy Lilliefors; Shapiro en P03 |
| §IV-A estudio del número de réplicas N | — | ⬜ | P14 |
| §IV-B(1) plan real del club | — | ⬜ | P18 |
| §IV-B(2) óptimo exacto en micro-instancias | `OptimoExacto.java`; `Operadores.proyectarAGrilla` | ✅ | micro-instancia fácil (P16) |
| §IV-B(3) greedy miope | `GreedyMiope.java` | ✅ | |
| §IV-B(4) búsqueda aleatoria con igual presupuesto | `AlgoritmoEvolutivo.java` · `busquedaAleatoria` | ✅ | |
| §IV-B(5) statu quo | `Cromosoma.java` · `statuQuo` | ✅ | |
| §IV-B % de mejora y GAP | `EjecutarAE`, `GreedyMiope`, `OptimoExacto` | 🟨 | una corrida; falta la comparación con 30 (P07) |
| §IV-B t / Mann-Whitney por referencia, Friedman + Holm | — | ⬜ | P03, P07 |
| §IV-C mejor, media, mediana, desvío; % factibles | `Experimentos.java`, `Evaluador.Fitness` | ✅ | |
| §IV-C robustez con 10.000 simulaciones | — | 🟨 | hoy N=200 (P08) |
| §IV-C evolución del fitness vs evaluaciones | `AlgoritmoEvolutivo.Resultado.historial*` | ✅ | |
| §IV-C hipervolumen, spread, GD; frente de referencia por unión | — | ⬜ | P06 |
| §IV-C tiempo de ejecución | `AlgoritmoEvolutivo.Resultado.segundos` | ✅ | |
| §IV-C evaluaciones hasta percentiles de la mejor | — | ⬜ | P15 |
| §IV-C speedup del esquema paralelo | — | ⬜ | P13 |
| §IV-C documentar plataforma | `docs/VALIDACION.md` (entorno) | 🟨 | completar en el informe final |

## Validación (no está en el informe; sostiene los resultados)

| qué | archivo |
|---|---|
| tests estadísticos contra scipy | `ValidarEstadistica.java` |
| regresión de agujeros del simulador (S1–S9) | `ValidarAgujeros.java` |
| decodificador y fitness | `ValidarPlan.java` |
| recálculo externo de la comparación de variantes | `herramientas/recalcular_variantes.py` |
| reporte de validación | `docs/VALIDACION.md` |

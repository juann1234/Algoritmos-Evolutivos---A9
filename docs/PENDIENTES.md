# Pendientes, responsables y bitácora

Tablero compartido entre Juan y Alen (y sus Claude). **Al empezar una
sesión**, mirá qué está "en curso" y quién lo tiene, para no pisarse. **Al
terminarla**, actualizá el estado y agregá una entrada a la bitácora.

Estados: ⬜ pendiente · 🟨 en curso · ✅ hecho · ⛔ bloqueado

---

## Tablero

| id | tarea | informe | responsable | estado | notas |
|---|---|---|---|---|---|
| P01 | Validación del trabajo previo | — | Alen | ✅ | `docs/VALIDACION.md` |
| P02 | Documentación de coordinación (CLAUDE.md, REFERENCIAS, DECISIONES, este archivo) | — | Alen | ✅ | |
| P03 | Shapiro-Wilk, Mann-Whitney, Holm, Wilcoxon exacto, validados contra scipy | §IV-A, §IV-B | Alen | ✅ | D-A5; 32 chequeos en `ValidarEstadistica` |
| P04 | Adaptador jMetal + AE generacional sobre jMetal + equivalencia con el AE propio | §III | Alen | ✅ | D-A1, D-A2, D-A12; `jmetal/AGPlantel`, `jmetal/ValidarEquivalenciaJMetal` |
| P05 | NSGA-II con jMetal (f₁ utilidad deportiva, f₂ costo neto) | §III-C | Alen | ✅ | D-A6, D-A10; `jmetal/NSGAIIPlantel`, validado en `jmetal/ValidarMO` |
| P06 | Métricas multiobjetivo: hipervolumen relativo, spread, GD; frente de referencia por unión de ≥ 30 corridas; estadística por métrica | §IV-C, Tema 12 | Alen | ✅ | `jmetal/IndicadoresMO`, `jmetal/ExperimentoMO` (vs agregación, D-A9); falta la corrida completa (P23) |
| P07 | Comparación AE vs referencias en las 3 instancias de evaluación, ≥ 30 corridas, % de mejora, tests (Shapiro → t / Mann-Whitney; Friedman + Holm), GAP | §IV-B | Alen | ✅ | programa: `jmetal/CompararReferencias`; falta la corrida completa (P23) |
| P08 | Robustez del plan final con validación de 10.000 simulaciones | §IV-C | Alen | ✅ | `CompararReferencias.robustez` |
| P09 | Límite de tiempo de reloj como segundo criterio de parada | §III-C | Alen | ✅ | `Config.segundosMax`, apagado por defecto (D-A11) |
| P10 | Selección proporcional con escalado (a comparar contra torneo) | §III-C | Alen | ✅ | peor que torneo (−1,93, p Holm 0,0014) |
| P11 | Cruzamiento por bloques posicionales (a comparar contra uniforme) | §III-C | Alen | ✅ | sin diferencia significativa (−0,58, p 0,076) |
| P12 | Escalado de fitness | §III-C | Alen | ✅ | `Operadores.escalar` |
| P13 | Modelo de islas con migración en anillo + speedup al variar recursos | §III-C, §IV-C | Alen | ✅ | `jmetal/IslasPlantel`, `jmetal/ExperimentoIslas`; speedup con más núcleos en ClusterUY (P23) |
| P14 | Estudio del número de réplicas N (menor N que mantiene estable el ordenamiento) | §IV-A | Alen | ✅ | `jmetal/EstudioReplicas`, D-A13 |
| P15 | Evaluaciones hasta alcanzar percentiles de la mejor solución | §IV-C | Alen | ✅ | `CompararReferencias.evaluacionesHasta` |
| P16 | Micro-instancias más difíciles (statu quo a > 5% del óptimo) | §IV-B(2) | Alen | ✅ | `jmetal/GapMicroInstancias`: óptimo en 30/30 con ≥ 8.000 evaluaciones (D-A15) |
| P17 | Datos reales: cargar SoFIFA/Transfermarkt al esquema `JugadorBase`, recalibrar valor, salario y rivales | §IV-A | Juan | ⬜ | README "Lo que falta" §2 |
| P18 | Reconstruir el **plan real del club** como individuo y evaluarlo | §IV-B(1) | Juan | ⬜ | depende de P17 |
| P19 | Barrido factorial completo en ClusterUY (27 config × 2 inst × 30) | §IV-A | — | ⬜ | `Experimentos 40000 30 2`; ya usa `AGPlantel` (P24) |
| P20 | Verificar `mvn package` en una máquina con Maven Central | — | Juan o Alen | ⬜ | en el entorno del Claude de Alen Maven está bloqueado (D-A4) |
| P21 | Informe final (IEEE, doble columna): ampliar con implementación, calibración, instancias, validación, resultados, conclusiones | todo | ambos | ⬜ | ver "Correcciones para el informe final" abajo |
| P22 | Zip de entrega: fuentes + instancias, sin binarios ni framework | consigna | ambos | ⬜ | |
| P23 | Corridas completas en ClusterUY: `CompararReferencias 40000 30` (3 instancias) y `ExperimentoMO 40000 30` por instancia | §IV-B, §IV-C | — | ⬜ | en el entorno del Claude de Alen solo hay 2 núcleos: ahí se corrieron versiones chicas |
| P24 | Pasar `Experimentos` (factorial) a `AGPlantel` | §IV-A | Alen | ✅ | |
| P25 | Repetir la comparación de variantes (gap, sharing, CHC, memético) sobre las instancias de configuración | §IV-A, Tema 12 | Juan (es su código) | ⬜ | `demo` = `eval-chica` (D-A16); agregar un argumento de instancia a `CompararVariantes` |
| P26 | Revisar y aprobar la decisión de N = 10 (D-A13) | §IV-A | Juan | ⬜ | si no, cambiar `N_FINAL`/`EVALUACIONES_FINALES` |
| P27 | Probar `herramientas/clusteruy.sh` en ClusterUY (partición, módulo de Java) | — | quien tenga cuenta | ⬜ | |

## Correcciones para el informe final (acumuladas)

Lo que el código hace distinto del informe inicial y hay que escribir en el
final. Agregar acá cada vez que aparezca una.

1. Implementación en **Java 21 con jMetal 5.10**, no Python/DEAP (D-A1).
2. Normalidad: **Shapiro-Wilk** (se mantiene lo prometido, D-A5); post-hoc **Holm**.
3. El "N creciente" se reemplazó por la **selección final en dos etapas** (M3).
4. Cruzamiento de los reales: **aritmético** (antes BLX-α, que no está en el temario).
5. Inicialización: statu quo + perturbaciones, **sin sembrar soluciones golosas** (D-A3).
6. Reparación: se usa el **proxy nivel/salario** del aporte marginal (el exacto exige simular).
7. Ec. (1): el **potencial** es un techo del crecimiento.
8. Lesiones: covariables centradas/normalizadas, probabilidad anual repartida en dos mitades, días de baja Gamma(2, 25).
9. Patrimonio final **neto** (D5) y todas las decisiones de modelado D1–D11.
10. Escenarios comunes y selección final en dos etapas: declararlas como metodología para fitness ruidoso (Tema 5 + Jin & Branke [3]).
11. Con el plantel lleno, toda incorporación exige una salida (refuerza la epistasis de §II-A).
12. **N = 10 réplicas y 120.000 evaluaciones** en los experimentos finales (mismo costo en simulaciones que 40.000 con N = 30), por el estudio de N (D-A13).
13. GAP con la fórmula del Tema 12, [f(VT) − f(AE)] / f(AE), y el AE optimizando sobre los mismos escenarios fijos que la enumeración (D-A15). Micro-instancias de **5 jugadores** como dice el informe (Juan usaba 6).
14. Selección proporcional con escalado: peor que el torneo; bloques posicionales: sin diferencia. Se queda torneo + uniforme.
15. Comparación multiobjetivo contra agregación con barrido de β (D-A9), que el informe inicial no especificaba.

---

## Bitácora

Entradas nuevas arriba. Formato: fecha · quién · qué se hizo · cómo se validó · qué sigue.

### 22/11/2026 · Alen (con Claude) · corridas preliminares y control de sesgo
- Corridas preliminares con el protocolo final reducido (N = 10, 24.000
  evaluaciones, 10 corridas): en eval-chica el AE mejora +6,7% sobre el
  greedy y +10,6% sobre el statu quo; en eval-media +13,5% y +16,6%; todos
  los tests significativos. NSGA-II vs agregación en eval-chica: RHV 0,93
  vs 0,76, GD 0,008 vs 0,041 (p < 0,0001). Salidas `resultados/*_preliminar.*`.
- `demo` = `eval-chica` (D-A16): los estudios de N y de operadores se
  repitieron sobre conf-joven y conf-veterano y las decisiones se sostienen.
- `herramientas/graficos.py` → `docs/figuras/` (frente de Pareto, GAP vs
  presupuesto, estudio de N). `herramientas/clusteruy.sh` para las corridas
  completas (sin probar). `docs/RESULTADOS.md`: todo lo obtenido, listo para
  el informe final.

### 22/11/2026 · Alen (con Claude) · compromisos pendientes del informe
- Selección proporcional con escalado (truncamiento sigma + lineal, ruleta
  con punteros equiespaciados) y cruzamiento por bloques posicionales;
  validados en `ValidarOperadores`. Comparados (`CompararOperadores`, 20
  semillas, 8.000 evaluaciones, Wilcoxon pareado + Holm): proporcional
  −1,93 (p Holm 0,0014, peor); bloques −0,58 (p 0,076, sin diferencia).
  Salida en `resultados/operadores_demo.txt`.
- Micro-instancias de 5 jugadores con el statu quo lejos del óptimo
  (semillas 55, 56, 57): `GapMicroInstancias`. Se detectó y corrigió un sesgo
  en cómo se medía el GAP (D-A15). Con 8.000 evaluaciones el AE encuentra el
  óptimo exacto en 30/30 en las tres. Salida en `resultados/gap_micro.txt`.
- Estudio del número de réplicas (`EstudioReplicas`, D-A13): a igual costo
  N = 10 es significativamente mejor que N = 30 → protocolo final N = 10 con
  120.000 evaluaciones (`Config.protocoloFinal()`). Juan: mirá D-A13.
- Modelo de islas con migración en anillo (`IslasPlantel`, D-A14) y su
  experimento (`ExperimentoIslas`): determinismo con 1 y k hilos, speedup y
  calidad contra el AE panmíctico.
- `OptimoExacto` admite semilla/edad/presupuesto e informa el AE sobre jMetal.
- Kendall y Spearman validados contra scipy en `ValidarEstadistica` (34 OK).

### 22/11/2026 · Alen (con Claude) · módulo jMetal (AE, NSGA-II, métricas, comparación)
- Paquete `src/ae/plantel/jmetal/`: `Gen` + `SolucionPlantel` (Solution),
  `ProblemaPlantel` / `ProblemaPlantelMO` (Problem), `OperadoresJMetal`
  (CrossoverOperator, MutationOperator, SelectionOperator con el RNG de la
  corrida, D-A2), `AGPlantel` (AbstractGeneticAlgorithm) y `NSGAIIPlantel`
  (NSGAII de jMetal) con escenarios comunes y reevaluación (D-A10).
- Validación de `AGPlantel` contra el AE de Juan
  (`ValidarEquivalenciaJMetal`, D-A12): misma semilla → misma corrida aun en
  paralelo; mismas evaluaciones (7.934) y generaciones (78); con 50 semillas
  y 8.000 evaluaciones, diferencia de medias −0,13 (IC 95% [−0,61; +0,35]),
  t de Welch p = 0,61, Mann-Whitney p = 0,54. Con 20 semillas Mann-Whitney
  había dado p = 0,044 (t p = 0,095): era azar, lo confirma la de 50.
  Salida completa en `resultados/equivalencia_jmetal_50semillas.txt`. Mismo
  costo por corrida (5,6 s con 4.000 evaluaciones las dos).
- `ValidarMO`: identidad f + ρσ = f₁ − βf₂ + C exacta (error 1e-13) en 30
  planes; HV de jMetal = cálculo 2D a mano; RHV, GD y spread con las
  propiedades esperadas; NSGA-II corto da frente no dominado, reproducible y
  con planes mejores que el statu quo.
- `CompararReferencias` (P07, P08, P15) y `ExperimentoMO` (P06, D-A9):
  probados con corridas chicas. Las corridas completas quedan para
  ClusterUY (P23).
- Límite de tiempo de reloj (P09, D-A11). `Experimentos` usa `AGPlantel` (P24).
- `validar.sh` suma los pasos 8 (ValidarMO) y 9 (equivalencia corta).
  Validado: `./validar.sh` completo, todo OK, números de referencia sin cambios.

### 22/11/2026 · Alen (con Claude) · módulo de estadística
- `Estadistica.java`: Shapiro-Wilk (AS R94 de Royston), t de Welch,
  Mann-Whitney (asintótico con corrección), Holm, Wilcoxon exacto cuando
  no hay empates y n ≤ 50, cuantil normal (AS 241) y Φ de precisión doble.
  Validado: `ValidarEstadistica` pasó de 8 a 32 chequeos contra scipy 1.18.1,
  todos OK.
- `Experimentos` usa Shapiro-Wilk (Lilliefors como control) y Holm;
  `CompararVariantes` usa Holm. Su salida coincide con
  `herramientas/recalcular_variantes.py` (p exacto 0,0020, Holm 0,0098).
- Efecto en números de referencia: solo cambian los p de la comparación de
  variantes (README actualizado). Las conclusiones no cambian.

### 22/11/2026 · Alen (con Claude) · validación y coordinación
- Validé todo lo de Juan (`docs/VALIDACION.md`): `validar.sh` reproduce
  exactamente todos los números; tests recalculados con scipy
  (`herramientas/recalcular_variantes.py`), las conclusiones se sostienen
  y el p exacto de Wilcoxon es menor que el normal; no encontré agujeros
  nuevos; las sondas útiles quedaron como S7–S9 en `ValidarAgujeros`.
- Etiqueté cada clase y los métodos clave con su sección del informe
  (`[Informe §...]`). Solo cambiaron comentarios en el código de Juan.
- Creé `CLAUDE.md`, `REFERENCIAS.md`, `docs/DECISIONES.md`, este archivo,
  `pom.xml` (jMetal 5.10) y `compilar.sh`; `validar.sh` compila con
  `compilar.sh`.
- Sigue: P03 (estadística), P04 (jMetal), P05–P06 (NSGA-II), P07–P08.

### hasta `d697d60` · Juan
Simulador, decodificador, reparación, fitness, AE generacional con variantes,
referencias (statu quo, aleatoria, greedy, óptimo exacto), tests
estadísticos, diseño factorial, comparación de variantes. Detalle en el
README.

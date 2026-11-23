# Resultados obtenidos (material para el informe final)

Todo lo que hay hasta ahora, con su fuente. **Preliminar** quiere decir que
se corrió con presupuesto o corridas reducidas en una máquina de 2 núcleos;
la versión definitiva va en ClusterUY (`herramientas/clusteruy.sh`,
PENDIENTES P23). Todos los fitness están revaluados con N = 200 sobre
escenarios de validación.

Figuras: `docs/figuras/` (regenerar con `python3 herramientas/graficos.py`).

---

## 1. El AE contra las técnicas de referencia (§IV-B) · preliminar

`CompararReferencias 24000 10` (N = 10). Fuente: `resultados/comparacion_preliminar.txt`.

| instancia | AE media ± sd (mejor) | aleatoria | greedy | statu quo | mejora vs greedy | mejora vs statu quo |
|---|---|---|---|---|---|---|
| eval-chica (25 + 50) | 233,98 ± 0,87 (235,37) | 211,60 | 219,35 | 211,60 | +6,7% | +10,6% |
| eval-media (25 + 150) | 242,94 ± 4,43 (248,04) | 208,45 | 214,08 | 208,29 | +13,5% | +16,6% |
| eval-grande (25 + 400) | pendiente (ClusterUY) | | | | | |

- Tests: AE vs aleatoria significativo (t de Welch / Mann-Whitney según
  Shapiro); AE vs greedy y vs statu quo: Wilcoxon de una muestra p = 0,002
  y criterio del Tema 12 "difieren"; Friedman p ≈ 1e-6 con rangos medios AE
  1,00 / greedy 2,00 / aleatoria y statu quo 3,5; Holm significativo en los
  tres pares.
- La ventaja del AE **crece con el tamaño del mercado** (más candidatos =
  más epistasis y más espacio).
- Robustez (10.000 simulaciones): el plan del AE tiene mayor media y un
  desvío apenas mayor (4,2 vs 3,8 en eval-chica); su percentil 5 (229,9)
  supera al percentil 95 del greedy (227,0).
- Eficiencia: el AE alcanza el 50% de su mejora a las ≈ 2.700 evaluaciones
  y el 90% a las ≈ 11.600 (eval-chica); una corrida de 24.000 evaluaciones
  tarda 13 s (eval-chica) y 28 s (eval-media).

## 2. Óptimo exacto en micro-instancias (§IV-B(2))

`GapMicroInstancias 30`, 3 micro-instancias de 5 jugadores (107.811 planes
cada una), el AE optimiza sobre los mismos escenarios fijos que la
enumeración (D-A15). Fuente: `resultados/gap_micro.txt`. Figura: `gap_micro.png`.

| micro-instancia | GAP del statu quo | GAP medio del AE con 500 / 2.000 / 8.000 evals | óptimo encontrado con 8.000 |
|---|---|---|---|
| 55 | 5,72% | 0,40% / 0,16% / 0,00% | 30 de 30 |
| 56 | 13,75% | 1,45% / 0,22% / 0,00% | 30 de 30 |
| 57 | 5,85% | 0,31% / 0,12% / 0,00% | 30 de 30 |

Con 8.000 evaluaciones (7,4% del espacio) el AE encuentra el óptimo exacto
en todas las corridas.

## 3. Variante multiobjetivo (§III-C, §IV-C) · preliminar

`ExperimentoMO 24000 10 eval-chica`. Fuente:
`resultados/mo_eval-chica_preliminar.txt`. Figura: `frente_pareto_eval-chica.png`.

| métrica | NSGA-II | agregación (barrido de β) | test |
|---|---|---|---|
| hipervolumen relativo (↑) | 0,927 ± 0,034 | 0,762 ± 0,013 | t de Welch p < 0,0001 |
| spread (↓) | 0,766 ± 0,033 | 0,678 ± 0,088 | t de Welch p = 0,013 (gana la agregación, con ~5 puntos) |
| distancia generacional (↓) | 0,008 ± 0,005 | 0,041 ± 0,004 | t de Welch p < 0,0001 |
| puntos no dominados | 38 ± 7 | 5,4 ± 0,5 | Mann-Whitney p = 0,0001 |

A igual presupuesto, NSGA-II da un frente más cercano a la referencia y con
siete veces más alternativas. Por D-A6, el plan mono-objetivo con β = 0,05
cae cerca del frente (distancia normalizada 0,09) y solo en 4 de 10
corridas queda dominado por el frente de NSGA-II.

## 4. Estudio del número de réplicas N (§IV-A)

`EstudioReplicas`. Fuente: `resultados/replicas_demo.txt` y
`resultados/diseno_conf-*.txt`. Figura: `replicas_N.png`.

A igual número de simulaciones (240.000), fitness medio de 10 semillas:

| N | eval-chica | conf-joven | conf-veterano |
|---|---|---|---|
| 5 | 234,48 | 227,57 | 221,03 |
| **10** | **233,98** | **226,82** | **220,36** |
| 20 | 232,14 | 224,48 | 218,44 |
| 30 | 229,53 | 222,38 | 216,19 |
| 50 | 228,35 | 217,95 | 214,03 |

N = 10 es significativamente mejor que N = 30 en las tres (p Holm 0,0078).
Decisión D-A13: protocolo final N = 10 con 120.000 evaluaciones, el mismo
costo que 40.000 con N = 30.

## 5. Estudio de operadores (§III-C)

`CompararOperadores 20 8000`, Wilcoxon pareado + Holm contra torneo +
uniforme. Fuente: `resultados/operadores_*.txt`, `resultados/diseno_conf-*.txt`.

| variante | eval-chica | conf-joven | conf-veterano |
|---|---|---|---|
| selección proporcional con escalado | −1,93 (p 0,0014) | −3,80 (p < 0,0001) | −2,03 (p 0,0001) |
| cruzamiento por bloques posicionales | −0,58 (p 0,076) | −3,49 (p < 0,0001) | −1,97 (p 0,0001) |

Se mantiene torneo de 3 con cruzamiento uniforme. La proporcional conserva
más diversidad (≈ 22 vs ≈ 12 genes distintos entre pares) pero tiene menos
presión selectiva; los bloques exploran menos (16 hijos posibles).

## 6. Variantes del modelo evolutivo (Juan, §III-C)

`CompararVariantes`, 10 semillas por variante, 8.000 evaluaciones equivalentes
con N = 30, Wilcoxon pareado por semilla contra la base con Holm (D-A5).
Fuentes: `resultados/resultados_variantes.csv` (demo = eval-chica) y
`resultados/resultados_variantes_conf-*.csv` con su análisis en
`resultados/variantes_configuracion.txt` (P25).

Diferencia de la media contra la base (p Holm entre paréntesis):

| variante | demo (eval-chica) | conf-joven | conf-veterano | lectura |
|---|---|---|---|---|
| memético | +0,01 (0,85) | +0,93 (0,32) | −0,56 (0,26) | no aporta |
| sharing | −2,46 (0,0098) | +0,22 (0,70) | −0,41 (0,38) | no aporta; el "peor" de demo era propio de esa instancia |
| gap generacional 50% | −2,76 (0,0098) | −2,34 (0,059) | −3,58 (0,0117) | peor o igual |
| CHC | −12,47 (0,0098) | −14,47 (0,0117) | −10,26 (0,0117) | peor |
| estado estacionario | −16,38 (0,0098) | −23,51 (0,0117) | −15,03 (0,0117) | peor |
| **N = 10, mismas simulaciones** | — | **+5,56 (0,0117)** | **+3,15 (0,0117)** | **mejor**: confirma D-A13 |

**En las instancias de configuración ninguna variante del modelo supera a
la base generacional con elitismo**, así que la decisión se sostiene sin el
sesgo de haberla tomado sobre una instancia de evaluación. El matiz: sharing
y memético quedan *neutros* (no peores), así que en el informe hay que decir
"no aportan", no "empeoran". Gap, CHC y estado estacionario son peores en
las tres instancias por la misma razón: con escenarios rotativos cada
generación reevalúa a todos, y producen menos hijos por evaluación gastada
(estacionario y gap) o no exploran los campos reales sin mutación (CHC).

La fila N = 10 es una verificación independiente de D-A13 hecha con el AE
original (no con el de jMetal): a igual total de simulaciones, N = 10 le gana
a N = 30 por +5,56 y +3,15, del mismo orden que lo medido por Alen (+4,44 y
+4,17).

## 7. Modelo de islas (§III-C, §IV-C)

`ExperimentoIslas 20 24000 4` (4 islas, anillo, 2 migrantes cada 5
generaciones). Fuente: `resultados/islas_demo.txt`.

- Determinismo: misma semilla, mismo plan con 1 y con 4 hilos.
- Speedup con 2 hilos: 1,92 (eficiencia 0,96). Con más núcleos, ClusterUY.
- Calidad: islas 234,02 ± 1,08 vs panmíctico 233,73 ± 0,85, t de Welch
  p = 0,35: el paralelismo no cuesta calidad.

## 8. Validación de la implementación

Ver `docs/VALIDACION.md`: el AE sobre jMetal es equivalente al de referencia
(50 semillas, p = 0,61), 34 tests estadísticos coinciden con scipy, la
identidad mono/multiobjetivo es exacta y no se encontraron agujeros nuevos
en el simulador.

## Plataforma (para el informe, Tema 12)

Corridas preliminares: Intel Xeon 2,1 GHz, 2 núcleos, 8 GB, Linux, OpenJDK
21.0.12, jMetal 5.10, Apache Commons Math 3.6.1 / Lang 3.9. Las definitivas:
completar con los datos del nodo de ClusterUY.

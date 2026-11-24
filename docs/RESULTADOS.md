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

### 4.1 N creciente (Juan, 23/11/2026)

El informe inicial prometía un "aumento progresivo de N a lo largo de la
ejecución" (§III-B). Se implementó en el AE original (`Config.replicasMin` y
`replicasMax`): N crece linealmente de 5 a 30 a medida que se consume el
presupuesto, con el mismo total de simulaciones que N fijo. La motivación sale
del estudio de N: con poblaciones diversas alcanza con pocas réplicas para
ordenar, y con poblaciones convergidas el orden se degrada (D-A13).
Fuente: `resultados/n_creciente.txt`.

| variante (mismas simulaciones) | conf-joven | conf-veterano |
|---|---|---|
| N = 30 fijo | 220,84 ± 2,02 | 217,42 ± 1,34 |
| **N = 10 fijo** | **226,41 ± 1,33** | **220,57 ± 1,47** |
| N creciente 5 → 30 | 225,74 ± 1,18 | 220,21 ± 1,25 |
| creciente vs N = 10 (p Holm) | −0,67 (0,084) | −0,35 (0,28) |

El N creciente **empata con N = 10 fijo** y le gana a N = 30 fijo. La
precisión que suma en la etapa convergida no compensa las generaciones que
pierde (171 contra 238): ese ruido ya lo absorbe la selección final en dos
etapas. Se mantiene N = 10 fijo por ser más simple (D-J1).

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

## 9. Caso real: FC Barcelona 2020-2023 (Juan, §IV-B(1)) · preliminar

`EvaluarCasoReal <instancia> 24000 5`, parámetros calibrados (D-J2, β = 0,01
por D-J6), protocolo N = 10, 5 semillas, revaluación con N = 200. Fuente:
`resultados/caso_real_barcelona_preliminar.txt`.

| instancia | statu quo | plan real del club | AE media ± sd | AE vs plan real |
|---|---|---|---|---|
| chica (25 + 50) | 195,17 | 229,43 | 247,88 ± 0,28 | +8,0% |
| media (25 + 150) | 195,17 | 229,43 | 244,63 ± 1,32 | +6,6% |
| grande (25 + 400) | 195,17 | 229,43 | 243,84 ± 1,59 | +6,3% |

Desglose en chica:

| | statu quo | plan real | AE (media) |
|---|---|---|---|
| puntos T1 / T2 / T3 | 94,0 / 85,7 / 52,5 | 92,5 / 88,3 / 88,7 | 93,3 / 93,4 / 93,8 |
| patrimonio final (caja + pases) | 692M | 667M | ≈ 1.190M |
| jugadores al final | 6,0 | 14,8 | 20,5 |
| perdidos libres por vencimiento | 17,2 | 9,4 | 3,5 |

- El AE **domina al plan real en las dos dimensiones**: más puntos en las
  tres temporadas y casi el doble de patrimonio. La diferencia con el plan
  real (≈ 18 puntos de fitness) es diez veces mayor que el desvío del AE
  (criterio del Tema 12: difieren). Con 5 semillas el Wilcoxon de una muestra
  no puede bajar de p = 0,0625; con las 30 de ClusterUY sí.
- El statu quo pierde 17 jugadores libres por vencimiento (Messi incluido) y
  se desploma en T3: el problema real es de contratos, no solo de fichajes.
- El simulador reproduce por su cuenta un hecho real: en 2022 al plan real le
  falta caja para Raphinha, Koundé, Kessié y Christensen en hasta el 38% de los
  escenarios, que es lo que el club resolvió con las "palancas".
- El AE rinde **menos en la instancia grande que en la chica** aunque la chica
  está contenida en ella: con el mismo presupuesto de 24.000 evaluaciones no
  llega a converger con 425 genes. Es lo primero a mirar con las 120.000
  evaluaciones del protocolo final.

## 10. Protocolo final sobre las instancias reales (jMetal, Alen, P29) · preliminar

`CompararReferencias 24000 10` (chica y media) y `24000 6` (grande), N = 10,
parámetros calibrados, plan real como referencia (D-A18). Fuente:
`resultados/comparacion_reales_preliminar.txt` y los CSV
`comparacion_barcelona-2020-*_preliminar.csv`.

| instancia | AE media ± sd | aleatoria | plan real | greedy | statu quo | AE vs plan real | AE vs greedy |
|---|---|---|---|---|---|---|---|
| barcelona-2020-chica (50) | 247,23 ± 0,85 | 225,79 | 229,43 | 234,63 | 195,17 | +7,8% | +5,4% |
| barcelona-2020-media (150) | 244,30 ± 0,81 | 227,62 | 229,43 | 235,78 | 195,17 | +6,5% | +3,6% |
| barcelona-2020-grande (400, 6 corridas) | 244,96 ± 1,27 | 228,95 | 229,43 | 237,16 | 195,17 | +6,8% | +3,3% |

- **El plan del AE domina al plan real en todas las corridas** (10/10, 10/10,
  6/6): más utilidad deportiva (f₁ 234,9–237,6 vs 224,9; unos 8–11 puntos de
  liga más en tres temporadas) y menos costo neto. Coincide con el
  preliminar de Juan con el AE original (§9).
- Tests: Wilcoxon de una muestra contra cada referencia p = 0,002 (n = 10;
  0,031 con n = 6) y criterio del Tema 12 "difieren"; Friedman p < 0,001 con
  el AE siempre en el rango 1; Holm 0,0078 con n = 10 (con n = 6 el mínimo
  posible es 0,125: grande necesita las 30 corridas de ClusterUY).
- **Con datos reales el greedy es mucho más fuerte** que con los sintéticos
  (supera al plan real por 2–3,5%), pero lo logra gastando: f₂ ≈ 470–490 M
  contra 231 M del plan real. Ordenamiento de Friedman en las tres:
  AE < greedy < plan real < aleatoria < statu quo.
- Robustez (10.000 simulaciones), barcelona-2020-chica: AE 249,9 ± 3,2
  (P5 244,5) contra plan real 231,6 ± 4,7 (P95 238,5): el peor 5% del AE
  supera al mejor 5% del plan real.
- **Cuidado con la magnitud de f₂.** En f₁ el AE es muy estable entre
  semillas (236–239), pero f₂ va de −369 a +4 M: a β = 0,01, 370 M valen
  3,7 puntos de fitness y el AE es casi indiferente entre esos planes. Los
  planes de patrimonio extremo renuevan temprano a casi todo el plantel
  valioso y compran jóvenes de potencial alto (Dest, Eric García, Koundé,
  Ferran Torres, Arthur), y terminan con unos 1.000 M en 18 jugadores. Sale de
  la economía de la renovación (D4/D5) y de la pendiente del valor calibrado
  (D-J2), no de un error, pero conviene apoyar la conclusión en la dominancia
  en f₁ y mostrar el compromiso completo con el frente de Pareto (PENDIENTES P31).

**Multiobjetivo en barcelona-2020-chica** (`ExperimentoMO 24000 10`; fuente
`resultados/mo_barcelona-2020-chica_preliminar.txt`; figura
`frente_pareto_barcelona-2020-chica.png`):

| métrica | NSGA-II | agregación (β × {0; 0,4; 1; 2; 4; 10}) | test |
|---|---|---|---|
| hipervolumen relativo (↑) | 0,917 ± 0,032 | 0,681 ± 0,036 | t de Welch p < 0,0001 |
| spread (↓) | 0,764 ± 0,067 | 0,686 ± 0,062 | p = 0,015 (gana la agregación, con 5 puntos) |
| distancia generacional (↓) | 0,006 ± 0,003 | 0,050 ± 0,010 | p < 0,0001 |
| puntos no dominados | 30,6 ± 6,2 | 5,1 ± 0,6 | p = 0,0001 |

El plan real y el statu quo quedan **dominados por el frente de NSGA-II en
10 de 10 corridas** (y por el de la agregación también). El plan
mono-objetivo con β = 0,01 queda cerca del frente (distancia normalizada
0,08), dominado en 7 de 10 corridas por el frente de NSGA-II de la misma
semilla, que con el mismo presupuesto explora mejor.

## Plataforma (para el informe, Tema 12)

Corridas preliminares: Intel Xeon 2,1 GHz, 2 núcleos, 8 GB, Linux, OpenJDK
21.0.12, jMetal 5.10, Apache Commons Math 3.6.1 / Lang 3.9. Las definitivas:
completar con los datos del nodo de ClusterUY.

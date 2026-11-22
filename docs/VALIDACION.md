# Validación del trabajo previo (Alen, 22/11/2026)

Revisión pedida en el README ("Lo primero: validación"), hecha con ojos frescos
sobre el commit `d697d60`. Cada punto dice qué se hizo, qué dio y qué queda
abierto. Lo que quedó abierto pasó a [`PENDIENTES.md`](PENDIENTES.md) y las
decisiones que se tomaron a raíz de esto están en [`DECISIONES.md`](DECISIONES.md).

Entorno: OpenJDK 21.0.12, Linux x86_64, 2 núcleos Xeon 2.1 GHz, Python 3 +
scipy 1.18.1.

---

## 1. `./validar.sh`: los números coinciden exactamente

| paso | esperado (README / cabeceras) | obtenido | ¿coincide? |
|---|---|---|---|
| tests estadísticos vs scipy | 8 OK | 8 OK | sí |
| S1 rango en 200 semillas | ≈ [0,00; 1,00] | [0,0048; 0,9993] | sí |
| S2 renovar a todos en V6 | −0,24 | −0,24 | sí |
| S3 liberar a los 6 peores | −11,34 | −11,34 | sí |
| S4 ceder / vender / liberar | −1,07 / −5,46 / −7,65 | −1,07 / −5,46 / −7,65 | sí |
| S5 correlación CRN | ≥ 0,99 | 0,999 / 0,991 / 1,000 | sí |
| S6 penalización statu quo | 1,10 | 1,10 | sí |
| statu quo / aleatoria / AE (8.000) | 211,60 / 211,60 / 233,28 | 211,60 / 211,60 / 233,28 | sí |
| greedy miope | 219,35 | 219,35 | sí |
| óptimo exacto, mejor de 10 | GAP 0,00 % | GAP 0,00 % (medias 0,14 / 0,06 / 0,03 %) | sí |

El simulador es determinista dada la semilla y el entorno no introduce
diferencias. Costo medido: unos 3 ms por evaluación con N=30 (el README dice
5 a 7 ms; esta máquina es algo más rápida). `validar.sh` completo tarda 3,5 min.

## 2. Simulador contra el informe (`docs/Informe_Inicial_v2.pdf`)

| parte del informe | implementación | veredicto |
|---|---|---|
| Ec. (1) evolución del nivel, g⁺·h(m) + g⁻ + ε | `Evolucion.evolucionarNivel` | coincide. **Agregar al informe final**: el potencial actúa como techo del crecimiento (no está en el inicial). |
| Lesiones, logística en edad, historial y carga | `Evolucion.probabilidadLesion`, `sortearLesionMitad` | coincide. Las covariables están centradas/normalizadas (edad−24, historial/100, minutos/3420) y la probabilidad anual se reparte en dos mitades; los días de baja son Gamma(2, 25). **Agregar al informe final.** |
| Valor de mercado de θ, edad y contrato | `Evolucion.valorMercado`, `valorNeto` | coincide; el valor neto (resta sobrecosto salarial) es una mejora posterior, documentarla. |
| Ec. (2) F = E[Σγᵗ Rₜ + β V₃] | `Evaluador.simular` | coincide; V₃ = caja + valor neto de los jugadores. |
| Ec. (3) f = media U − ρ σ̂ − Σ λⱼ vⱼ | `Evaluador.evaluar` | coincide (las penalizaciones se descuentan dentro de cada U). |
| Operaciones que fracasan según la oferta salarial | `Evaluador.probAcepta` | coincide. |
| Reparación por aporte marginal | `Evaluador.reparar` (proxy θ / salario) | coincide en espíritu; el informe final debe decir que se usa un proxy porque el aporte marginal exacto exige simular. |
| Inicialización "sembrando con soluciones golosas y el statu quo" | `Operadores.poblacionInicial` | **difiere**: se siembra con el statu quo y perturbaciones suyas, **no** con soluciones golosas. Ver decisión D-A3. |
| Selección proporcional con escalado (a evaluar) | — | **no implementado** → M6. |
| Cruzamiento por bloques posicionales (a comparar) | — | **no implementado** → M6. |
| Parada: 40.000 evaluaciones **más límite de tiempo de reloj** | solo evaluaciones | **falta el límite de tiempo** → M6. |
| Escalado de fitness, islas con migración en anillo | — | **no implementado** → M6. |
| Estudio del número de réplicas N | — | **no implementado** → PENDIENTES. |
| NSGA-II (f₁ puntos, f₂ costo neto) | — | **no implementado** → M4 (jMetal). |
| Shapiro-Wilk, t/Mann-Whitney, Friedman + Holm | Lilliefors, Wilcoxon, Bonferroni | **parcial** → M2. |
| Robustez con validación de 10.000 simulaciones | se usa N=200 | **difiere** → M5. |
| Plan real del club | — | **falta** (datos reales). |

## 3. Decisiones de modelado: ¿son razonables? ¿hay otro agujero?

Las 11 decisiones del README me parecen razonables y cada una tiene su sonda.
Busqué agujeros nuevos con el método de `ValidarAgujeros` (plan "tramposo" armado
a mano, N=200, escenarios de validación). Resultados (statu quo: f = 211,60):

| sonda | f | puntos T1/T2/T3 | lectura |
|---|---|---|---|
| renovar θ≥78 en V1 con cláusula 1,0× | 198,80 | 75,4 / 71,3 / 64,5 | cláusula baja: 3 jugadores perdidos por cláusula, entra caja pero se pierden puntos. **No es explotable.** |
| ídem con cláusula 3× / 6× / 10× | 211,1 / 211,1 / 211,3 | 75,4 / 76,1 / 74,6 | el blindaje se paga con salario; diferencias chicas. |
| comprar los 3 mejores candidatos en V1 sin vender | 211,60 | igual al statu quo | **no se concreta ninguna**: el plantel está en el cupo (25) y la reparación revierte las compras. Comportamiento correcto. |
| vender 3 peores + comprar 3 candidatos en V1 | 217,92 | — | sí se concreta: comprar exige liberar cupo en la misma ventana. |
| ceder a los 7 peores en V1 / en V5 | 208,45 / 204,03 | baja T1 / baja T3 | ceder cuesta puntos en la temporada en que el jugador no está. |
| vender a los 5 mayores de 29 en V1 | 199,66 | 71,4 / 72,2 / 65,6 | la caja sube 52M pero no compensa los puntos. |
| prioridad DESARROLLO a todos los ≤ 22 | 211,58 | 73,8 / 75,4 / 71,7 | sacrifica T1 por T3 y patrimonio (+14M en pases): compromiso real, no agujero. |

**Conclusión:** no encontré otra forma de subir el fitness sin subir los
puntos. Las tres sondas más útiles quedaron como **S7, S8 y S9** en
`ValidarAgujeros.java` (con sus valores esperados en la cabecera), así que
corren en cada `./validar.sh` como regresión.

Un hallazgo que no es un error pero hay que saberlo: **con el plantel lleno,
toda incorporación necesita una salida en la misma ventana o una anterior**.
Eso vuelve el problema más epistático (refuerza la Sección II-A del informe):
una compra solo vale si el plan también vende.

## 4. Metodología

| regla | verificación | veredicto |
|---|---|---|
| Escenarios comunes (CRN) | S5: correlación ≥ 0,99 entre planes que difieren en una operación | correcta |
| Reevaluación de sobrevivientes | `bucleGeneracional` reevalúa toda la población (élite incluida) al rotar | correcta |
| Selección final en dos etapas | top 10 remedidos con N=100 sobre semillas propias; su costo se descuenta del presupuesto | correcta |
| Semillas de validación nunca usadas en la búsqueda | búsqueda: s·1.000.003 + k·30; selección: 9·10⁸ + s·10⁴; validación: 777.000–777.199 | sin solapamiento para s = 1..30 |
| Presupuesto en evaluaciones | contador único que incluye reevaluaciones, búsqueda local y selección final | correcta |
| Instancias de configuración ≠ evaluación | semillas 201–202 vs 101–103 | correcta |
| Friedman y Wilcoxon bloqueados por semilla | — | correcto como diseño en bloques. **Matiz:** el comentario de `Experimentos` dice que la semilla s de dos configuraciones "comparte población inicial"; eso solo es cierto si tienen el mismo tamaño de población. El bloqueo sigue siendo válido (Friedman no lo exige), pero hay que redactarlo bien en el informe. |
| Wilcoxon con n = 10 | aproximación normal | **mejorable**: con n = 10 corresponde el p exacto. Ver §5. |
| Bonferroni | correcto pero conservador | StatTests (diapositivas) recomienda **Holm** como post-hoc de Friedman → M2. |

## 5. Tests recalculados por fuera (scipy)

`python3 herramientas/recalcular_variantes.py` sobre
`resultados/resultados_variantes.csv`:

| variante (8.000 evals) | dif. vs base | p normal (Java) | **p exacto** | Bonferroni (exacto) | Holm (exacto) | Shapiro-Wilk p |
|---|---|---|---|---|---|---|
| gap50 | −2,76 | 0,0051 | 0,0020 | 0,0098 | 0,0098 | 0,888 |
| estacionario | −16,38 | 0,0051 | 0,0020 | 0,0098 | 0,0098 | **0,016** |
| sharing | −2,46 | 0,0051 | 0,0020 | 0,0098 | 0,0098 | 0,904 |
| chc | −12,47 | 0,0051 | 0,0020 | 0,0098 | 0,0098 | 0,751 |
| memético | +0,01 | 0,7989 | 0,8457 | 1,0000 | 0,8457 | 0,925 |

Con 24.000 evaluaciones (n = 6): sharing −1,08, p exacto 0,4375 (Java: 0,3454).

**Las conclusiones del README se sostienen** (las cuatro variantes peores
siguen siendo significativamente peores; memético y sharing con 24.000 siguen
sin diferencia). Lo que cambia es el número que hay que reportar: el p exacto
es menor (0,0098 en lugar de 0,025). Además, `estacionario` no es normal según
Shapiro-Wilk, lo que confirma que los no paramétricos son la elección correcta.

## 6. Temario

| técnica usada | dónde está en las diapositivas |
|---|---|
| torneo, elitismo, reemplazo generacional | Tema 3 (selección, reemplazo), Tema 4 (elitismo) |
| cruzamiento uniforme | Tema 3, Tema 7 |
| cruzamiento aritmético para reales | Tema 2 ("aritmética/gaussiana"), Tema 3 ("aritméticos: promedio, combinación lineal") |
| mutación gaussiana | Tema 2, Tema 3 |
| reparación y penalización | Tema 3 (modelo de penalización), Tema 5 (descartar, penalizar o corregir) |
| gap generacional, estado estacionario | Tema 4 |
| sharing | Tema 8 |
| CHC, memético | Tema 9 |
| maestro-esclavo | Tema 11 |
| Lilliefors, ANOVA, Kruskal-Wallis, Friedman | Tema 12 |
| Shapiro-Wilk, Wilcoxon, Holm | StatTests (Dorronsoro) |
| NSGA-II, HV, spread, GD | Tema 10, Tema 12 |
| jMetal | Tema 6 ("Implementación de AEs usando jMetal") |
| **escenarios comunes y selección final en dos etapas** | **no están como tales**. Tema 5 contempla fitness "evaluada por simulaciones" y "con ruido"; se justifican con Jin & Branke [3] del informe. Hay que declararlas así en el informe final. |

## 7. Recomendaciones que quedaron abiertas

1. **La micro-instancia es fácil**: el statu quo ya está a 1,0 % del óptimo, así
   que un GAP de 0 % dice poco. Conviene agregar micro-instancias donde el
   statu quo quede lejos (> 5 %) del óptimo. → PENDIENTES.
2. La búsqueda aleatoria devuelve siempre el statu quo (su individuo 0): ningún
   plan sesgado al azar lo supera en 8.000 intentos. Está bien como evidencia
   de que el problema no es trivial, pero en el informe hay que decirlo así.
3. El greedy es determinista: contra él corresponde comparar la distribución
   del AE con un test de una muestra, o con el criterio
   |f̄(A) − f̄(B)| > máx(σA, σB) del Tema 12. → M5.

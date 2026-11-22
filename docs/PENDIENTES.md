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
| P03 | Shapiro-Wilk, Mann-Whitney, Holm, Wilcoxon exacto, validados contra scipy | §IV-A, §IV-B | Alen | ⬜ | D-A5 |
| P04 | Adaptador jMetal + AE generacional sobre jMetal + equivalencia con el AE propio | §III | Alen | ⬜ | D-A1, D-A2 |
| P05 | NSGA-II con jMetal (f₁ utilidad deportiva, f₂ costo neto) | §III-C | Alen | ⬜ | D-A6 |
| P06 | Métricas multiobjetivo: hipervolumen relativo, spread, GD; frente de referencia por unión de ≥ 30 corridas; estadística por métrica | §IV-C, Tema 12 | Alen | ⬜ | |
| P07 | Comparación AE vs referencias en las 3 instancias de evaluación, ≥ 30 corridas, % de mejora, tests (Shapiro → t / Mann-Whitney; Friedman + Holm), GAP | §IV-B | Alen | ⬜ | |
| P08 | Robustez del plan final con validación de 10.000 simulaciones | §IV-C | Alen | ⬜ | hoy se usa N=200 |
| P09 | Límite de tiempo de reloj como segundo criterio de parada | §III-C | Alen | ⬜ | |
| P10 | Selección proporcional con escalado (a comparar contra torneo) | §III-C | — | ⬜ | |
| P11 | Cruzamiento por bloques posicionales (a comparar contra uniforme) | §III-C | — | ⬜ | |
| P12 | Escalado de fitness | §III-C | — | ⬜ | va junto con P10 |
| P13 | Modelo de islas con migración en anillo + speedup al variar recursos | §III-C, §IV-C | — | ⬜ | Tema 11 |
| P14 | Estudio del número de réplicas N (menor N que mantiene estable el ordenamiento) | §IV-A | — | ⬜ | |
| P15 | Evaluaciones hasta alcanzar percentiles de la mejor solución | §IV-C | — | ⬜ | |
| P16 | Micro-instancias más difíciles (statu quo a > 5% del óptimo) | §IV-B(2) | — | ⬜ | VALIDACION §7.1 |
| P17 | Datos reales: cargar SoFIFA/Transfermarkt al esquema `JugadorBase`, recalibrar valor, salario y rivales | §IV-A | Juan | ⬜ | README "Lo que falta" §2 |
| P18 | Reconstruir el **plan real del club** como individuo y evaluarlo | §IV-B(1) | Juan | ⬜ | depende de P17 |
| P19 | Barrido factorial completo en ClusterUY (27 config × 2 inst × 30) | §IV-A | — | ⬜ | `Experimentos 40000 30 2`; correr con la versión jMetal cuando esté P04 |
| P20 | Verificar `mvn package` en una máquina con Maven Central | — | Juan o Alen | ⬜ | en el entorno del Claude de Alen Maven está bloqueado (D-A4) |
| P21 | Informe final (IEEE, doble columna): ampliar con implementación, calibración, instancias, validación, resultados, conclusiones | todo | ambos | ⬜ | ver "Correcciones para el informe final" abajo |
| P22 | Zip de entrega: fuentes + instancias, sin binarios ni framework | consigna | ambos | ⬜ | |

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

---

## Bitácora

Entradas nuevas arriba. Formato: fecha · quién · qué se hizo · cómo se validó · qué sigue.

### 22/11/2026 · Alen (con Claude)
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

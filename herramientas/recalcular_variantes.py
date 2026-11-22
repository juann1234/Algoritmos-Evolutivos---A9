"""Recalcula por fuera (scipy) el análisis de variantes del AE.

Informe: §IV-B (análisis estadístico) — verificación independiente pedida en
el README, paso 5 de "Lo primero: validación".

Lee resultados/resultados_variantes.csv y, para cada presupuesto, compara cada
variante contra la base con Wilcoxon de rangos con signo pareado por semilla:
  - p exacto (scipy, method="exact"), que es el correcto con n = 10;
  - p por aproximación normal, que es lo que calcula Estadistica.wilcoxon;
y aplica Bonferroni y Holm sobre las 5 comparaciones.

Uso:  python3 herramientas/recalcular_variantes.py [resultados/resultados_variantes.csv]
"""
import csv
import sys
from collections import defaultdict

import numpy as np
from scipy import stats


def holm(pvals):
    """Corrección de Holm (step-down). Devuelve p ajustados en el orden original."""
    m = len(pvals)
    orden = np.argsort(pvals)
    ajust = np.empty(m)
    acumulado = 0.0
    for rango, i in enumerate(orden):
        acumulado = max(acumulado, (m - rango) * pvals[i])
        ajust[i] = min(1.0, acumulado)
    return ajust


def main(ruta):
    datos = defaultdict(lambda: defaultdict(dict))  # presupuesto -> variante -> semilla -> fitness
    with open(ruta, newline="", encoding="utf-8") as f:
        for fila in csv.DictReader(f):
            datos[int(fila["presupuesto"])][fila["variante"]][int(fila["semilla"])] = float(fila["fitness"])

    for presupuesto, variantes in sorted(datos.items()):
        base = variantes.get("base", {})
        print(f"\n=== Presupuesto {presupuesto} evaluaciones")
        print(f"{'variante':14s}{'n':>4s}{'media':>9s}{'desvío':>8s}{'dif':>8s}"
              f"{'p exacto':>10s}{'p normal':>10s}{'Bonf(ex)':>10s}{'Holm(ex)':>10s}{'SW p':>8s}")
        filas, p_ex = [], []
        for nombre, por_semilla in variantes.items():
            v = np.array([por_semilla[s] for s in sorted(por_semilla)])
            sw = stats.shapiro(v).pvalue if len(v) >= 3 else float("nan")
            if nombre == "base":
                print(f"{nombre:14s}{len(v):4d}{v.mean():9.2f}{v.std(ddof=1):8.2f}{'':8s}{'':10s}{'':10s}{'':10s}{'':10s}{sw:8.3f}")
                continue
            comunes = sorted(set(por_semilla) & set(base))
            a = np.array([por_semilla[s] for s in comunes])
            b = np.array([base[s] for s in comunes])
            ex = stats.wilcoxon(a, b, method="exact").pvalue
            no = stats.wilcoxon(a, b, method="approx", correction=False).pvalue
            filas.append((nombre, len(comunes), a, b, ex, no, sw))
            p_ex.append(ex)
        if not filas:
            continue
        bonf = np.minimum(1.0, np.array(p_ex) * len(p_ex))
        hol = holm(np.array(p_ex))
        for (nombre, n, a, b, ex, no, sw), pb, ph in zip(filas, bonf, hol):
            print(f"{nombre:14s}{n:4d}{a.mean():9.2f}{a.std(ddof=1):8.2f}{a.mean()-b.mean():+8.2f}"
                  f"{ex:10.4f}{no:10.4f}{pb:10.4f}{ph:10.4f}{sw:8.3f}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "resultados/resultados_variantes.csv")

"""Gráficos para el informe final, a partir de los CSV de resultados/.

[Informe §IV-C: "evolución del fitness", frentes de Pareto representativos;
Tema 12: "también suelen reportarse ejemplos gráficos de FP representativos"]

Uso:  python3 herramientas/graficos.py      (deja los PNG en docs/figuras/)
Colores: paleta categórica por defecto de la guía de visualización
(azul, naranja, aqua), en orden fijo; ejes y grilla recesivos.
"""
import csv
import re
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker

AZUL, NARANJA, AQUA, GRIS = "#2a78d6", "#eb6834", "#1baf7a", "#8a8984"
TINTA, TINTA2 = "#0b0b0b", "#52514e"
OUT = Path("docs/figuras")
OUT.mkdir(parents=True, exist_ok=True)

plt.rcParams.update({
    "font.size": 9, "axes.edgecolor": "#c9c8c2", "axes.labelcolor": TINTA2,
    "xtick.color": TINTA2, "ytick.color": TINTA2, "axes.grid": True,
    "grid.color": "#ebeae6", "grid.linewidth": 0.6, "axes.spines.top": False,
    "axes.spines.right": False, "figure.dpi": 150, "legend.frameon": False,
})


def leer(ruta):
    with open(ruta, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def frente(nombre="eval-chica"):
    ruta = Path(f"resultados/mo_{nombre}_frentes_preliminar.csv")
    if not ruta.exists():
        return
    filas = leer(ruta)
    fig, ax = plt.subplots(figsize=(3.4, 3.3))
    ref = sorted([(float(r["f2_costo_neto"]), float(r["f1_utilidad"])) for r in filas if r["algoritmo"] == "referencia"])
    ax.plot([p[0] for p in ref], [p[1] for p in ref], color=GRIS, lw=1, zorder=1, label="frente de referencia")
    for alg, color, etiqueta, m in [("NSGA-II", AZUL, "NSGA-II", "o"), ("agregacion", NARANJA, "agregación (barrido de β)", "s")]:
        pts = [(float(r["f2_costo_neto"]), float(r["f1_utilidad"])) for r in filas if r["algoritmo"] == alg]
        ax.scatter([p[0] for p in pts], [p[1] for p in pts], s=10, marker=m, color=color,
                   edgecolors="white", linewidths=0.4, zorder=2, label=etiqueta)
    # Planes de referencia (P29): rombo negro el plan real, cruz el statu quo.
    for alg, etiqueta, m in [("plan_real", "plan real del club", "D"), ("statu_quo", "statu quo", "X")]:
        pts = [(float(r["f2_costo_neto"]), float(r["f1_utilidad"])) for r in filas if r["algoritmo"] == alg]
        if pts:
            ax.scatter([p[0] for p in pts], [p[1] for p in pts], s=34, marker=m, color=TINTA,
                       edgecolors="white", linewidths=0.8, zorder=3, label=etiqueta)
    ax.set_xlabel("f₂: costo neto (M, menor es mejor)")
    ax.set_ylabel("f₁: utilidad deportiva")
    ax.legend(fontsize=6.5, loc="upper center", bbox_to_anchor=(0.45, -0.22), ncol=2)
    fig.tight_layout()
    fig.savefig(OUT / f"frente_pareto_{nombre}.png")
    plt.close(fig)


def gap():
    ruta = Path("resultados/gap_micro.csv")
    if not ruta.exists():
        return
    datos = defaultdict(lambda: defaultdict(list))
    for r in leer(ruta):
        datos[r["instancia"]][int(r["presupuesto"])].append(float(r["gap_pct"]))
    fig, ax = plt.subplots(figsize=(3.4, 2.4))
    for (inst, por), color in zip(sorted(datos.items()), [AZUL, NARANJA, AQUA]):
        xs = sorted(por)
        ys = [sum(por[x]) / len(por[x]) for x in xs]
        ax.plot(xs, ys, color=color, lw=2, marker="o", ms=4, label=inst)
    ax.set_xscale("log")
    ax.set_xticks([500, 1000, 2000, 4000, 8000])
    ax.set_xticklabels(["500", "1.000", "2.000", "4.000", "8.000"])
    ax.xaxis.set_minor_formatter(matplotlib.ticker.NullFormatter())
    ax.set_xlabel("evaluaciones")
    ax.set_ylabel("GAP medio (%)")
    ax.legend(fontsize=7)
    fig.tight_layout()
    fig.savefig(OUT / "gap_micro.png")
    plt.close(fig)


def replicas():
    ruta = Path("resultados/replicas_demo.txt")
    if not ruta.exists():
        return
    texto = ruta.read_text(encoding="utf-8")
    bloques = texto.split("Parte 2")[1:]
    fig, ax = plt.subplots(figsize=(3.4, 2.4))
    for bloque, color, nombre in zip(bloques[-2:], [AZUL, NARANJA], ["240.000 simulaciones", "720.000 simulaciones"]):
        filas = re.findall(r"^\s+(\d+)\s+(\d+)\s+([\d.]+)\s+([\d.]+)", bloque, re.M)
        ns = [int(f[0]) for f in filas]
        med = [float(f[2]) for f in filas]
        sd = [float(f[3]) for f in filas]
        ax.errorbar(ns, med, yerr=sd, color=color, lw=2, marker="o", ms=4, capsize=2, label=nombre)
    ax.set_xscale("log")
    ax.set_xticks([5, 10, 20, 30, 50])
    ax.set_xticklabels(["5", "10", "20", "30", "50"])
    ax.xaxis.set_minor_formatter(matplotlib.ticker.NullFormatter())
    ax.set_xlabel("réplicas N (a igual número de simulaciones)")
    ax.set_ylabel("fitness (media ± desvío)")
    ax.legend(fontsize=7)
    fig.tight_layout()
    fig.savefig(OUT / "replicas_N.png")
    plt.close(fig)


if __name__ == "__main__":
    frente("eval-chica")
    frente("barcelona-2020-chica")
    gap()
    replicas()
    print("figuras en", OUT)

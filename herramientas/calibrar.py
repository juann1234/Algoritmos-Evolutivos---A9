#!/usr/bin/env python3
"""
Calibración del simulador contra datos reales (PENDIENTES P17, DECISIONES D-J2).

Fuentes (no se versionan por tamaño y licencia; ver README, "Datos reales"):
  - EA Sports FC 24 Complete Player Dataset (Stefano Leone, Kaggle): male_players.csv
    Una foto por edición, de FIFA 15 a FC 24, tomada en septiembre, después del
    mercado de verano.
  - Football Data from Transfermarkt (davidcariboo, Kaggle): players.csv,
    transfers.csv, appearances.csv, competitions.csv

Qué se calibra y contra qué:
  1. Salario de referencia  log(salario) = log(base) + escala * (theta - 60)
     contra el salario de FIFA 21 (5 grandes ligas, nivel >= 65).
  2. Valor de mercado (nivel y edad) contra el valor de FIFA 21, solo con
     contratos de 3 o más años, donde el factor contractual vale 1.
  3. Factor contractual: FIFA no hace depender el valor del contrato, así que
     se VALIDA contra montos reales de Transfermarkt (precio / valor según
     años de contrato restantes) en lugar de calibrarlo.
  4. Evolución del nivel: cambio de rating entre ediciones consecutivas, con
     los minutos de liga de esa temporada tomados de Transfermarkt.
     La sensibilidad a los minutos NO es identificable con estos datos: el
     rating de septiembre resume la temporada que terminó, así que una buena
     temporada sube a la vez minutos y rating (simultaneidad; los efectos
     fijos por jugador no la corrigen). Por eso minutosMediaSaturacion queda
     como supuesto (1200) y se calibran el nivel medio de crecimiento y
     declive por edad y el ruido.

Uso:  python3 herramientas/calibrar.py <carpeta_con_los_csv> [M1,M2,...]
Imprime los parámetros a copiar en Parametros.calibrados(). Con la lista
opcional de valores de minutosMediaSaturacion, recalibra además la evolución
para cada uno (análisis de sensibilidad de P31, D-J7): como el efecto de los
minutos no es identificable, todos ajustan los datos igual de bien.
"""
import re
import sys
import unicodedata

import numpy as np
import pandas as pd
from scipy.optimize import curve_fit

TOP5 = ["La Liga", "Premier League", "Serie A", "Bundesliga", "Ligue 1"]


def norm(s):
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z ]", "", s).split()


def cargar_fifa(carpeta):
    cols = ["player_id", "fifa_version", "short_name", "long_name", "overall", "potential",
            "value_eur", "wage_eur", "age", "dob", "club_name", "league_name",
            "club_contract_valid_until_year"]
    return pd.read_csv(f"{carpeta}/male_players.csv", usecols=cols, low_memory=False)


def vincular(fifa, carpeta):
    """Vincula jugadores FIFA y Transfermarkt por fecha de nacimiento y apellido."""
    tm = pd.read_csv(f"{carpeta}/players.csv", usecols=["player_id", "name", "last_name", "date_of_birth"])
    tm["dob"] = pd.to_datetime(tm.date_of_birth, errors="coerce").dt.date
    f = fifa[["player_id", "long_name", "short_name", "dob"]].drop_duplicates("player_id").copy()
    f["dob"] = pd.to_datetime(f.dob, errors="coerce").dt.date
    j = f.merge(tm.rename(columns={"player_id": "tm_id"}), on="dob")

    def coincide(r):
        apellido = norm(r.last_name if pd.notna(r.last_name) else r["name"])
        nombres = set(norm(r.long_name)) | set(norm(r.short_name))
        return len(apellido) > 0 and all(t in nombres for t in apellido)

    j = j[j.apply(coincide, axis=1)]
    unico = ~(j.player_id.duplicated(keep=False) | j.tm_id.duplicated(keep=False))
    return j[unico][["player_id", "tm_id"]]


def calibrar_salario(fifa):
    d = fifa[(fifa.fifa_version == 21) & fifa.league_name.isin(TOP5) & (fifa.wage_eur > 0) & (fifa.overall >= 65)]
    y = np.log(d.wage_eur * 52 / 1e6)
    X = np.c_[np.ones(len(d)), d.overall - 60]
    c, *_ = np.linalg.lstsq(X, y, rcond=None)
    r2 = 1 - ((y - X @ c) ** 2).sum() / ((y - y.mean()) ** 2).sum()
    return {"salarioBase": np.exp(c[0]), "salarioEscala": c[1], "R2": r2, "n": len(d)}


def calibrar_valor(fifa):
    d = fifa[(fifa.fifa_version == 21) & fifa.league_name.isin(TOP5) & (fifa.value_eur > 0)
             & (fifa.overall >= 65) & (fifa.club_contract_valid_until_year - 2020 >= 3)]

    def log_valor(X, lb, k, tope, pend, centro):
        th, ed = X
        return lb + k * (th - 70) + np.log(tope / (1 + np.exp(pend * (ed - centro))))

    y = np.log(d.value_eur.values / 1e6)
    p, _ = curve_fit(log_valor, (d.overall.values, d.age.values), y,
                     p0=[np.log(8), 0.14, 1.25, 0.35, 28.5], maxfev=20000)
    r2 = 1 - ((y - log_valor((d.overall.values, d.age.values), *p)) ** 2).sum() / ((y - y.mean()) ** 2).sum()
    return {"valorBase": np.exp(p[0]), "thetaEscala": p[1], "edadTope": p[2],
            "edadPendiente": p[3], "edadCentro": p[4], "R2": r2, "n": len(d)}


def validar_factor_contrato(fifa, vinculo, carpeta):
    t = pd.read_csv(f"{carpeta}/transfers.csv", parse_dates=["transfer_date"])
    t = t[(t.transfer_fee > 0) & (t.market_value_in_eur > 0) & t.transfer_date.dt.month.isin([6, 7, 8, 9])]
    t["anio"] = t.transfer_date.dt.year
    t = t.merge(vinculo.rename(columns={"tm_id": "player_id", "player_id": "fifa_id"}), on="player_id")
    f = fifa[["player_id", "fifa_version", "club_contract_valid_until_year"]].rename(columns={"player_id": "fifa_id"})
    f["anio"] = 2000 + f.fifa_version
    t = t.merge(f, on=["fifa_id", "anio"])
    t["restan"] = (t.club_contract_valid_until_year - t.anio).clip(lower=0, upper=4)
    g = (t.transfer_fee / t.market_value_in_eur).groupby(t.restan).median()
    ref = g.loc[3:].mean()
    return {"1 año": g.loc[1] / ref, "2 años": g.loc[2] / ref,
            "modelo 1 año": 0.45 + 0.55 / 3, "modelo 2 años": 0.45 + 1.1 / 3}


def calibrar_evolucion(fifa, vinculo, carpeta, minutos_saturacion=1200.0):
    a = fifa[["player_id", "fifa_version", "age", "overall"]]
    b = a.copy()
    b["fifa_version"] -= 1
    m = a.merge(b[["player_id", "fifa_version", "overall"]], on=["player_id", "fifa_version"],
                suffixes=("", "_sig"))
    m["delta"] = m.overall_sig - m.overall
    m = m[m.overall >= 60].merge(vinculo, on="player_id")

    comp = pd.read_csv(f"{carpeta}/competitions.csv")
    ligas = set(comp[comp.type == "domestic_league"].competition_id)
    ap = pd.read_csv(f"{carpeta}/appearances.csv", usecols=["player_id", "date", "competition_id", "minutes_played"])
    ap = ap[ap.competition_id.isin(ligas)]
    ap["date"] = pd.to_datetime(ap.date)
    ap["temporada"] = np.where(ap.date.dt.month >= 7, ap.date.dt.year, ap.date.dt.year - 1)
    mins = (ap.groupby(["player_id", "temporada"]).minutes_played.sum().rename("minutos")
            .reset_index().rename(columns={"player_id": "tm_id"}))
    m["temporada"] = 2000 + m.fifa_version - 1
    m = m.merge(mins, on=["tm_id", "temporada"])
    m = m[m.age.between(17, 37)]
    m["minutos"] = m.minutos.clip(upper=3420)

    def modelo(X, G, D, E, A0):
        ed, mi = X
        return (G * np.clip((26 - ed) / (26 - 16), 0, 1) * mi / (mi + minutos_saturacion)
                - D * np.maximum(0, ed - A0) ** E)

    p, _ = curve_fit(modelo, (m.age.values, m.minutos.values), m.delta.values, p0=[4, 0.3, 1.3, 28],
                     bounds=([0, 0, 0.5, 24], [15, 3, 3, 33]), maxfev=50000)
    res = m.delta.values - modelo((m.age.values, m.minutos.values), *p)
    return {"crecimientoMax": p[0], "minutosMediaSaturacion (supuesto)": minutos_saturacion,
            "decliveCoef": p[1], "decliveExp": p[2], "edadInicioDeclive": p[3],
            "sigmaTheta": res.std(), "n": len(m)}


def main():
    carpeta = sys.argv[1] if len(sys.argv) > 1 else "."
    fifa = cargar_fifa(carpeta)
    vinculo = vincular(fifa, carpeta)
    print(f"Jugadores vinculados FIFA-Transfermarkt: {len(vinculo)}\n")
    for titulo, res in (("SALARIO", calibrar_salario(fifa)),
                        ("VALOR", calibrar_valor(fifa)),
                        ("FACTOR CONTRACTUAL (validación con montos reales)",
                         validar_factor_contrato(fifa, vinculo, carpeta)),
                        ("EVOLUCIÓN DEL NIVEL", calibrar_evolucion(fifa, vinculo, carpeta))):
        print(titulo)
        for k, v in res.items():
            print(f"  {k:36s} {v:.4f}" if isinstance(v, float) else f"  {k:36s} {v}")
        print()
    if len(sys.argv) > 2:
        print("SENSIBILIDAD A LOS MINUTOS (D-J7)")
        for M in (float(x) for x in sys.argv[2].split(",")):
            r = calibrar_evolucion(fifa, vinculo, carpeta, minutos_saturacion=M)
            print(f"  minutosMediaSaturacion={M:7.0f}  crecimientoMax={r['crecimientoMax']:.3f}  "
                  f"sigmaTheta={r['sigmaTheta']:.3f}")


if __name__ == "__main__":
    main()

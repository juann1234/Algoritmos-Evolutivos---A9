#!/usr/bin/env python3
"""
Instancias reales y plan real del club (PENDIENTES P17 y P18, DECISIONES D-J4).

Caso: FC Barcelona desde la foto de FIFA 21 (23/09/2020), horizonte de tres
temporadas: 2020-21, 2021-22 y 2022-23, con sus seis ventanas.

  V1 resto del verano 2020 (23/09 al cierre)   V2 invierno 2021
  V3 verano 2021                                V4 invierno 2022
  V5 verano 2022                                V6 invierno 2023

Salidas (carpeta instancias/):
  barcelona-2020-{chica,media,grande}.csv   plantel propio (25) + mercado (50, 150, 400)
  plan_real_barcelona.csv                    lo que el club hizo, en el formato del cromosoma

Plantel propio: los 23 jugadores del primer equipo en FIFA 21 más Pedri y
Araujo, que figuran como reserva pero jugaron en el primer equipo en 2020-21.

Mercado: los 16 jugadores que el club efectivamente incorporó en el horizonte
y figuran en FIFA 21, más una muestra aleatoria (semilla fija) de jugadores
de nivel 74 a 92 y hasta 33 años de las cinco grandes ligas, Portugal y
Holanda. Las tres instancias están anidadas: la chica está contenida en la
media y la media en la grande.

Limitaciones que hay que declarar en el informe:
  - Los canteranos que subieron después (Gavi, Balde, Nico González,
    Mingueza) no están en FIFA 21: no los puede usar ni el plan real ni el AE.
  - El cromosoma admite una acción por jugador. Si un cedido nunca volvió a
    jugar en el club dentro del horizonte, se codifica como venta (si después
    se lo vendió) o liberación (si se fue gratis) en la ventana en que salió.
  - Los montos del modelo no son los de Transfermarkt: el plan real se evalúa
    con los precios, salarios y la incertidumbre del simulador, igual que el AE.

Uso:  python3 herramientas/preparar_instancias.py <carpeta_con_male_players.csv> [salida]
"""
import os
import sys

import numpy as np
import pandas as pd

LIGAS = ["La Liga", "Premier League", "Serie A", "Bundesliga", "Ligue 1", "Liga Portugal", "Eredivisie"]
TAMANIOS = {"chica": 50, "media": 150, "grande": 400}
SEMILLA = 2020
ANIO_BASE = 2020                     # FIFA 21: foto de septiembre de 2020
ANIO_VENTANA = {1: 2020, 2: 2020, 3: 2021, 4: 2021, 5: 2022, 6: 2022}   # temporada que arranca

PROPIOS_RESERVA = ["Pedri", "R. Araujo"]

# Plan real de los propios: (short_name en FIFA 21, acción, ventana, justificación)
PLAN_PROPIOS = [
    ("L. Messi", "MANTENER", 1, "contrato hasta 2021; se fue libre al PSG (ago-2021) al no poder renovar"),
    ("M. ter Stegen", "RENOVAR", 1, "renovó en oct-2020"),
    ("L. Suárez", "LIBERAR", 1, "rescisión en sept-2020, al Atlético"),
    ("Sergio Busquets", "MANTENER", 1, "siguió con su contrato"),
    ("A. Griezmann", "VENDER", 3, "cedido al Atlético (ago-2021) con obligación de compra; no volvió"),
    ("Piqué", "RENOVAR", 1, "renovó en oct-2020"),
    ("Jordi Alba", "MANTENER", 1, "siguió con su contrato"),
    ("F. de Jong", "MANTENER", 1, "siguió con su contrato"),
    ("C. Lenglet", "CEDER", 5, "cedido al Tottenham 2022-23, volvió en jun-2023"),
    ("M. Pjanić", "LIBERAR", 3, "cedido al Besiktas 2021-22 y después se fue libre; no volvió a jugar"),
    ("O. Dembélé", "RENOVAR", 4, "contrato hasta 2022; renovó en 2022"),
    ("S. Umtiti", "CEDER", 5, "cedido al Lecce 2022-23, volvió en jun-2023"),
    ("Nélson Semedo", "VENDER", 1, "vendido al Wolves (32M, sept-2020)"),
    ("Coutinho", "VENDER", 4, "cedido al Aston Villa (ene-2022) y vendido (jul-2022); no volvió"),
    ("Sergi Roberto", "RENOVAR", 4, "contrato hasta 2022; renovó"),
    ("Neto", "LIBERAR", 5, "se fue libre al Bournemouth (ago-2022)"),
    ("Junior Firpo", "VENDER", 3, "vendido al Leeds (15M, jul-2021)"),
    ("Rafinha", "LIBERAR", 1, "se fue al PSG en oct-2020"),
    ("Trincão", "VENDER", 3, "cedido al Wolves y al Sporting, vendido en 2023; no volvió"),
    ("Ansu Fati", "RENOVAR", 3, "renovó en oct-2021"),
    ("M. Braithwaite", "LIBERAR", 5, "rescisión en sept-2022"),
    ("Riqui Puig", "RENOVAR", 2, "contrato hasta jun-2021: la renovación tiene que ser antes (V2); siguió hasta 2022 y se fue libre"),
    ("J. Todibo", "VENDER", 1, "cedido al Benfica y al Niza, vendido en 2021; no volvió"),
    ("Pedri", "RENOVAR", 3, "renovó en oct-2021"),
    ("R. Araujo", "RENOVAR", 3, "renovó en 2021"),
]

# Plan real del mercado: (short_name en FIFA 21, club en FIFA 21, acción, ventana, justificación)
PLAN_MERCADO = [
    ("S. Dest", "Ajax", "COMPRAR", 1, "comprado al Ajax (21M, oct-2020)"),
    ("S. Agüero", "Manchester City", "COMPRAR", 3, "libre (jul-2021)"),
    ("M. Depay", "Olympique Lyonnais", "COMPRAR", 3, "libre (jul-2021)"),
    ("Eric García", "Manchester City", "COMPRAR", 3, "libre (jul-2021)"),
    ("L. de Jong", "Sevilla FC", "INCORPORAR_PRESTAMO", 3, "cedido por el Sevilla (ago-2021)"),
    ("Y. Demir", "SK Rapid Wien", "INCORPORAR_PRESTAMO", 3, "cedido por el Rapid (jul-2021)"),
    ("Ferran Torres", "Manchester City", "COMPRAR", 4, "comprado al City (55M, ene-2022)"),
    ("Adama Traoré", "Wolverhampton Wanderers", "INCORPORAR_PRESTAMO", 4, "cedido por el Wolves (ene-2022)"),
    ("P. Aubameyang", "Arsenal", "COMPRAR", 4, "libre tras rescindir con el Arsenal (ene-2022)"),
    ("R. Lewandowski", "FC Bayern München", "COMPRAR", 5, "comprado al Bayern (45M, jul-2022)"),
    ("J. Koundé", "Sevilla FC", "COMPRAR", 5, "comprado al Sevilla (50M, jul-2022)"),
    ("F. Kessié", "Milan", "COMPRAR", 5, "libre (jul-2022)"),
    ("Raphinha", "Stade Rennais FC", "COMPRAR", 5, "comprado al Leeds (58M, jul-2022)"),
    ("A. Christensen", "Chelsea", "COMPRAR", 5, "libre (jul-2022)"),
    ("Héctor Bellerín", "Arsenal", "COMPRAR", 5, "libre tras rescindir con el Arsenal (sept-2022)"),
    ("Marcos Alonso", "Chelsea", "COMPRAR", 5, "libre tras rescindir con el Chelsea (sept-2022)"),
]


def puesto(posiciones):
    p = str(posiciones).split(",")[0].strip()
    if p == "GK":
        return "POR"
    if p in ("CB", "LB", "RB", "LWB", "RWB"):
        return "DEF"
    if p in ("CDM", "CM", "CAM", "LM", "RM"):
        return "MED"
    return "DEL"


def buscar(df, nombre, club=None):
    """Una fila por nombre corto (y club, si hay homónimos)."""
    m = df[df.short_name == nombre]
    if club is not None and len(m) > 1:
        m = m[m.club_name.str.contains(club.split()[0], na=False)]
    if len(m) != 1:
        raise ValueError(f"no encuentro a {nombre} ({club}) de forma única: {len(m)} filas")
    return m.iloc[0]


def fila_instancia(r, pertenencia):
    anios = int(max(1, (r.club_contract_valid_until_year or ANIO_BASE + 1) - ANIO_BASE))
    valor = r.value_eur / 1e6 if r.value_eur > 0 else 0.5
    clausula = r.release_clause_eur / 1e6 if pd.notna(r.release_clause_eur) else 4.0 * valor
    return {
        "id": int(r.player_id), "nombre": r.short_name, "puesto": puesto(r.player_positions),
        "pertenencia": pertenencia, "edad": int(r.age), "theta": float(r.overall),
        "potencial": float(max(r.potential, r.overall)), "anios_contrato": anios,
        "salario": round(r.wage_eur * 52 / 1e6, 4),
        "clausula": round(clausula if pertenencia == "PROPIO" else 0.0, 3),
        "historial_lesiones": 0.0, "club_origen": r.club_name,
    }


# Salario de referencia calibrado (debe coincidir con Parametros.calibrados()).
SALARIO_BASE = 0.0888
SALARIO_ESCALA = 0.1727


def salario_referencia(theta):
    return SALARIO_BASE * np.exp(SALARIO_ESCALA * (theta - 60))


# Primera foto de FIFA posterior a cada ventana (las fotos son de septiembre).
FOTO_POSTERIOR = {1: 22, 2: 22, 3: 22, 4: 23, 5: 23, 6: 24}

# Contratos que ninguna foto registra porque el jugador se fue antes de la
# siguiente: (duración en temporadas, según Transfermarkt).
DURACION_MANUAL = {"P. Aubameyang": 4}


def condiciones(f, fila21, ventana):
    """Duración, multiplicador salarial y de cláusula del contrato firmado.

    El multiplicador salarial es salario en el Barcelona / salario de
    referencia de su nivel en esa foto.

    Se leen de las fotos de FIFA posteriores a la operación (hasta FIFA 23, el
    fin del horizonte) en que el jugador figura en el Barcelona, tomando la de
    contrato más largo: algunas renovaciones (Pedri, Ansu Fati) se firmaron
    después de la foto inmediata y recién aparecen en la siguiente."""
    filas = f[(f.fifa_version >= FOTO_POSTERIOR[ventana]) & (f.fifa_version <= 23)
              & (f.player_id == fila21.player_id) & (f.club_name == "FC Barcelona")]
    if len(filas) == 0:
        dur = DURACION_MANUAL.get(fila21.short_name, 1)
        return dur, 1.0, 3.0
    x = filas.sort_values("club_contract_valid_until_year").iloc[-1]
    dur = int(np.clip((x.club_contract_valid_until_year or 0) - ANIO_VENTANA[ventana], 1, 5))
    # El gen expresa el salario como múltiplo del de referencia del nivel del
    # jugador (D-J5), no de su salario anterior.
    sal = float(np.clip((x.wage_eur * 52 / 1e6) / salario_referencia(x.overall), 0.7, 1.6))
    cla = float(np.clip((x.release_clause_eur / x.value_eur)
                        if pd.notna(x.release_clause_eur) and x.value_eur > 0 else 3.0, 1.0, 10.0))
    return dur, round(sal, 3), round(cla, 3)


def main():
    carpeta = sys.argv[1] if len(sys.argv) > 1 else "."
    salida = sys.argv[2] if len(sys.argv) > 2 else "instancias"
    os.makedirs(salida, exist_ok=True)
    cols = ["player_id", "fifa_version", "short_name", "player_positions", "overall", "potential",
            "value_eur", "wage_eur", "age", "club_name", "league_name", "club_position",
            "club_contract_valid_until_year", "release_clause_eur"]
    f = pd.read_csv(f"{carpeta}/male_players.csv", usecols=cols, low_memory=False)
    f21 = f[f.fifa_version == 21]

    # ---------------- plantel propio ----------------
    bar = f21[f21.club_name == "FC Barcelona"]
    propios = bar[(bar.club_position != "RES") | bar.short_name.isin(PROPIOS_RESERVA)]
    assert len(propios) == 25, f"se esperaban 25 propios, hay {len(propios)}"
    filas = [fila_instancia(r, "PROPIO") for _, r in propios.iterrows()]

    # ---------------- mercado ----------------
    reales = [buscar(f21, n, c) for n, c, *_ in PLAN_MERCADO]
    ids_reales = {int(r.player_id) for r in reales}
    pool = f21[f21.league_name.isin(LIGAS) & f21.overall.between(74, 92) & (f21.age <= 33)
               & (f21.club_name != "FC Barcelona") & ~f21.player_id.isin(ids_reales)]
    pool = pool.sample(frac=1.0, random_state=SEMILLA)
    mercado_reales = [fila_instancia(r, "MERCADO") for r in reales]

    masa = sum(x["salario"] for x in filas)
    for nombre, n in TAMANIOS.items():
        extra = [fila_instancia(r, "MERCADO") for _, r in pool.head(n - len(mercado_reales)).iterrows()]
        tabla = pd.DataFrame(filas + mercado_reales + extra)
        ruta = f"{salida}/barcelona-2020-{nombre}.csv"
        with open(ruta, "w", encoding="utf-8") as fh:
            fh.write(f"# nombre=barcelona-2020-{nombre}\n")
            fh.write("# fuente=EA Sports FC 24 Complete Player Dataset (FIFA 21) + Transfermarkt; ver herramientas/preparar_instancias.py\n")
            fh.write(f"# presupuesto={PRESUPUESTO}\n")
            fh.write(f"# tope_masa_salarial={round(masa * HOLGURA_SALARIAL, 2)}\n")
            fh.write("# cupo_fichas=25\n")
            tabla.to_csv(fh, index=False)
        print(f"{ruta}: {len(filas)} propios + {len(tabla) - len(filas)} candidatos")

    # ---------------- plan real ----------------
    plan = []
    for nombre, accion, ventana, nota in PLAN_PROPIOS:
        r = buscar(propios, nombre)
        dur, sal, cla = (condiciones(f, r, ventana) if accion == "RENOVAR" else (1, 1.0, 3.0))
        plan.append({"id": int(r.player_id), "nombre": nombre, "accion": accion, "ventana": ventana,
                     "duracion": dur, "salario": sal, "clausula": cla, "justificacion": nota})
    for (nombre, club, accion, ventana, nota), r in zip(PLAN_MERCADO, reales):
        dur, sal, cla = (condiciones(f, r, ventana) if accion == "COMPRAR" else (1, 1.0, 3.0))
        plan.append({"id": int(r.player_id), "nombre": nombre, "accion": accion, "ventana": ventana,
                     "duracion": dur, "salario": sal, "clausula": cla, "justificacion": nota})
    pd.DataFrame(plan).to_csv(f"{salida}/plan_real_barcelona.csv", index=False)
    print(f"{salida}/plan_real_barcelona.csv: {len(plan)} operaciones "
          f"({sum(p['accion'] != 'MANTENER' for p in plan)} distintas de mantener)")
    print(f"masa salarial del plantel propio: {masa:.1f}M; tope: {masa * HOLGURA_SALARIAL:.1f}M; presupuesto: {PRESUPUESTO}M")


# Economía de la instancia (D-J4): el tope salarial es la masa del plantel con
# una holgura chica (el club estaba en el límite del control económico de la
# liga) y el presupuesto de traspasos es del orden del gasto neto real del
# período. Se fijan acá para que las tres instancias compartan economía.
HOLGURA_SALARIAL = 1.10
PRESUPUESTO = 150.0

if __name__ == "__main__":
    main()

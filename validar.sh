#!/usr/bin/env bash
# Validación rápida del proyecto: compila y corre los chequeos que no tardan.
# Cada paso imprime los valores esperados para comparar (ver README, sección
# "Validación").
set -e
cd "$(dirname "$0")"

echo "== 1. Compilación"
rm -rf out && mkdir out
javac -Xlint:all -d out $(find src -name '*.java')
echo "   OK"

echo
echo "== 2. Tests estadísticos contra scipy"
java -cp out ae.plantel.ValidarEstadistica

echo
echo "== 3. Agujeros del simulador (regresión)"
java -cp out ae.plantel.ValidarAgujeros

echo
echo "== 4. Decodificador y fitness"
java -cp out ae.plantel.ValidarPlan

echo
echo "== 5. AE contra statu quo y búsqueda aleatoria (esperado: 211.60 / 211.60 / 233.28)"
java -cp out ae.plantel.EjecutarAE 8000 1

echo
echo "== 6. Greedy miope (esperado: 219.35)"
java -cp out ae.plantel.GreedyMiope 8000

echo
echo "== 7. Óptimo exacto en micro-instancia (esperado: GAP 0.00% el mejor de 10)"
java -cp out ae.plantel.OptimoExacto 4 2

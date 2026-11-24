#!/bin/bash
# Corridas completas de la evaluación experimental en ClusterUY (SLURM).
# [Informe §IV-A (configuración paramétrica), §IV-B y §IV-C] · PENDIENTES P19 y P23
#
# SIN PROBAR en ClusterUY (escrito por Alen/Claude, que no tiene acceso al
# cluster): revisar partición, qos, módulo de Java y límites antes de mandar.
#
# Uso:
#   1. En tu máquina: ./compilar.sh  (deja out/ y lib/ con jMetal)
#   2. Copiar el repo entero (con out/ y lib/) al cluster.
#   3. sbatch herramientas/clusteruy.sh <experimento>
#      experimento: factorial | referencias | mo-chica | mo-media | mo-grande | islas
#                   | referencias-reales | mo-real-chica | mo-real-media | mo-real-grande
#
# Costos estimados con el protocolo final (N = 10, 120.000 evaluaciones; D-A13),
# medidos en un Xeon de 2,1 GHz: 1,2 / 1,9 / 3,6 ms por evaluación en las
# instancias chica / media / grande.
#   factorial   : 27 configuraciones x 2 instancias x 30 corridas  ~ 54 h-núcleo
#   referencias : 3 instancias x 30 corridas x (AE + aleatoria)    ~ 14 h-núcleo
#   mo-*        : 30 corridas x (NSGA-II + agregación), por instancia ~ 2-7 h-núcleo
#   islas       : speedup con 1..k hilos + calidad, 30 semillas     ~ 3 h-núcleo
#
#SBATCH --job-name=plantel-ae
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=32
#SBATCH --mem=16G
#SBATCH --time=12:00:00
#SBATCH --output=plantel-%x-%j.out

set -e
cd "${SLURM_SUBMIT_DIR:-.}"
# module load java/21   # <- ajustar al nombre del módulo en ClusterUY
CP="$(cat out/classpath.txt)"
JAVA="java -Xmx12g -cp $CP"

case "${1:-referencias}" in
    factorial)   $JAVA ae.plantel.Experimentos 120000 30 2 ;;
    referencias) $JAVA ae.plantel.jmetal.CompararReferencias 120000 30 eval-chica eval-media eval-grande ;;
    mo-chica)    $JAVA ae.plantel.jmetal.ExperimentoMO 120000 30 eval-chica ;;
    mo-media)    $JAVA ae.plantel.jmetal.ExperimentoMO 120000 30 eval-media ;;
    mo-grande)   $JAVA ae.plantel.jmetal.ExperimentoMO 120000 30 eval-grande ;;
    islas)       $JAVA ae.plantel.jmetal.ExperimentoIslas 30 120000 8 eval-chica ;;
    # Instancias reales del Barcelona con parámetros calibrados y el plan real (P29, D-A18)
    referencias-reales) $JAVA ae.plantel.jmetal.CompararReferencias 120000 30 barcelona-2020-chica barcelona-2020-media barcelona-2020-grande ;;
    mo-real-chica)  $JAVA ae.plantel.jmetal.ExperimentoMO 120000 30 barcelona-2020-chica ;;
    mo-real-media)  $JAVA ae.plantel.jmetal.ExperimentoMO 120000 30 barcelona-2020-media ;;
    mo-real-grande) $JAVA ae.plantel.jmetal.ExperimentoMO 120000 30 barcelona-2020-grande ;;
    *) echo "experimento desconocido: $1" >&2; exit 1 ;;
esac

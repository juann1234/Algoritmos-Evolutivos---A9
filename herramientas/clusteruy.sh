#!/bin/bash -l
# Corridas completas de la evaluación experimental en ClusterUY (SLURM).
# [Informe §IV-A (configuración paramétrica), §IV-B y §IV-C] · PENDIENTES P19 y P23
#
# Uso (todo en el nodo de acceso de ClusterUY):
#   1. git clone del repo y, una sola vez:  bash herramientas/preparar_cluster.sh
#      (consigue Java 21 y Maven, compila y baja jMetal a lib/)
#   2. Prueba corta (qos rapida, máximo 30 min):
#        sbatch --qos=rapida --time=00:30:00 --cpus-per-task=8 herramientas/clusteruy.sh prueba
#   3. Corridas:  sbatch herramientas/clusteruy.sh <experimento>
#      experimento: prueba | factorial | referencias | mo-chica | mo-media | mo-grande | islas
#                   | referencias-reales | mo-real-chica | mo-real-media | mo-real-grande
#   4. Seguimiento:  squeue --me   ·   salida en plantel-<nombre>-<jobid>.out
#
# Límites de ClusterUY (documentación del centro): qos normal hasta 5 días y
# 40 CPUs por usuario; qos rapida hasta 30 min; besteffort usa recursos
# expropiables. Con 32 CPUs por trabajo, en normal corre un trabajo a la vez;
# para mandar más en paralelo:  sbatch --partition=besteffort --qos=besteffort ...
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
#SBATCH --partition=normal
#SBATCH --qos=normal
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=32
#SBATCH --mem=16G
#SBATCH --time=12:00:00
#SBATCH --output=plantel-%x-%j.out

set -e
cd "${SLURM_SUBMIT_DIR:-.}"
[ -n "$(type -t module)" ] || source /etc/profile.d/modules.sh 2>/dev/null || true
# Java 21 que dejó preparar_cluster.sh (módulo del cluster o JDK propio).
[ -f "$HOME/herramientas/entorno.sh" ] && source "$HOME/herramientas/entorno.sh"
if [ ! -f out/classpath.txt ]; then
    echo "Falta compilar: correr antes  bash herramientas/preparar_cluster.sh" >&2
    exit 1
fi
echo "nodo: $(hostname) · CPUs: ${SLURM_CPUS_PER_TASK:-?} · $(java -version 2>&1 | head -1)"
CP="$(cat out/classpath.txt)"
JAVA="java -Xmx12g -XX:ActiveProcessorCount=${SLURM_CPUS_PER_TASK:-1} -cp $CP"

case "${1:-referencias}" in
    # Prueba de humo: lo mismo que referencias-reales pero minúsculo (pocos minutos).
    prueba)      $JAVA ae.plantel.jmetal.CompararReferencias 1000 2 barcelona-2020-chica ;;
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

#!/bin/bash -l
# Preparación de ClusterUY (se corre UNA vez, en el nodo de acceso).
# [PENDIENTES P20 y P27]
#
# El home de ClusterUY tiene cuota (del orden de 1 GB) y un JDK más jMetal no
# entran. Por eso TODO lo pesado va a $BASE, que por defecto es un directorio
# propio en /tmp (disco local del nodo, sin cuota). El repo se queda en el home;
# solo el JDK, Maven, las clases compiladas y los .jar de jMetal van a /tmp.
#
# OJO: /tmp es local a la máquina y se borra al reiniciarla. En ClusterUY el
# nodo de acceso es siempre el mismo (login), así que entre que preparás y
# mandás los trabajos no se pierde; pero si pasan días, puede haberse limpiado:
# en ese caso, volver a correr este script (es rápido salvo la primera bajada).
#
# Uso (desde la raíz del repo, en el nodo de acceso):
#   bash herramientas/preparar_cluster.sh
set -e
REPO="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO"
BASE="${PLANTEL_BASE:-/tmp/$USER/plantel-ae}"
mkdir -p "$BASE"
echo "Repo:  $REPO"
echo "Base (JDK, Maven, jMetal, clases):  $BASE"
echo "Espacio en esa base:"; df -h "$BASE" | tail -1

version_java() { "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'; }

# ---------- 1. Java 21 ----------
[ -n "$(type -t module)" ] || source /etc/profile.d/modules.sh 2>/dev/null || true
JAVA_BIN=""; MOD_JAVA=""
if command -v module >/dev/null 2>&1; then
    for m in $(module -t avail 2>&1 | grep -iE '^(java|jdk|openjdk)' || true); do
        module load "$m" 2>/dev/null || continue
        if command -v java >/dev/null && [ "$(version_java java)" -ge 21 ] 2>/dev/null; then
            JAVA_BIN="$(dirname "$(command -v java)")"; MOD_JAVA="module load $m"
            echo "Java 21+ desde el módulo: $m"; break
        fi
        module unload "$m" 2>/dev/null || true
    done
fi
if [ -z "$JAVA_BIN" ] && command -v java >/dev/null && [ "$(version_java java)" -ge 21 ] 2>/dev/null; then
    JAVA_BIN="$(dirname "$(command -v java)")"; echo "Java 21+ del sistema: $JAVA_BIN"
fi
if [ -z "$JAVA_BIN" ]; then
    if [ ! -x "$BASE/jdk-21/bin/java" ]; then
        echo "No hay Java 21: bajando un JDK 21 a $BASE/jdk-21 ..."
        curl -fsSL -o "$BASE/jdk21.tar.gz" \
            "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
        mkdir -p "$BASE/jdk-21" && tar -xzf "$BASE/jdk21.tar.gz" -C "$BASE/jdk-21" --strip-components=1
        rm -f "$BASE/jdk21.tar.gz"
    fi
    JAVA_BIN="$BASE/jdk-21/bin"; echo "Java 21 propio: $JAVA_BIN"
fi
export PATH="$JAVA_BIN:$PATH"; export JAVA_HOME="$(dirname "$JAVA_BIN")"
java -version

# ---------- 2. Maven (a /tmp, con su repositorio local también en /tmp) ----------
MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
    if [ ! -x "$BASE/maven/bin/mvn" ]; then
        echo "No hay Maven: bajando Maven 3.9.9 a $BASE/maven ..."
        curl -fsSL -o "$BASE/maven.tar.gz" \
            "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
        mkdir -p "$BASE/maven" && tar -xzf "$BASE/maven.tar.gz" -C "$BASE/maven" --strip-components=1
        rm -f "$BASE/maven.tar.gz"
    fi
    MVN="$BASE/maven/bin/mvn"
fi
# El repositorio local de Maven (~/.m2, que puede ser enorme) también a /tmp.
MVN="$MVN -Dmaven.repo.local=$BASE/m2"
$MVN -version | head -1

# ---------- 3. jMetal a lib/ y compilación a $BASE/out ----------
echo "Bajando jMetal a lib/ con Maven ..."
$MVN -q dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime
CP=$(ls lib/*.jar | tr '\n' ':' | sed 's/:$//')
OUT="$BASE/out"
rm -rf "$OUT" && mkdir -p "$OUT"
echo "Compilando en $OUT ..."
javac -encoding UTF-8 -cp "$CP" -d "$OUT" $(find src -name '*.java')
echo "$OUT:$CP" > "$OUT/classpath.txt"
echo "Compilado. $(ls "$OUT"/ae/plantel/*.class | wc -l) clases."

# ---------- 4. Entorno para los trabajos ----------
mkdir -p "$BASE"
{
    echo "# generado por preparar_cluster.sh"
    echo "export PLANTEL_BASE=\"$BASE\""
    [ -n "$MOD_JAVA" ] && echo "$MOD_JAVA"
    echo "export PATH=\"$JAVA_BIN:\$PATH\""
    echo "export JAVA_HOME=\"$JAVA_HOME\""
} > "$BASE/entorno.sh"
echo
echo "Listo. El JDK/jMetal/clases viven en $BASE (local, sin cuota)."
echo "Uso en el home:"; du -sh "$REPO" 2>/dev/null
echo
echo "Probá con:"
echo "  sbatch --qos=rapida --time=00:30:00 --cpus-per-task=8 herramientas/clusteruy.sh prueba"

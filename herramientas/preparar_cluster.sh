#!/bin/bash -l
# Preparación de ClusterUY (se corre UNA vez, en el nodo de acceso).
# [PENDIENTES P20 y P27]
#
# Compilar en el nodo de acceso está permitido (lo prohibido es ejecutar
# cálculos ahí). Este script:
#   1. busca Java 21 (módulo del cluster o el del sistema) y, si no hay, baja
#      un JDK 21 a ~/herramientas/jdk-21;
#   2. busca Maven y, si no hay, lo baja a ~/herramientas/maven;
#   3. compila con ./compilar.sh, que baja jMetal a lib/ con Maven (P20);
#   4. deja en ~/herramientas/entorno.sh las variables que usa clusteruy.sh.
#
# Uso (desde la raíz del repo, en el nodo de acceso):
#   bash herramientas/preparar_cluster.sh
set -e
cd "$(dirname "$0")/.."
H="$HOME/herramientas"
mkdir -p "$H"

version_java() {
    "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'
}

# ---------- 1. Java 21 ----------
[ -n "$(type -t module)" ] || source /etc/profile.d/modules.sh 2>/dev/null || true
JAVA_BIN=""
if command -v module >/dev/null 2>&1; then
    for m in $(module -t avail 2>&1 | grep -iE '^(java|jdk|openjdk)' || true); do
        module load "$m" 2>/dev/null || continue
        if command -v java >/dev/null && [ "$(version_java java)" -ge 21 ] 2>/dev/null; then
            JAVA_BIN="$(dirname "$(command -v java)")"
            echo "Java 21+ desde el módulo $m"
            echo "module load $m" > "$H/modulo_java"
            break
        fi
        module unload "$m" 2>/dev/null || true
    done
fi
if [ -z "$JAVA_BIN" ] && command -v java >/dev/null && [ "$(version_java java)" -ge 21 ] 2>/dev/null; then
    JAVA_BIN="$(dirname "$(command -v java)")"
    echo "Java 21+ del sistema: $JAVA_BIN"
fi
if [ -z "$JAVA_BIN" ]; then
    if [ ! -x "$H/jdk-21/bin/java" ]; then
        echo "No hay Java 21: bajando un JDK 21 a $H/jdk-21 ..."
        curl -fsSL -o "$H/jdk21.tar.gz" \
            "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
        mkdir -p "$H/jdk-21" && tar -xzf "$H/jdk21.tar.gz" -C "$H/jdk-21" --strip-components=1
        rm -f "$H/jdk21.tar.gz"
    fi
    JAVA_BIN="$H/jdk-21/bin"
    echo "Java 21 propio: $JAVA_BIN"
fi
export PATH="$JAVA_BIN:$PATH"
export JAVA_HOME="$(dirname "$JAVA_BIN")"
java -version

# ---------- 2. Maven ----------
if ! command -v mvn >/dev/null 2>&1; then
    if [ ! -x "$H/maven/bin/mvn" ]; then
        echo "No hay Maven: bajando Maven 3.9.9 a $H/maven ..."
        curl -fsSL -o "$H/maven.tar.gz" \
            "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz"
        mkdir -p "$H/maven" && tar -xzf "$H/maven.tar.gz" -C "$H/maven" --strip-components=1
        rm -f "$H/maven.tar.gz"
    fi
    export PATH="$H/maven/bin:$PATH"
fi
mvn -version | head -1

# ---------- 3. Compilación (P20) ----------
./compilar.sh
echo "Compilado. Classpath: $(cat out/classpath.txt)"

# ---------- 4. Entorno para los trabajos ----------
{
    echo "# generado por preparar_cluster.sh"
    [ -f "$H/modulo_java" ] && cat "$H/modulo_java"
    echo "export PATH=\"$JAVA_BIN:\$PATH\""
    echo "export JAVA_HOME=\"$JAVA_HOME\""
} > "$H/entorno.sh"
echo
echo "Listo. Probá con:  sbatch herramientas/clusteruy.sh prueba"

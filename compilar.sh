#!/usr/bin/env bash
# Compila el proyecto en out/. Ver docs/DECISIONES.md D-A4.
#
# Busca las dependencias (jMetal 5.10 y Apache Commons) en este orden:
#   1. la variable de entorno JMETAL_CP (classpath explícito);
#   2. los .jar que haya en lib/;
#   3. Maven: baja las dependencias a lib/ con `mvn dependency:copy-dependencies`.
#
# Deja el classpath de ejecución en out/classpath.txt, para que validar.sh y
# cualquier otro script corran con `java -cp "$(cat out/classpath.txt)" ...`.
set -e
cd "$(dirname "$0")"

CP="${JMETAL_CP:-}"
if [ -z "$CP" ] && ! ls lib/*.jar >/dev/null 2>&1; then
    if command -v mvn >/dev/null 2>&1; then
        echo "   bajando dependencias con Maven a lib/ ..."
        mvn -q dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime
    fi
fi
if [ -z "$CP" ] && ls lib/*.jar >/dev/null 2>&1; then
    CP=$(ls lib/*.jar | tr '\n' ':' | sed 's/:$//')
fi
if [ -z "$CP" ]; then
    echo "ERROR: no encuentro jMetal. Instalá Maven, poné los .jar en lib/ o definí JMETAL_CP." >&2
    exit 1
fi

rm -rf out && mkdir out
javac -Xlint:all -Xlint:-serial -encoding UTF-8 -cp "$CP" -d out $(find src -name '*.java')
echo "out:$CP" > out/classpath.txt

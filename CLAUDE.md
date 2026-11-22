# Instrucciones para cualquier Claude (o persona) que trabaje en este repo

Este repo lo trabajan **Juan Ignacio Apolo y Alen Cukier**, cada uno con su
propio Claude. Ninguno ve las conversaciones del otro: **todo el contexto
tiene que quedar escrito en el repo**. Si algo no está escrito acá, para el
otro no existe.

## Antes de tocar nada, leé en este orden

1. `README.md`: qué es el proyecto, estado y cómo se corre.
2. `docs/PENDIENTES.md`: qué falta, quién lo tiene y la bitácora de cambios.
3. `docs/DECISIONES.md`: decisiones de modelado, metodología e implementación,
   con el porqué. **No cambies una decisión sin agregar una nueva que la
   reemplace y explique por qué.**
4. `REFERENCIAS.md`: qué parte del informe está implementada en qué archivo.
5. `docs/VALIDACION.md`: qué se verificó y con qué resultado; `docs/RESULTADOS.md`:
   qué resultados hay (y cuáles son preliminares).
6. El informe inicial `docs/Informe_Inicial_v2.pdf`. Es el contrato con el
   profesor: lo que dice que vamos a hacer, hay que hacerlo, o justificar en
   el informe final por qué no.

## Reglas de trabajo

- **Idioma**: español rioplatense en código, comentarios y documentos.
- **Cada implementación lleva la referencia al informe** en su javadoc, con
  el formato `[Informe §III-B ec. (3)]`. Si implementás algo que no está en el
  informe, poné `[No está en el informe inicial: ver DECISIONES D-xx]`.
- **Cada vez que implementes una parte del informe, actualizá
  `REFERENCIAS.md`** (sección del informe → archivo/clase/método → estado).
- **Cada decisión de implementación no trivial va a `docs/DECISIONES.md`**
  con número, fecha, autor, qué se decidió, alternativas y por qué.
- **Validar antes de subir**: `./validar.sh` tiene que pasar y los números
  de referencia tienen que coincidir (el simulador es determinista dada la
  semilla). Si un cambio altera un número de referencia a propósito, se
  actualiza el valor esperado **y** se explica en la bitácora.
- **Un módulo por commit**, con un mensaje que diga qué se hizo y cómo se
  validó. Alen pidió que sus commits vayan **sin línea de co-autoría**.
- Trabajo en **ramas** (`alen/...`, `juan/...`) y PR a `main`, así el otro
  puede revisar.
- Al terminar una sesión, **agregá una entrada a la bitácora** de
  `docs/PENDIENTES.md` y movés las tareas de estado.
- Todo lo que se diga en las diapositivas del curso (Temas 0 a 12 y
  StatTests) es la vara: cualquier técnica usada tiene que poder citarse
  ahí o en la bibliografía del informe.

## Reglas técnicas que no se negocian (ver DECISIONES, sección M)

- El presupuesto se mide en **evaluaciones de fitness**, incluyendo
  reevaluaciones, búsqueda local y selección final.
- **Escenarios comunes**: toda una generación se evalúa sobre los mismos
  escenarios, que rotan entre generaciones; los sobrevivientes se reevalúan.
- Los resultados que se reportan son siempre los **revaluados con N=200 (o
  10.000 para robustez) sobre semillas de validación** que la búsqueda nunca
  usa (`777_000` en adelante).
- **Nunca usar `JMetalRandom`** (es un singleton global y rompe la
  reproducibilidad cuando las corridas van en paralelo). Todo operador recibe
  el `Aleatorio` de su corrida. Ver D-A2.
- **jMetal minimiza**: un objetivo a maximizar se guarda negado.
- No commitear `lib/`, `out/` ni `target/`, ni el código fuente de jMetal
  (la consigna prohíbe incluir el framework si no se modificó).

## Cómo compilar

```bash
./compilar.sh          # usa Maven si está; si no, los .jar de lib/
./validar.sh           # compila y corre todas las validaciones
```

Detalle en el README, sección "Cómo compilar y correr".

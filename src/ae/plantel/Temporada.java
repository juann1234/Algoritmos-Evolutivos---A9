package ae.plantel;

import ae.plantel.Modelo.EstadoJugador;
import ae.plantel.Modelo.PrioridadMinutos;
import ae.plantel.Modelo.Puesto;

import java.util.Arrays;

/**
 * Reparto de minutos y rendimiento deportivo de un período (media temporada).
 *
 * Solo resuelve la parte deportiva. El orden de los pasos lo fija el
 * Evaluador: primero se sortean las lesiones (definen los minutos
 * disponibles), después se reparten los minutos y recién entonces se calcula
 * el rendimiento. Repartir antes de sortear lesiones dejaría planificar sobre
 * jugadores que en ese escenario no estaban, y el fitness premiaría planes
 * frágiles.
 *
 * Todo trabaja con arrays indexados por la posición del jugador en la
 * instancia. Una versión anterior usaba mapas con clave Integer y pasaba el
 * 40% del tiempo de simulación buscando en ellos.
 */
public final class Temporada {

    /** Fracción de los minutos del período garantizada a quien está en DESARROLLO. */
    public static final double PISO_DESARROLLO = 0.45;

    /** Nadie juega el 100%: carga física, sanciones, rotación obligada. */
    public static final double TOPE_INDIVIDUAL = 0.92;

    /** Bono de score de quien tiene prioridad de titular. */
    public static final double BONO_TITULAR = 6.0;

    /** Nivel del juvenil de filial con el que se tapa un hueco de plantilla. */
    public static final double THETA_FILIAL = 62.0;

    // Mapeo de fuerza del equipo a puntos de liga de una temporada completa.
    public static final double PUNTOS_PISO = 20.0;
    public static final double PUNTOS_RANGO = 80.0;
    public static final double FUERZA_CENTRO = 78.0;
    public static final double FUERZA_PENDIENTE = 3.0;

    /** Peso del once frente al banco. */
    public static final double PESO_ONCE = 0.85;

    /** Tamaño del once y del banco que cuenta para la profundidad. */
    public static final int TITULARES = 11;
    public static final int BANCO = 7;

    private static final Puesto[] PUESTOS = Puesto.values();
    private static final int DESARROLLO = PrioridadMinutos.DESARROLLO.ordinal();
    private static final int TITULAR = PrioridadMinutos.TITULAR_PREFERENTE.ordinal();

    private Temporada() {
    }

    /** Ordena idx[0..n) por clave descendente; estable (inserción, n es chico). */
    private static void ordenarDesc(int[] idx, int n, double[] clave) {
        for (int a = 1; a < n; a++) {
            int v = idx[a];
            int b = a - 1;
            while (b >= 0 && clave[idx[b]] < clave[v]) {
                idx[b + 1] = idx[b];
                b--;
            }
            idx[b + 1] = v;
        }
    }

    /**
     * Reparte los minutos del período puesto por puesto.
     *
     * @param conv        posiciones de los convocables (plantel y préstamos)
     * @param prioridad   prioridad de minutos por posición
     * @param disponibles minutos disponibles por posición (según lesiones)
     * @param minutos     salida: minutos asignados por posición
     * @param aux         buffers de trabajo de largo n (evita asignar memoria)
     * @return minutos que hubo que cubrir con la filial
     */
    public static double repartirMinutos(EstadoJugador[] e, int[] conv, int nConv,
                                         int[] prioridad, double[] disponibles,
                                         double minutosPeriodo, double[] minutos,
                                         int[] auxIdx, double[] auxTheta, double[] auxScore) {
        double piso = PISO_DESARROLLO * minutosPeriodo;
        double tope = TOPE_INDIVIDUAL * minutosPeriodo;
        double filial = 0.0;

        for (int c = 0; c < nConv; c++) {
            int i = conv[c];
            minutos[i] = 0.0;
            auxTheta[i] = e[i].theta;
            auxScore[i] = e[i].theta + (prioridad[i] == TITULAR ? BONO_TITULAR : 0.0);
        }

        for (Puesto puesto : PUESTOS) {
            double cupo = Modelo.ONCE_TITULAR.get(puesto) * minutosPeriodo;
            int n = 0;
            for (int c = 0; c < nConv; c++) {
                if (e[conv[c]].puesto() == puesto) {
                    auxIdx[n++] = conv[c];
                }
            }

            // 1) piso garantizado a los marcados como DESARROLLO, de mejor a peor
            ordenarDesc(auxIdx, n, auxTheta);
            for (int k = 0; k < n; k++) {
                int i = auxIdx[k];
                if (prioridad[i] != DESARROLLO) {
                    continue;
                }
                double asignado = Math.max(0.0, Math.min(Math.min(piso, disponibles[i]),
                        Math.min(tope, cupo)));
                minutos[i] += asignado;
                cupo -= asignado;
            }

            // 2) el resto del cupo, por orden de score
            ordenarDesc(auxIdx, n, auxScore);
            for (int k = 0; k < n && cupo > 0; k++) {
                int i = auxIdx[k];
                double margen = Math.min(tope - minutos[i], disponibles[i] - minutos[i]);
                double asignado = Math.max(0.0, Math.min(margen, cupo));
                minutos[i] += asignado;
                cupo -= asignado;
            }

            // 3) hueco sin cubrir -> juvenil de filial, con su costo deportivo
            if (cupo > 0) {
                filial += cupo;
            }
        }
        return filial;
    }

    /**
     * Fuerza del equipo en el período.
     *
     * Combina la calidad de quienes jugaron, ponderada por minutos, con la
     * calidad del BANCO: los siete mejores que siguen a los once de más nivel,
     * completando con juveniles de filial si no alcanzan.
     *
     * Antes la profundidad era la media de nivel de todo el plantel, y eso
     * producía un resultado absurdo: liberar a los seis peores SUBÍA los puntos
     * (de 76.3 a 77.8), porque la media mejoraba. Con el banco acotado, tener
     * más jugadores nunca resta y perder suplentes cuesta.
     */
    public static double fuerza(EstadoJugador[] e, int[] conv, int nConv, double[] minutos,
                                double minutosFilial, double[] auxNiveles) {
        double totalMin = minutosFilial;
        double ponderado = THETA_FILIAL * minutosFilial;
        for (int c = 0; c < nConv; c++) {
            int i = conv[c];
            totalMin += minutos[i];
            ponderado += e[i].theta * minutos[i];
            auxNiveles[c] = e[i].theta;
        }
        if (totalMin <= 0) {
            return 0.0;
        }
        double fuerzaOnce = ponderado / totalMin;

        Arrays.sort(auxNiveles, 0, nConv);   // ascendente: el mejor queda al final
        double banco = 0;
        for (int b = 0; b < BANCO; b++) {
            int idx = nConv - 1 - TITULARES - b;
            banco += idx >= 0 ? auxNiveles[idx] : THETA_FILIAL;
        }
        banco /= BANCO;

        return PESO_ONCE * fuerzaOnce + (1.0 - PESO_ONCE) * banco;
    }

    /** Puntos de liga que rinde una temporada completa a una fuerza dada. */
    public static double puntosTemporada(double fuerza) {
        if (fuerza <= 0) {
            return PUNTOS_PISO;
        }
        return PUNTOS_PISO + PUNTOS_RANGO
                / (1.0 + Math.exp(-(fuerza - FUERZA_CENTRO) / FUERZA_PENDIENTE));
    }
}

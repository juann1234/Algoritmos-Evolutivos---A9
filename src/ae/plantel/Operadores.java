package ae.plantel;

import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.JugadorBase;

import java.util.ArrayList;
import java.util.List;

/**
 * Operadores evolutivos.
 *
 * [Informe §III-C inicialización, selección, cruzamiento y mutación]
 *
 * Todos son operadores del temario: selección por torneo, cruzamiento uniforme
 * a nivel de gen, cruzamiento aritmético para los campos reales y mutación
 * gaussiana o de reasignación para los discretos.
 *
 * La mutación elige uno de cinco operadores por gen mutado. El de intercambio
 * solo opera entre jugadores del mismo puesto Y la misma pertenencia: cambiar
 * genes entre un propio y uno del mercado produciría acciones fuera de dominio.
 */
public final class Operadores {

    /** Fracción de la población inicial sembrada con variantes del statu quo. */
    public static final double FRACCION_SIEMBRA = 0.30;

    /**
     * Probabilidad de que un gen aleatorio pida MANTENER / IGNORAR.
     *
     * Con acciones uniformes, el 80% del plantel recibe una orden distinta de
     * MANTENER y el plan se autodestruye: 200 planes uniformes no le ganan al
     * statu quo ni una vez. Sesgar la inicialización hacia la inacción es lo
     * que le da al AE un punto de partida del que valga la pena salir.
     */
    public static final double PROB_INACCION = 0.65;


    private Operadores() {
    }

    // ----------------------------------------------------------------
    // Restricción a la grilla (solo para la comparación con el óptimo exacto)
    // ----------------------------------------------------------------

    /** Ventanas, duración, salarios y prioridad que enumera OptimoExacto. */
    public static final int[] GRILLA_VENTANAS = { 1, 2 };
    public static final int GRILLA_DURACION = 3;
    public static final double[] GRILLA_SALARIOS = { 1.0, 1.3 };
    public static final double GRILLA_CLAUSULA = 3.0;

    /**
     * Proyecta un cromosoma sobre la grilla enumerable.
     *
     * Sin esto, el AE busca en un espacio ESTRICTAMENTE MAYOR que el que se
     * enumera (seis ventanas, cinco duraciones, salario continuo, tres
     * prioridades) y encuentra planes mejores que el supuesto óptimo: el GAP
     * sale negativo y la comparación no significa nada. Para medir GAP, los dos
     * métodos tienen que buscar en el mismo conjunto.
     *
     * [Informe §IV-B(2) micro-instancias]
     */
    public static void proyectarAGrilla(Cromosoma c) {
        for (int i = 0; i < c.n; i++) {
            c.ventana[i] = c.ventana[i] <= 1 ? 1 : 2;
            c.duracion[i] = GRILLA_DURACION;
            c.salario[i] = c.salario[i] < (GRILLA_SALARIOS[0] + GRILLA_SALARIOS[1]) / 2
                    ? GRILLA_SALARIOS[0] : GRILLA_SALARIOS[1];
            c.clausula[i] = GRILLA_CLAUSULA;
            c.prioridad[i] = Modelo.PrioridadMinutos.ROTACION.ordinal();
        }
    }

    // ----------------------------------------------------------------
    // Inicialización
    // ----------------------------------------------------------------

    /** Individuo aleatorio sesgado hacia la inacción. */
    public static Cromosoma aleatorioSesgado(Instancia inst, Aleatorio rng) {
        Cromosoma c = Cromosoma.aleatorio(inst, rng);
        for (int i = 0; i < c.n; i++) {
            if (rng.uniforme() < PROB_INACCION) {
                c.accion[i] = 0;   // MANTENER para propios, IGNORAR para mercado
            }
        }
        return c;
    }

    /** Variante perturbada del statu quo: unas pocas operaciones al azar. */
    public static Cromosoma perturbarStatuQuo(Instancia inst, Aleatorio rng, int operaciones) {
        Cromosoma c = Cromosoma.statuQuo(inst);
        for (int k = 0; k < operaciones; k++) {
            int i = rng.entero(0, c.n);
            c.accion[i] = rng.entero(0, Cromosoma.dominioAccion(inst, i));
            c.ventana[i] = rng.entero(1, Cromosoma.VENTANAS + 1);
            c.duracion[i] = rng.entero(Cromosoma.DURACION_MIN, Cromosoma.DURACION_MAX + 1);
            c.salario[i] = rng.uniforme(Cromosoma.SALARIO_MIN, Cromosoma.SALARIO_MAX);
            c.clausula[i] = rng.uniforme(Cromosoma.CLAUSULA_MIN, Cromosoma.CLAUSULA_MAX);
            c.prioridad[i] = rng.entero(0, 3);
        }
        return c;
    }

    /**
     * Población inicial: una fracción sembrada con variantes del statu quo y
     * el resto aleatoria sesgada. El statu quo puro entra siempre como
     * individuo 0, para que el AE nunca pueda devolver algo peor que no operar.
     *
     * [Informe §III-C inicialización]
     */
    public static Cromosoma[] poblacionInicial(Instancia inst, int tamano, Aleatorio rng) {
        Cromosoma[] pob = new Cromosoma[tamano];
        pob[0] = Cromosoma.statuQuo(inst);
        int sembrados = (int) Math.round(tamano * FRACCION_SIEMBRA);
        for (int i = 1; i < tamano; i++) {
            pob[i] = i <= sembrados
                    ? perturbarStatuQuo(inst, rng, rng.entero(1, 8))
                    : aleatorioSesgado(inst, rng);
        }
        return pob;
    }

    // ----------------------------------------------------------------
    // Selección
    // ----------------------------------------------------------------

    /**
     * Torneo de tamaño k; devuelve el índice del ganador.
     *
     * [Informe §III-C selección por torneo de tamaño 3]
     */
    public static int torneo(double[] fitness, int k, Aleatorio rng) {
        int mejor = rng.entero(0, fitness.length);
        for (int i = 1; i < k; i++) {
            int reto = rng.entero(0, fitness.length);
            if (fitness[reto] > fitness[mejor]) {
                mejor = reto;
            }
        }
        return mejor;
    }

    // ----------------------------------------------------------------
    // Cruzamiento
    // ----------------------------------------------------------------

    /**
     * Cruzamiento uniforme a nivel de gen, más cruzamiento aritmético sobre los
     * campos reales.
     *
     * Cada gen (un jugador) se hereda ENTERO de uno de los padres: partirlo
     * rompería combinaciones que solo tienen sentido juntas, como "comprar con
     * salario alto y cláusula alta".
     *
     * El cruzamiento aritmético (combinación lineal, el que el curso indica
     * para codificación real) se aplica solo cuando los dos padres hacen LO
     * MISMO con ese jugador. Si uno lo compra y el otro lo ignora, promediar
     * sus salarios no significa nada; si los dos lo compran, un salario
     * intermedio es una oferta intermedia.
     *
     * [Informe §III-C cruzamiento uniforme + aritmético]
     */
    public static Cromosoma[] cruzar(Cromosoma p1, Cromosoma p2, double pC, Aleatorio rng) {
        Cromosoma h1 = p1.copia();
        Cromosoma h2 = p2.copia();
        if (rng.uniforme() >= pC) {
            return new Cromosoma[] { h1, h2 };
        }
        for (int i = 0; i < p1.n; i++) {
            if (rng.uniforme() < 0.5) {
                copiarGen(p2, h1, i);
                copiarGen(p1, h2, i);
            }
            if (p1.accion[i] == p2.accion[i]) {
                double l = rng.uniforme();
                h1.salario[i] = l * p1.salario[i] + (1 - l) * p2.salario[i];
                h2.salario[i] = (1 - l) * p1.salario[i] + l * p2.salario[i];
                h1.clausula[i] = l * p1.clausula[i] + (1 - l) * p2.clausula[i];
                h2.clausula[i] = (1 - l) * p1.clausula[i] + l * p2.clausula[i];
            }
        }
        return new Cromosoma[] { h1, h2 };
    }

    private static void copiarGen(Cromosoma origen, Cromosoma destino, int i) {
        destino.accion[i] = origen.accion[i];
        destino.ventana[i] = origen.ventana[i];
        destino.duracion[i] = origen.duracion[i];
        destino.prioridad[i] = origen.prioridad[i];
        destino.salario[i] = origen.salario[i];
        destino.clausula[i] = origen.clausula[i];
    }

    /**
     * Cruzamiento por bloques posicionales: el cromosoma se parte por puesto
     * (POR, DEF, MED, DEL; cada bloque con los propios y los candidatos de ese
     * puesto) y cada bloque se hereda ENTERO de uno de los padres. Los campos
     * reales se combinan con el cruzamiento aritmético, como en
     * {@link #cruzar}, cuando los dos padres hacen lo mismo con el jugador.
     *
     * La idea: las decisiones de un mismo puesto son las más epistáticas entre
     * sí (comprar un delantero solo vale si no hay otro titular, informe §II-A),
     * así que mantenerlas juntas preserva bloques constructivos (Tema 7). El
     * costo es menos exploración: con 4 bloques hay solo 16 hijos posibles.
     *
     * [Informe §III-C: "se comparará contra un cruzamiento por bloques
     * posicionales, donde el cromosoma se particiona por puesto y cada bloque se
     * hereda entero"] (Alen, P11)
     */
    public static Cromosoma[] cruzarPorBloques(Instancia inst, Cromosoma p1, Cromosoma p2,
                                               double pC, Aleatorio rng) {
        Cromosoma h1 = p1.copia();
        Cromosoma h2 = p2.copia();
        if (rng.uniforme() >= pC) {
            return new Cromosoma[] { h1, h2 };
        }
        Modelo.Puesto[] puestos = Modelo.Puesto.values();
        boolean[] intercambiar = new boolean[puestos.length];
        for (int b = 0; b < puestos.length; b++) {
            intercambiar[b] = rng.uniforme() < 0.5;
        }
        for (int i = 0; i < p1.n; i++) {
            if (intercambiar[inst.jugadores.get(i).puesto().ordinal()]) {
                copiarGen(p2, h1, i);
                copiarGen(p1, h2, i);
            }
            if (p1.accion[i] == p2.accion[i]) {
                double l = rng.uniforme();
                h1.salario[i] = l * p1.salario[i] + (1 - l) * p2.salario[i];
                h2.salario[i] = (1 - l) * p1.salario[i] + l * p2.salario[i];
                h1.clausula[i] = l * p1.clausula[i] + (1 - l) * p2.clausula[i];
                h2.clausula[i] = (1 - l) * p1.clausula[i] + l * p2.clausula[i];
            }
        }
        return new Cromosoma[] { h1, h2 };
    }

    // ----------------------------------------------------------------
    // Escalado del fitness (para la selección proporcional)
    // ----------------------------------------------------------------

    /** Constante c del truncamiento sigma (Tema 3: "usualmente entre 1 y 3"). */
    public static final double C_SIGMA = 2.0;
    /** Copias esperadas del mejor tras el escalado lineal (Goldberg, C_mult). */
    public static final double C_MULT = 2.0;

    /**
     * Escalado del fitness para la selección proporcional, en dos pasos, tal
     * como lo presenta el Tema 3:
     * <ol>
     *   <li><b>truncamiento sigma</b> (Forrest): F' = F − (F̄ − c·σ), con los
     *       negativos llevados a 0;</li>
     *   <li><b>escalado lineal</b> F'' = a·F' + b, con a y b elegidos para que la
     *       media no cambie y el mejor reciba C_MULT copias esperadas; si eso
     *       volviera negativo al peor, se elige la recta que lo deja en 0.</li>
     * </ol>
     * Sin esto, con fitness del orden de 230 y diferencias de pocas unidades, la
     * selección proporcional sería casi uniforme: la "caminata aleatoria" que
     * describe el Tema 3.
     *
     * [Informe §III-C: "selección proporcional con escalado" y "escalado de
     * fitness"] (Alen, P10 y P12)
     *
     * @return pesos de selección, no negativos
     */
    public static double[] escalar(double[] fitness) {
        int n = fitness.length;
        double media = 0;
        for (double f : fitness) {
            media += f;
        }
        media /= n;
        double var = 0;
        for (double f : fitness) {
            var += (f - media) * (f - media);
        }
        double sigma = n > 1 ? Math.sqrt(var / (n - 1)) : 0;
        double[] t = new double[n];
        double tMedia = 0;
        double tMax = 0;
        double tMin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            t[i] = Math.max(0.0, fitness[i] - (media - C_SIGMA * sigma));
            tMedia += t[i] / n;
            tMax = Math.max(tMax, t[i]);
            tMin = Math.min(tMin, t[i]);
        }
        double[] w = new double[n];
        if (tMax - tMedia <= 1e-12) {
            java.util.Arrays.fill(w, 1.0);   // población uniforme: todos igual
            return w;
        }
        double a;
        double b;
        if (tMin > (C_MULT * tMedia - tMax) / (C_MULT - 1.0)) {
            a = (C_MULT - 1.0) * tMedia / (tMax - tMedia);
            b = tMedia * (tMax - C_MULT * tMedia) / (tMax - tMedia);
        } else {
            a = tMedia / (tMedia - tMin);
            b = -tMin * tMedia / (tMedia - tMin);
        }
        for (int i = 0; i < n; i++) {
            w[i] = Math.max(0.0, a * t[i] + b);
        }
        return w;
    }

    // ----------------------------------------------------------------
    // Mutación
    // ----------------------------------------------------------------

    /**
     * Mutación por gen: con probabilidad pM se aplica uno de cinco operadores.
     *
     * [Informe §III-C mutación: cinco operadores]
     */
    public static void mutar(Instancia inst, Cromosoma c, double pM, Aleatorio rng) {
        for (int i = 0; i < c.n; i++) {
            if (rng.uniforme() >= pM) {
                continue;
            }
            switch (rng.entero(0, 5)) {
                case 0 -> // cambio de acción
                        c.accion[i] = rng.entero(0, Cromosoma.dominioAccion(inst, i));
                case 1 -> { // desplazamiento de ventana +-1
                    int w = c.ventana[i] + (rng.uniforme() < 0.5 ? -1 : 1);
                    c.ventana[i] = Math.max(1, Math.min(Cromosoma.VENTANAS, w));
                }
                case 2 -> { // gaussiana sobre los campos reales
                    c.salario[i] = recortar(
                            c.salario[i] + rng.normal(0, 0.10),
                            Cromosoma.SALARIO_MIN, Cromosoma.SALARIO_MAX);
                    c.clausula[i] = recortar(
                            c.clausula[i] + rng.normal(0, 0.8),
                            Cromosoma.CLAUSULA_MIN, Cromosoma.CLAUSULA_MAX);
                    c.duracion[i] = (int) recortar(
                            c.duracion[i] + (rng.uniforme() < 0.5 ? -1 : 1),
                            Cromosoma.DURACION_MIN, Cromosoma.DURACION_MAX);
                }
                case 3 -> // prioridad de minutos
                        c.prioridad[i] = rng.entero(0, 3);
                default -> // intercambio con otro jugador del mismo puesto y tipo
                        intercambiar(inst, c, i, rng);
            }
        }
    }

    // ----------------------------------------------------------------
    // Distancia entre planes y operadores de diversidad
    // ----------------------------------------------------------------

    /**
     * Distancia entre dos planes: cantidad de jugadores sobre los que deciden
     * algo distinto. Cuenta la acción y, si los dos operan, la ventana. Los
     * campos contractuales no cuentan: dos planes que compran a los mismos
     * jugadores en las mismas ventanas son el mismo plan con otra negociación.
     *
     * Es la métrica sobre la que trabajan sharing (Tema 8) y la prevención de
     * incesto de CHC (Tema 9).
     *
     * [Informe §III-C distancia entre planes para sharing/crowding]
     */
    public static int distancia(Cromosoma a, Cromosoma b) {
        int d = 0;
        for (int i = 0; i < a.n; i++) {
            if (a.accion[i] != b.accion[i]
                    || (a.accion[i] != 0 && a.ventana[i] != b.ventana[i])) {
                d++;
            }
        }
        return d;
    }

    /**
     * Cruzamiento HUX de CHC: intercambia exactamente la mitad de los genes en
     * los que los padres difieren, elegidos al azar. Cada gen viaja entero.
     */
    public static Cromosoma[] hux(Cromosoma p1, Cromosoma p2, Aleatorio rng) {
        Cromosoma h1 = p1.copia();
        Cromosoma h2 = p2.copia();
        List<Integer> distintos = new ArrayList<>();
        for (int i = 0; i < p1.n; i++) {
            if (p1.accion[i] != p2.accion[i]
                    || (p1.accion[i] != 0 && p1.ventana[i] != p2.ventana[i])) {
                distintos.add(i);
            }
        }
        rng.mezclar(distintos);
        for (int k = 0; k < distintos.size() / 2; k++) {
            int i = distintos.get(k);
            copiarGen(p2, h1, i);
            copiarGen(p1, h2, i);
        }
        return new Cromosoma[] { h1, h2 };
    }

    /** Reasigna al azar cada gen con probabilidad q (reinicio cataclísmico de CHC). */
    public static Cromosoma perturbar(Instancia inst, Cromosoma base, double q, Aleatorio rng) {
        Cromosoma c = base.copia();
        Cromosoma azar = aleatorioSesgado(inst, rng);
        for (int i = 0; i < c.n; i++) {
            if (rng.uniforme() < q) {
                copiarGen(azar, c, i);
            }
        }
        return c;
    }

    private static double recortar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Intercambia el gen completo con el de otro jugador del mismo puesto y la
     * misma pertenencia. Sirve para probar "este fichaje en lugar de aquel"
     * sin alterar el resto del plan.
     */
    private static void intercambiar(Instancia inst, Cromosoma c, int i, Aleatorio rng) {
        JugadorBase ji = inst.jugadores.get(i);
        List<Integer> pares = new ArrayList<>();
        for (int k = 0; k < c.n; k++) {
            if (k == i) {
                continue;
            }
            JugadorBase jk = inst.jugadores.get(k);
            if (jk.puesto() == ji.puesto() && jk.pertenencia() == ji.pertenencia()) {
                pares.add(k);
            }
        }
        if (pares.isEmpty()) {
            return;
        }
        int j = pares.get(rng.entero(0, pares.size()));
        intercambiarGenes(c, i, j);
    }

    private static void intercambiarGenes(Cromosoma c, int i, int j) {
        int a = c.accion[i];
        c.accion[i] = c.accion[j];
        c.accion[j] = a;
        int w = c.ventana[i];
        c.ventana[i] = c.ventana[j];
        c.ventana[j] = w;
        int d = c.duracion[i];
        c.duracion[i] = c.duracion[j];
        c.duracion[j] = d;
        double s = c.salario[i];
        c.salario[i] = c.salario[j];
        c.salario[j] = s;
        double cl = c.clausula[i];
        c.clausula[i] = c.clausula[j];
        c.clausula[j] = cl;
        int pr = c.prioridad[i];
        c.prioridad[i] = c.prioridad[j];
        c.prioridad[j] = pr;
    }
}

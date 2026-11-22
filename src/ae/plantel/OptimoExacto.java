package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.Pertenencia;
import ae.plantel.Modelo.PrioridadMinutos;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Óptimo exacto por enumeración exhaustiva sobre micro-instancias.
 *
 * [Informe §IV-B(2) óptimo exacto en micro-instancias; §II-B tamaño del espacio]
 *
 * Es la única referencia que permite decir cuán lejos está el AE del óptimo y
 * no solo cuánto le gana al statu quo. Como el problema real tiene del orden de
 * 10^293 genotipos, el óptimo exacto solo es alcanzable achicando dos cosas:
 *
 *   - el conjunto de jugadores (4 propios, 2 candidatos);
 *   - el espacio de decisión por jugador (2 ventanas, 2 niveles salariales,
 *     duración fija, prioridad fija).
 *
 * UNA ACLARACIÓN QUE HAY QUE HACER EN EL INFORME: lo que se enumera es el
 * óptimo del FITNESS MUESTRAL, es decir, del promedio sobre los N escenarios
 * fijados por la semilla, no del valor esperado verdadero. Con un objetivo
 * estocástico el óptimo verdadero no es computable por enumeración: haría falta
 * N infinito. Por eso el AE se compara contra el óptimo sobre exactamente el
 * mismo conjunto de escenarios.
 *
 * Uso:  java -cp out ae.plantel.OptimoExacto [propios] [candidatos] [semilla] [edadMedia] [presupuesto]
 *
 * Los tres últimos argumentos (Alen, P16) permiten armar micro-instancias más
 * difíciles que la original, donde el statu quo queda lejos del óptimo (con la
 * original queda a 1%, y un GAP de 0% dice poco). Sin ellos la instancia es la
 * de siempre (semilla 55, edad media 26,5, presupuesto 120) y la salida de las
 * líneas "AE" no cambia; se agregan las líneas del AE sobre jMetal.
 */
public final class OptimoExacto {

    private static final Parametros P = new Parametros();

    /** Espacio de decisión reducido para un gen. */
    private record Opcion(int accion, int ventana, int duracion, double salario) {
    }

    /** Opciones consideradas para un jugador, según su pertenencia. */
    private static List<Opcion> opciones(Instancia inst, int i) {
        List<Opcion> out = new ArrayList<>();
        boolean propio = inst.jugadores.get(i).pertenencia() == Pertenencia.PROPIO;
        // índice 0 del dominio: MANTENER para propios, IGNORAR para mercado.
        out.add(new Opcion(0, 1, Operadores.GRILLA_DURACION, Operadores.GRILLA_SALARIOS[0]));
        for (int w : Operadores.GRILLA_VENTANAS) {
            if (propio) {
                out.add(new Opcion(Accion.VENDER.ordinal(), w,
                        Operadores.GRILLA_DURACION, Operadores.GRILLA_SALARIOS[0]));
                out.add(new Opcion(Accion.CEDER.ordinal(), w,
                        Operadores.GRILLA_DURACION, Operadores.GRILLA_SALARIOS[0]));
                out.add(new Opcion(Accion.LIBERAR.ordinal(), w,
                        Operadores.GRILLA_DURACION, Operadores.GRILLA_SALARIOS[0]));
                for (double sal : Operadores.GRILLA_SALARIOS) {
                    out.add(new Opcion(Accion.RENOVAR.ordinal(), w,
                            Operadores.GRILLA_DURACION, sal));
                }
            } else {
                for (int a : new int[] { 1, 2 }) {   // COMPRAR, INCORPORAR_PRESTAMO
                    for (double sal : Operadores.GRILLA_SALARIOS) {
                        out.add(new Opcion(a, w, Operadores.GRILLA_DURACION, sal));
                    }
                }
            }
        }
        return out;
    }

    /** Resultado de la enumeración exhaustiva. */
    public record Optimo(Cromosoma plan, double valor, long planes, double segundos) {
    }

    /** Escenarios con que se enumera (y con que se mide al AE para el GAP). */
    public static final int N_ENUM = 30;
    public static final long SEMILLA_ENUM = 424_242L;

    /**
     * Enumera todo el espacio reducido (ver {@link #opciones}) y devuelve el
     * plan de mayor fitness muestral sobre los N escenarios de la semilla dada.
     * Extraído de main para reutilizarlo en jmetal/GapMicroInstancias (Alen, P16);
     * el recorrido es el mismo de antes.
     *
     * [Informe §IV-B(2) óptimo exacto en micro-instancias]
     */
    public static Optimo enumerar(Instancia inst, int N, long SEMILLA) {
        List<List<Opcion>> espacio = new ArrayList<>();
        long total = 1;
        for (int i = 0; i < inst.jugadores.size(); i++) {
            List<Opcion> o = opciones(inst, i);
            espacio.add(o);
            total *= o.size();
        }

        // ---------------- enumeración ----------------
        Cromosoma c = Cromosoma.statuQuo(inst);
        for (int i = 0; i < c.n; i++) {
            c.prioridad[i] = PrioridadMinutos.ROTACION.ordinal();
            c.clausula[i] = 3.0;
        }

        int[] contador = new int[c.n];
        double mejorValor = Double.NEGATIVE_INFINITY;
        Cromosoma mejor = null;
        long t0 = System.nanoTime();

        for (long k = 0; k < total; k++) {
            for (int i = 0; i < c.n; i++) {
                Opcion o = espacio.get(i).get(contador[i]);
                c.accion[i] = o.accion();
                c.ventana[i] = o.ventana();
                c.duracion[i] = o.duracion();
                c.salario[i] = o.salario();
            }
            double v = Evaluador.evaluar(inst, c, N, SEMILLA, P).valor;
            if (v > mejorValor) {
                mejorValor = v;
                mejor = c.copia();
            }
            // odómetro
            for (int i = 0; i < c.n; i++) {
                if (++contador[i] < espacio.get(i).size()) {
                    break;
                }
                contador[i] = 0;
            }
        }
        return new Optimo(mejor, mejorValor, total, (System.nanoTime() - t0) / 1e9);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        final String linea = "=".repeat(78);
        final int N = N_ENUM;
        final long SEMILLA = SEMILLA_ENUM;

        int nPropios = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int nMercado = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        long semillaInst = args.length > 2 ? Long.parseLong(args[2]) : 55;
        double edadMedia = args.length > 3 ? Double.parseDouble(args[3]) : 26.5;
        double presupuesto = args.length > 4 ? Double.parseDouble(args[4]) : 120.0;
        Instancia inst = GeneradorInstancias.generar("micro", nPropios, nMercado, semillaInst,
                78.0, edadMedia, presupuesto, 1.25);
        System.out.println(inst.resumen());

        long espacioTotal = 1;
        for (int i = 0; i < inst.jugadores.size(); i++) {
            espacioTotal *= opciones(inst, i).size();
        }
        System.out.printf("%nEspacio enumerado: %,d planes (N=%d escenarios por plan)%n",
                espacioTotal, N);
        Optimo opt = enumerar(inst, N, SEMILLA);
        long total = opt.planes();
        double mejorValor = opt.valor();
        Cromosoma mejor = opt.plan();
        double dt = opt.segundos();
        System.out.printf("Enumeración completa en %.1f s (%.2f ms por plan)%n",
                dt, dt * 1000 / total);

        // ---------------- AE sobre la misma micro-instancia ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("GAP del AE respecto del óptimo exacto");
        System.out.println(linea);
        System.out.printf("%-26s%12s%12s%10s%n", "método", "f(x)", "evals", "GAP");

        double fsq = Evaluador.evaluar(inst, Cromosoma.statuQuo(inst), N, SEMILLA, P).valor;
        System.out.printf("%-26s%12.2f%12s%9.1f%%%n", "statu quo", fsq, "1",
                100.0 * (mejorValor - fsq) / Math.abs(mejorValor));

        for (int evals : new int[] { 500, 1000, 2000 }) {
            AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
            cfg.poblacion = 50;
            cfg.maxEvaluaciones = evals;
            cfg.replicas = N;
            cfg.grilla = true;   // mismo espacio de búsqueda que la enumeración
            double suma = 0;
            double mejorAE = Double.NEGATIVE_INFINITY;
            int corridas = 10;
            for (int r = 0; r < corridas; r++) {
                cfg.semilla = r + 1;
                AlgoritmoEvolutivo.Resultado res = AlgoritmoEvolutivo.ejecutar(inst, cfg, P);
                // Se mide sobre LOS MISMOS escenarios que la enumeración.
                double v = Evaluador.evaluar(inst, res.mejor, N, SEMILLA, P).valor;
                suma += v;
                mejorAE = Math.max(mejorAE, v);
            }
            double media = suma / corridas;
            System.out.printf("%-26s%12.2f%12d%9.2f%%%n",
                    "AE (media de " + corridas + ")", media, evals,
                    100.0 * (mejorValor - media) / Math.abs(mejorValor));
            System.out.printf("%-26s%12.2f%12d%9.2f%%%n",
                    "AE (mejor de " + corridas + ")", mejorAE, evals,
                    100.0 * (mejorValor - mejorAE) / Math.abs(mejorValor));

            // Mismo experimento con el AE sobre jMetal (motor de los experimentos, D-A1).
            double sumaJ = 0;
            double mejorJ = Double.NEGATIVE_INFINITY;
            int optimos = 0;
            for (int r = 0; r < corridas; r++) {
                cfg.semilla = r + 1;
                AlgoritmoEvolutivo.Resultado res = ae.plantel.jmetal.AGPlantel.ejecutar(inst, cfg, P);
                double v = Evaluador.evaluar(inst, res.mejor, N, SEMILLA, P).valor;
                sumaJ += v;
                mejorJ = Math.max(mejorJ, v);
                if (v >= mejorValor - 1e-9) {
                    optimos++;
                }
            }
            System.out.printf("%-26s%12.2f%12d%9.2f%%   (óptimo en %d de %d)%n",
                    "AE jMetal (media de " + corridas + ")", sumaJ / corridas, evals,
                    100.0 * (mejorValor - sumaJ / corridas) / Math.abs(mejorValor), optimos, corridas);
        }

        System.out.printf("%-26s%12.2f%12s%9.2f%%%n", "ÓPTIMO EXACTO", mejorValor,
                String.format("%,d", total), 0.0);

        System.out.println();
        System.out.println("Plan óptimo:");
        System.out.print(mejor.describir(inst, 10));
    }
}

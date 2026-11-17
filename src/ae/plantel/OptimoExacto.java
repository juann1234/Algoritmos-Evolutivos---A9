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
 * Uso:  java -cp out ae.plantel.OptimoExacto [propios] [candidatos]
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

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        final String linea = "=".repeat(78);
        final int N = 30;
        final long SEMILLA = 424_242L;

        int nPropios = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        int nMercado = args.length > 1 ? Integer.parseInt(args[1]) : 2;
        Instancia inst = GeneradorInstancias.generar("micro", nPropios, nMercado, 55);
        System.out.println(inst.resumen());

        List<List<Opcion>> espacio = new ArrayList<>();
        long total = 1;
        for (int i = 0; i < inst.jugadores.size(); i++) {
            List<Opcion> o = opciones(inst, i);
            espacio.add(o);
            total *= o.size();
        }
        System.out.printf("%nEspacio enumerado: %,d planes (N=%d escenarios por plan)%n",
                total, N);

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
        double dt = (System.nanoTime() - t0) / 1e9;
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
        }

        System.out.printf("%-26s%12.2f%12s%9.2f%%%n", "ÓPTIMO EXACTO", mejorValor,
                String.format("%,d", total), 0.0);

        System.out.println();
        System.out.println("Plan óptimo:");
        System.out.print(mejor.describir(inst, 10));
    }
}

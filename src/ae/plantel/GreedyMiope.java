package ae.plantel;

import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.PrioridadMinutos;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Heurística golosa miope: la referencia que encarna "decidir ventana por
 *
 * [Informe §IV-B(3) heurística golosa miope]
 * ventana en lugar de planificar el horizonte".
 *
 * Recorre las seis ventanas en orden cronológico y, en cada una, acepta la
 * operación individual que más mejora LA TEMPORADA INMEDIATAMENTE SIGUIENTE,
 * ignorando el resto del horizonte y el patrimonio final. Repite hasta que
 * ninguna operación mejore.
 *
 * Es deliberadamente miope, y por eso es la referencia correcta: si el AE le
 * gana, lo que gana es la planificación plurianual, que es la tesis del
 * proyecto. Si no le ganara, todo el planteo del horizonte de tres temporadas
 * sería innecesario y bastaría con una heurística de una línea.
 *
 * Uso:  java -cp out ae.plantel.GreedyMiope [evaluacionesAE]
 */
public final class GreedyMiope {

    private static final Parametros P = new Parametros();
    private static final int N_MIOPE = 10;        // réplicas al evaluar candidatos
    private static final long SEMILLA_MIOPE = 31_000L;
    private static final int N_VALIDACION = 200;
    private static final long SEMILLA_VALIDACION = 777_000L;
    private static final double EPSILON = 0.01;
    private static final int MAX_PASADAS = 6;

    private GreedyMiope() {
    }

    /** Puntos esperados de la temporada t bajo el plan x. Nada más que eso. */
    private static double puntosTemporada(Instancia inst, Cromosoma x, int t) {
        double suma = 0;
        for (int k = 0; k < N_MIOPE; k++) {
            Evaluador.Resultado r =
                    Evaluador.simular(inst, x, new Escenario(SEMILLA_MIOPE + k), P);
            suma += r.puntos[t];
        }
        return suma / N_MIOPE;
    }

    /** Construye el plan goloso. Devuelve también cuántas evaluaciones usó. */
    public static Cromosoma construir(Instancia inst, int[] evaluaciones) {
        Cromosoma plan = Cromosoma.statuQuo(inst);
        evaluaciones[0] = 0;

        for (int ventana = 1; ventana <= Cromosoma.VENTANAS; ventana++) {
            int t = (ventana - 1) / 2;   // temporada que la ventana afecta

            for (int pasada = 0; pasada < MAX_PASADAS; pasada++) {
                double actual = puntosTemporada(inst, plan, t);
                evaluaciones[0]++;

                double mejorValor = actual;
                int mejorGen = -1;
                int mejorAccion = -1;

                for (int i = 0; i < plan.n; i++) {
                    int dominio = Cromosoma.dominioAccion(inst, i);
                    for (int a = 0; a < dominio; a++) {
                        if (a == plan.accion[i] && plan.ventana[i] == ventana) {
                            continue;
                        }
                        Cromosoma c = plan.copia();
                        c.accion[i] = a;
                        c.ventana[i] = ventana;
                        c.duracion[i] = 4;
                        c.salario[i] = 1.10;
                        c.prioridad[i] = PrioridadMinutos.ROTACION.ordinal();
                        double v = puntosTemporada(inst, c, t);
                        evaluaciones[0]++;
                        if (v > mejorValor) {
                            mejorValor = v;
                            mejorGen = i;
                            mejorAccion = a;
                        }
                    }
                }

                if (mejorGen < 0 || mejorValor <= actual + EPSILON) {
                    break;   // ninguna operación mejora esta temporada
                }
                plan.accion[mejorGen] = mejorAccion;
                plan.ventana[mejorGen] = ventana;
                plan.duracion[mejorGen] = 4;
                plan.salario[mejorGen] = 1.10;
                plan.prioridad[mejorGen] = PrioridadMinutos.ROTACION.ordinal();
            }
        }
        return plan;
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        final String linea = "=".repeat(78);
        int evalsAE = args.length > 0 ? Integer.parseInt(args[0]) : 8000;

        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        System.out.println(inst.resumen());

        // --- greedy ---
        int[] evals = new int[1];
        long t0 = System.nanoTime();
        Cromosoma greedy = construir(inst, evals);
        double dtGreedy = (System.nanoTime() - t0) / 1e9;

        // --- AE ---
        AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
        cfg.maxEvaluaciones = evalsAE;
        AlgoritmoEvolutivo.Resultado res = AlgoritmoEvolutivo.ejecutar(inst, cfg, P);

        // --- comparación, todos revaluados igual ---
        Evaluador.Fitness fsq = Evaluador.evaluar(
                inst, Cromosoma.statuQuo(inst), N_VALIDACION, SEMILLA_VALIDACION, P);
        Evaluador.Fitness fgr = Evaluador.evaluar(
                inst, greedy, N_VALIDACION, SEMILLA_VALIDACION, P);
        Evaluador.Fitness fae = Evaluador.evaluar(
                inst, res.mejor, N_VALIDACION, SEMILLA_VALIDACION, P);

        System.out.println();
        System.out.println(linea);
        System.out.println("Comparación (N=200, escenarios distintos de los de búsqueda)");
        System.out.println(linea);
        System.out.printf("%-22s%10s%10s%10s%12s%10s%n",
                "método", "f(x)", "U media", "desvío", "evals", "seg");
        System.out.printf("%-22s%10.2f%10.2f%10.2f%12d%10.1f%n",
                "statu quo", fsq.valor, fsq.utilidadMedia, fsq.desvio, 1, 0.0);
        System.out.printf("%-22s%10.2f%10.2f%10.2f%12d%10.1f%n",
                "greedy miope", fgr.valor, fgr.utilidadMedia, fgr.desvio, evals[0], dtGreedy);
        System.out.printf("%-22s%10.2f%10.2f%10.2f%12d%10.1f%n",
                "algoritmo evolutivo", fae.valor, fae.utilidadMedia, fae.desvio,
                res.evaluaciones, res.segundos);

        System.out.println();
        System.out.printf("  mejora del AE sobre el greedy: %+.1f%%%n",
                100.0 * (fae.valor - fgr.valor) / Math.abs(fgr.valor));
        System.out.printf("  mejora del greedy sobre el statu quo: %+.1f%%%n",
                100.0 * (fgr.valor - fsq.valor) / Math.abs(fsq.valor));

        System.out.println();
        System.out.println(linea);
        System.out.println("Plan goloso (primeras filas)");
        System.out.println(linea);
        System.out.printf("  operaciones: %d de %d genes%n%n",
                greedy.operacionesPedidas(inst), greedy.n);
        System.out.print(greedy.describir(inst, 10));
    }
}

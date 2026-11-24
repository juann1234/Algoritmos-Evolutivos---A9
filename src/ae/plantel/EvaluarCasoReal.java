package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Caso real: FC Barcelona 2020-2023 con parámetros calibrados.
 *
 * Compara, sobre la misma instancia real y los mismos escenarios de
 * validación, tres planes:
 *   - statu quo (no operar);
 *   - el plan que el club efectivamente ejecutó (instancias/plan_real_barcelona.csv);
 *   - el mejor plan del AE, con el protocolo final (N = 10) y el presupuesto
 *     que se indique.
 *
 * El desglose (puntos por temporada, patrimonio, pérdidas por cláusula y por
 * vencimiento, operaciones concretadas) es lo que permite leer POR QUÉ un plan
 * rinde más que otro, no solo cuánto.
 *
 * Uso:  java -cp out ae.plantel.EvaluarCasoReal [instancia] [evaluaciones] [semillas] [primera]
 *       por defecto: barcelona-2020-chica 24000 3 1
 *       (primera semilla distinta de 1 sirve para correr por partes)
 *
 * [Informe §IV-B comparación con el plan real del club; §IV-A instancias reales]
 */
public final class EvaluarCasoReal {

    private static final int N_VAL = AlgoritmoEvolutivo.N_VALIDACION;
    private static final long SEMILLA_VAL = AlgoritmoEvolutivo.SEMILLA_VALIDACION;

    private EvaluarCasoReal() {
    }

    /** Desglose medio de un plan sobre los escenarios de validación. */
    private static double[] desglose(Instancia inst, Cromosoma x, Parametros p) {
        double[] d = new double[11];
        for (int k = 0; k < N_VAL; k++) {
            Evaluador.Resultado r = Evaluador.simular(inst, x, new Escenario(SEMILLA_VAL + k), p);
            d[0] += r.utilidad;
            for (int t = 0; t < 3; t++) {
                d[1 + t] += r.puntos[t];
            }
            d[4] += r.caja;
            d[5] += r.valorPlantel;
            d[6] += r.plantelFinal;
            d[7] += r.salidasLibres;
            d[8] += r.perdidosClausula;
            d[9] += r.concretadas;
            d[10] += r.descartadas;
        }
        for (int i = 0; i < d.length; i++) {
            d[i] /= N_VAL;
        }
        return d;
    }

    /**
     * Qué operaciones del plan real no se concretan y por qué, sobre los
     * escenarios de validación. Distingue los motivos legítimos (otro club se
     * llevó al jugador, el jugador no aceptó, no aparece comprador) de los que
     * indicarían un problema de armado del plan.
     */
    private static void diagnosticarDescartes(Instancia inst, Cromosoma x, Parametros p) {
        java.util.Map<String, Integer> conteo = new java.util.TreeMap<>();
        Evaluador.traza = linea -> conteo.merge(linea, 1, Integer::sum);
        try {
            for (int k = 0; k < N_VAL; k++) {
                Evaluador.simular(inst, x, new Escenario(SEMILLA_VAL + k), p);
            }
        } finally {
            Evaluador.traza = null;
        }
        System.out.println("\nOperaciones del plan real que no se concretan (en % de los escenarios):");
        conteo.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> System.out.printf("  %5.1f%% %s%n", 100.0 * e.getValue() / N_VAL, e.getKey().trim()));
        System.out.println();
    }

    private static void imprimir(String nombre, double f, double[] d) {
        System.out.printf("%-16s %8.2f %7.1f %7.1f %7.1f %8.1f %8.1f %6.1f %6.1f %6.2f %6.1f %6.1f%n",
                nombre, f, d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10]);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        String nombre = args.length > 0 ? args[0] : "barcelona-2020-chica";
        int evaluaciones = args.length > 1 ? Integer.parseInt(args[1]) : 24_000;
        int semillas = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        int primera = args.length > 3 ? Integer.parseInt(args[3]) : 1;

        Parametros p = Parametros.calibrados();
        Instancia inst = InstanciasReales.cargar(nombre);
        System.out.println(inst.resumen());
        System.out.println("Parámetros calibrados con datos reales (D-J2). Validación: N = "
                + N_VAL + " escenarios.\n");

        Cromosoma sq = Cromosoma.statuQuo(inst);
        Cromosoma real = InstanciasReales.planReal(inst);

        System.out.printf("%-16s %8s %7s %7s %7s %8s %8s %6s %6s %6s %6s %6s%n", "plan", "f(x)",
                "pts T1", "pts T2", "pts T3", "caja", "pases", "plant.", "libres", "cláus.",
                "conc.", "desc.");
        double fsq = Evaluador.evaluar(inst, sq, N_VAL, SEMILLA_VAL, p).valor;
        imprimir("statu quo", fsq, desglose(inst, sq, p));
        double freal = Evaluador.evaluar(inst, real, N_VAL, SEMILLA_VAL, p).valor;
        imprimir("plan real", freal, desglose(inst, real, p));
        System.out.printf("%-16s operaciones pedidas por el plan real: %d%n", "",
                real.operacionesPedidas(inst));
        diagnosticarDescartes(inst, real, p);

        double suma = 0;
        double mejor = Double.NEGATIVE_INFINITY;
        Cromosoma mejorPlan = null;
        for (int s = primera; s < primera + semillas; s++) {
            AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
            cfg.replicas = AlgoritmoEvolutivo.N_FINAL;
            cfg.maxEvaluaciones = evaluaciones;
            cfg.semilla = s;
            AlgoritmoEvolutivo.Resultado r = AlgoritmoEvolutivo.ejecutar(inst, cfg, p);
            double f = r.fitnessMejor.valor;
            suma += f;
            imprimir("AE semilla " + s, f, desglose(inst, r.mejor, p));
            if (f > mejor) {
                mejor = f;
                mejorPlan = r.mejor;
            }
        }
        double media = suma / semillas;
        System.out.printf("%nAE media de %d semillas: %.2f  (%+.1f%% sobre el plan real, %+.1f%% sobre el statu quo)%n",
                semillas, media, 100 * (media - freal) / Math.abs(freal), 100 * (media - fsq) / Math.abs(fsq));
        System.out.printf("Plan real: %+.1f%% sobre el statu quo%n", 100 * (freal - fsq) / Math.abs(fsq));

        System.out.println("\nMejor plan del AE (operaciones):");
        System.out.print(mejorPlan.describir(inst, 40));
    }
}

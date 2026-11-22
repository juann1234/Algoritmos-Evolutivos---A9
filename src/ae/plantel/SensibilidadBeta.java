package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Sensibilidad al peso del patrimonio (beta).
 *
 * [Informe §I-C peso β de la ec. (2); motivación de la variante NSGA-II de §III-C]
 *
 * beta es el tipo de cambio entre millones y puntos de liga, y es el parámetro
 * que decide QUÉ optimiza el AE. Con beta alto el plan se vuelve un fondo de
 * inversión en pases; con beta bajo, quema patrimonio para ganar el campeonato
 * de la primera temporada. Ninguno de los dos extremos es "el" óptimo: son
 * puntos distintos del compromiso entre rendimiento y economía.
 *
 * Esta tabla es el argumento para la variante multiobjetivo: en lugar de elegir
 * un beta y defenderlo, NSGA-II devuelve el frente completo.
 *
 * Uso:  java -cp out ae.plantel.SensibilidadBeta [evaluaciones]
 */
public final class SensibilidadBeta {

    public static void main(String[] args) {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        int evaluaciones = args.length > 0 ? Integer.parseInt(args[0]) : 6000;

        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        System.out.println(inst.resumen());
        System.out.println();
        System.out.printf("%7s%12s%12s%12s%12s%12s%n",
                "beta", "f(x)", "puntos T1", "puntos T3", "pases (M)", "plantel");

        for (double beta : new double[] { 0.03, 0.05, 0.10, 0.15 }) {
            Parametros p = new Parametros();
            p.beta = beta;

            AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
            cfg.maxEvaluaciones = evaluaciones;
            AlgoritmoEvolutivo.Resultado res = AlgoritmoEvolutivo.ejecutar(inst, cfg, p);

            int n = 200;
            double p1 = 0, p3 = 0, pases = 0, jug = 0;
            for (int k = 0; k < n; k++) {
                Evaluador.Resultado r =
                        Evaluador.simular(inst, res.mejor, new Escenario(777_000L + k), p);
                p1 += r.puntos[0];
                p3 += r.puntos[2];
                pases += r.valorPlantel;
                jug += r.plantelFinal;
            }
            System.out.printf("%7.2f%12.2f%12.1f%12.1f%12.1f%12.1f%n",
                    beta, res.fitnessMejor.valor, p1 / n, p3 / n, pases / n, jug / n);
        }

        // Referencia: qué da el statu quo en las mismas métricas.
        Parametros p = new Parametros();
        int n = 200;
        double p1 = 0, p3 = 0, pases = 0, jug = 0;
        Cromosoma sq = Cromosoma.statuQuo(inst);
        for (int k = 0; k < n; k++) {
            Evaluador.Resultado r = Evaluador.simular(inst, sq, new Escenario(777_000L + k), p);
            p1 += r.puntos[0];
            p3 += r.puntos[2];
            pases += r.valorPlantel;
            jug += r.plantelFinal;
        }
        System.out.printf("%7s%12s%12.1f%12.1f%12.1f%12.1f%n",
                "—", "statu quo", p1 / n, p3 / n, pases / n, jug / n);
    }
}

package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Cromosoma;
import ae.plantel.Evaluador;
import ae.plantel.GeneradorInstancias;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Validación de la variante multiobjetivo, antes de usarla en experimentos.
 *
 * [Informe §III-C NSGA-II y §IV-C métricas; DECISIONES D-A6]
 *
 *  1. IDENTIDAD f + ρσ = f₁ − β f₂ + C sobre planes al azar y sobre el statu
 *     quo: si falla, f₁ o f₂ no miden lo que dice D-A6.
 *  2. HIPERVOLUMEN de jMetal contra un cálculo 2D a mano.
 *  3. GD = 0, RHV = 1 y spread finito cuando el frente evaluado ES la
 *     referencia; RHV &lt; 1 para un frente dominado.
 *  4. NSGA-II corto: el frente final es no dominado, contiene planes mejores
 *     que el statu quo en f₁, y se reproduce con la misma semilla.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.ValidarMO
 */
public final class ValidarMO {

    private static int fallas;

    private ValidarMO() {
    }

    private static void chequear(String nombre, boolean ok, String detalle) {
        if (!ok) {
            fallas++;
        }
        System.out.printf("  %-58s %s  %s%n", nombre, ok ? "OK   " : "FALLA", detalle);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        Parametros p = new Parametros();
        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        String linea = "=".repeat(78);

        // ---------------- 1. identidad ----------------
        System.out.println(linea);
        System.out.println("1. Identidad f + rho*sd = f1 - beta*f2 + C  (D-A6)");
        System.out.println(linea);
        double c = ProblemaPlantelMO.constanteAgregacion(inst, p);
        Aleatorio rng = new Aleatorio(99);
        double peor = 0;
        for (int k = 0; k < 30; k++) {
            Cromosoma x = k == 0 ? Cromosoma.statuQuo(inst) : Operadores.aleatorioSesgado(inst, rng);
            Evaluador.Fitness f = Evaluador.evaluar(inst, x, 30, 5000 + k, p);
            double izq = f.valor + p.rho * f.desvio;
            double der = f.utilidadDeportivaMedia - p.beta * f.costoNetoMedio + c;
            peor = Math.max(peor, Math.abs(izq - der));
        }
        chequear("30 planes (statu quo + 29 al azar), N=30", peor < 1e-9,
                String.format("máx |diferencia| = %.2e  (C = %.3f)", peor, c));

        // ---------------- 2. hipervolumen ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("2. Hipervolumen de jMetal contra cálculo 2D a mano");
        System.out.println(linea);
        List<double[]> ref = List.of(new double[] { 0, 10 }, new double[] { 2, 6 },
                new double[] { 5, 3 }, new double[] { 9, 0 });
        IndicadoresMO ind = new IndicadoresMO(ref);
        List<double[]> a = List.of(new double[] { 1, 9 }, new double[] { 4, 5 }, new double[] { 8, 2 });
        // A mano: normalización con min (0,0), rango*(1+margen) = (9.9, 11)
        List<double[]> an = new ArrayList<>();
        for (double[] q : a) {
            an.add(new double[] { q[0] / 9.9, q[1] / 11.0 });
        }
        List<double[]> refn = new ArrayList<>();
        for (double[] q : ref) {
            refn.add(new double[] { q[0] / 9.9, q[1] / 11.0 });
        }
        double hvManualA = IndicadoresMO.hipervolumen2dManual(an);
        double hvManualR = IndicadoresMO.hipervolumen2dManual(refn);
        chequear("HV absoluto de A", Math.abs(ind.hipervolumenAbsoluto(a) - hvManualA) < 1e-12,
                String.format("jMetal %.6f, a mano %.6f", ind.hipervolumenAbsoluto(a), hvManualA));
        chequear("HV absoluto de la referencia", Math.abs(ind.hipervolumenAbsoluto(ref) - hvManualR) < 1e-12,
                String.format("jMetal %.6f, a mano %.6f", ind.hipervolumenAbsoluto(ref), hvManualR));

        // ---------------- 3. propiedades de las métricas ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("3. Propiedades de las métricas");
        System.out.println(linea);
        chequear("RHV(referencia) = 1", Math.abs(ind.hipervolumenRelativo(ref) - 1.0) < 1e-12,
                String.format("%.6f", ind.hipervolumenRelativo(ref)));
        chequear("GD(referencia) = 0", ind.distanciaGeneracional(ref) < 1e-12,
                String.format("%.2e", ind.distanciaGeneracional(ref)));
        double rhvA = ind.hipervolumenRelativo(a);
        chequear("frente dominado: 0 < RHV < 1", rhvA > 0 && rhvA < 1, String.format("%.4f", rhvA));
        chequear("frente dominado: GD > 0", ind.distanciaGeneracional(a) > 0,
                String.format("%.4f", ind.distanciaGeneracional(a)));
        double sp = ind.spread(ref);
        chequear("spread de la referencia en [0, 1]", sp >= 0 && sp <= 1, String.format("%.4f", sp));

        // ---------------- 4. NSGA-II corto ----------------
        System.out.println();
        System.out.println(linea);
        System.out.println("4. NSGA-II corto (pob 40, 2.000 evaluaciones)");
        System.out.println(linea);
        AlgoritmoEvolutivo.Config cfg = new AlgoritmoEvolutivo.Config();
        cfg.poblacion = 40;
        cfg.maxEvaluaciones = 2000;
        cfg.semilla = 3;
        NSGAIIPlantel nsga = new NSGAIIPlantel(inst, cfg, p);
        nsga.run();
        List<SolucionPlantel> frente = nsga.getResult();
        List<double[]> pts = IndicadoresMO.puntos(frente);
        chequear("presupuesto respetado", nsga.evaluaciones() <= cfg.maxEvaluaciones,
                nsga.evaluaciones() + " evaluaciones, " + nsga.generaciones() + " generaciones");
        chequear("frente final no dominado", IndicadoresMO.noDominados(pts).size() == pts.size(),
                pts.size() + " puntos");
        ProblemaPlantelMO val = new ProblemaPlantelMO(inst, p, AlgoritmoEvolutivo.N_VALIDACION,
                AlgoritmoEvolutivo.SEMILLA_VALIDACION, new Aleatorio(0));
        SolucionPlantel sq = SolucionPlantel.desde(Cromosoma.statuQuo(inst), 2);
        val.evaluate(sq);
        double f1max = Double.NEGATIVE_INFINITY;
        for (SolucionPlantel s : frente) {
            f1max = Math.max(f1max, ProblemaPlantelMO.utilidadDeportiva(s));
        }
        chequear("algún plan supera al statu quo en f1", f1max > ProblemaPlantelMO.utilidadDeportiva(sq),
                String.format("mejor f1 %.2f vs statu quo %.2f", f1max, ProblemaPlantelMO.utilidadDeportiva(sq)));
        NSGAIIPlantel otra = new NSGAIIPlantel(inst, cfg, p);
        otra.run();
        List<double[]> pts2 = IndicadoresMO.puntos(otra.getResult());
        boolean igual = pts.size() == pts2.size();
        for (int i = 0; igual && i < pts.size(); i++) {
            igual = java.util.Arrays.equals(pts.get(i), pts2.get(i));
        }
        chequear("misma semilla, mismo frente", igual, "");
        System.out.println();
        System.out.println("  frente (f1 utilidad deportiva, f2 costo neto en M):");
        for (double[] q : IndicadoresMO.noDominados(pts)) {
            System.out.printf("    f1 = %7.2f   f2 = %8.2f%n", -q[0], q[1]);
        }
        System.out.printf("    statu quo: f1 = %7.2f   f2 = %8.2f%n",
                ProblemaPlantelMO.utilidadDeportiva(sq), ProblemaPlantelMO.costoNeto(sq));

        System.out.println();
        System.out.println(fallas == 0 ? "Todas las validaciones multiobjetivo OK." : fallas + " FALLAS");
        if (fallas > 0) {
            System.exit(1);
        }
    }
}

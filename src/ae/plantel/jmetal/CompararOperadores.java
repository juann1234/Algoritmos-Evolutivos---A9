package ae.plantel.jmetal;

import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.Estadistica;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Parametros;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Estudio comparativo de operadores con parámetros fijos (Tema 12: "puede
 * incorporarse un estudio acotado de operadores con parámetros fijos").
 *
 * [Informe §III-C: "se evaluará también selección proporcional con escalado" y
 * "se comparará contra un cruzamiento por bloques posicionales"] (Alen, P10-P12)
 *
 * Variantes, todas sobre {@link AGPlantel} con la configuración base:
 * <ul>
 *   <li>base: torneo de 3 + cruzamiento uniforme a nivel de gen;</li>
 *   <li>proporcional: selección proporcional con escalado (truncamiento sigma
 *       + lineal) y ruleta con punteros equiespaciados;</li>
 *   <li>bloques: torneo de 3 + cruzamiento por bloques posicionales.</li>
 * </ul>
 * La semilla s de cada variante comparte población inicial y escenarios con
 * la de la base, así que las muestras son pareadas: Wilcoxon de rangos con
 * signo (exacto si n ≤ 50) con post-hoc de Holm, como CompararVariantes.
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.CompararOperadores [semillas] [evaluaciones] [instancia]
 */
public final class CompararOperadores {

    private CompararOperadores() {
    }

    static Map<String, AlgoritmoEvolutivo.Config> variantes() {
        Map<String, AlgoritmoEvolutivo.Config> v = new LinkedHashMap<>();
        v.put("base", new AlgoritmoEvolutivo.Config());
        AlgoritmoEvolutivo.Config prop = new AlgoritmoEvolutivo.Config();
        prop.seleccion = AlgoritmoEvolutivo.Seleccion.PROPORCIONAL_ESCALADA;
        v.put("proporcional", prop);
        AlgoritmoEvolutivo.Config bloques = new AlgoritmoEvolutivo.Config();
        bloques.cruce = AlgoritmoEvolutivo.Cruce.BLOQUES_POSICIONALES;
        v.put("bloques", bloques);
        return v;
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        int semillas = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        int evaluaciones = args.length > 1 ? Integer.parseInt(args[1]) : 8000;
        String nombre = args.length > 2 ? args[2] : "demo";
        Instancia inst = ExperimentoMO.instancia(nombre);
        Parametros p = new Parametros();
        System.out.println(inst.resumen());
        System.out.printf("%d semillas por variante, %d evaluaciones, fitness revaluado con N=200%n%n",
                semillas, evaluaciones);

        Map<String, AlgoritmoEvolutivo.Config> vs = variantes();
        ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Map<String, List<Future<AlgoritmoEvolutivo.Resultado>>> futuros = new LinkedHashMap<>();
        for (Map.Entry<String, AlgoritmoEvolutivo.Config> e : vs.entrySet()) {
            List<Future<AlgoritmoEvolutivo.Resultado>> l = new ArrayList<>();
            for (int s = 1; s <= semillas; s++) {
                AlgoritmoEvolutivo.Config c = copia(e.getValue());
                c.semilla = s;
                c.maxEvaluaciones = evaluaciones;
                l.add(pool.submit((Callable<AlgoritmoEvolutivo.Resultado>) () -> AGPlantel.ejecutar(inst, c, p)));
            }
            futuros.put(e.getKey(), l);
        }
        Map<String, double[]> fit = new LinkedHashMap<>();
        Map<String, double[]> div = new LinkedHashMap<>();
        StringBuilder csv = new StringBuilder("variante,semilla,presupuesto,fitness,puntos,div_final,segundos\n");
        for (Map.Entry<String, List<Future<AlgoritmoEvolutivo.Resultado>>> e : futuros.entrySet()) {
            double[] f = new double[semillas];
            double[] d = new double[semillas];
            for (int i = 0; i < semillas; i++) {
                AlgoritmoEvolutivo.Resultado r = e.getValue().get(i).get();
                f[i] = r.fitnessMejor.valor;
                d[i] = r.historialDiversidad.get(r.historialDiversidad.size() - 1);
                csv.append(String.format(Locale.ROOT, "%s,%d,%d,%.4f,%.4f,%.2f,%.1f%n", e.getKey(), i + 1,
                        evaluaciones, f[i], r.fitnessMejor.puntosMedios, d[i], r.segundos));
            }
            fit.put(e.getKey(), f);
            div.put(e.getKey(), d);
        }
        pool.shutdown();

        System.out.printf("%-14s%10s%9s%9s%9s%11s%9s%n", "variante", "media", "desvío", "mediana",
                "mejor", "div.final", "SW p");
        for (String v : fit.keySet()) {
            double[] f = fit.get(v);
            System.out.printf("%-14s%10.2f%9.2f%9.2f%9.2f%11.1f%9.3f%n", v, Estadistica.media(f),
                    Estadistica.desvio(f), Estadistica.mediana(f), Estadistica.maximo(f),
                    Estadistica.media(div.get(v)), Estadistica.shapiroWilk(f)[1]);
        }
        System.out.println();
        System.out.println("Cada variante contra la base (Wilcoxon pareado por semilla, post-hoc de Holm):");
        double[] base = fit.get("base");
        List<String> nombres = new ArrayList<>();
        List<Double> crudos = new ArrayList<>();
        for (String v : fit.keySet()) {
            if (!v.equals("base")) {
                nombres.add(v);
                crudos.add(Estadistica.wilcoxon(fit.get(v), base)[1]);
            }
        }
        double[] holm = Estadistica.holm(crudos.stream().mapToDouble(Double::doubleValue).toArray());
        for (int i = 0; i < nombres.size(); i++) {
            double dif = Estadistica.media(fit.get(nombres.get(i))) - Estadistica.media(base);
            String veredicto = holm[i] >= 0.05 ? "sin diferencia significativa"
                    : dif > 0 ? "MEJOR que la base" : "PEOR que la base";
            System.out.printf("  %-14s dif. media %+7.2f  p = %.4f  p Holm = %.4f  -> %s%n", nombres.get(i),
                    dif, crudos.get(i), holm[i], veredicto);
        }
        Path destino = Path.of("resultados/operadores_" + nombre + ".csv");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("  datos crudos en " + destino);
    }

    private static AlgoritmoEvolutivo.Config copia(AlgoritmoEvolutivo.Config o) {
        AlgoritmoEvolutivo.Config c = new AlgoritmoEvolutivo.Config();
        c.poblacion = o.poblacion;
        c.pC = o.pC;
        c.pM = o.pM;
        c.elite = o.elite;
        c.torneo = o.torneo;
        c.replicas = o.replicas;
        c.seleccion = o.seleccion;
        c.cruce = o.cruce;
        return c;
    }
}

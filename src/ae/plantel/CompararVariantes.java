package ae.plantel;

import ae.plantel.Modelo.Instancia;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Comparación de las variantes del AE contra la configuración base.
 *
 * [Informe §III-C variantes del modelo evolutivo y diversidad; §IV-B análisis estadístico]
 *
 * Todas las variantes corren con el mismo presupuesto de evaluaciones (que
 * incluye reevaluaciones y búsqueda local), las mismas semillas y la misma
 * selección final. El fitness que se compara es el revaluado con N=200 sobre
 * escenarios de validación. La semilla s de una variante y la de la base
 * comparten la población inicial, así que las muestras son PAREADAS: se
 * comparan con Wilcoxon de rangos con signo y post-hoc de Holm (D-A5).
 *
 * INSTANCIA. La comparación original se hizo sobre "demo", que es la misma
 * instancia que eval-chica (DECISIONES D-A16). El Tema 12 pide decidir el
 * diseño sobre instancias de configuración distintas de las de evaluación,
 * así que la instancia es un argumento (PENDIENTES P25). Cada instancia
 * acumula sus resultados en su propio CSV.
 *
 * PRESUPUESTO. El argumento de evaluaciones está expresado con N = 30
 * réplicas. Una variante con otro N recibe la cantidad de evaluaciones que
 * da el mismo total de simulaciones (evaluaciones x 30 / N). Así la variante
 * "n10" verifica, con este motor y no con el de jMetal, la decisión D-A13
 * (N = 10 a igual costo).
 *
 * Se corre por partes (el contenedor corta los procesos largos):
 *
 *   java -cp out ae.plantel.CompararVariantes correr conf-joven base 1 10 8000
 *   java -cp out ae.plantel.CompararVariantes correr conf-joven chc 1 10 8000
 *   ...
 *   java -cp out ae.plantel.CompararVariantes analizar conf-joven 8000
 *
 * La forma vieja, sin instancia (correr base 1 10 8000 / analizar 8000),
 * sigue funcionando sobre demo.
 */
public final class CompararVariantes {

    private static final Parametros P = new Parametros();

    /** N con el que se expresa el presupuesto de evaluaciones. */
    private static final int N_REFERENCIA = new AlgoritmoEvolutivo.Config().replicas;

    /** Cada instancia acumula en su CSV; demo conserva el nombre original. */
    private static Path csv(String instancia) {
        return Path.of(instancia.equals("demo")
                ? "resultados_variantes.csv"
                : "resultados_variantes_" + instancia + ".csv");
    }

    /** Instancias disponibles por nombre. */
    private static Instancia instancia(String nombre) {
        if (nombre.equals("demo")) {
            return GeneradorInstancias.generar("demo", 25, 50, 101);
        }
        List<Instancia> todas = new ArrayList<>(GeneradorInstancias.configuracion());
        todas.addAll(GeneradorInstancias.evaluacion());
        for (Instancia i : todas) {
            if (i.nombre.equals(nombre)) {
                return i;
            }
        }
        throw new IllegalArgumentException("instancia desconocida: " + nombre);
    }

    /** Variantes del temario, todas sobre la configuración base. */
    static Map<String, AlgoritmoEvolutivo.Config> variantes() {
        Map<String, AlgoritmoEvolutivo.Config> v = new LinkedHashMap<>();
        v.put("base", new AlgoritmoEvolutivo.Config());

        AlgoritmoEvolutivo.Config gap = new AlgoritmoEvolutivo.Config();
        gap.gap = 0.5;
        v.put("gap50", gap);

        AlgoritmoEvolutivo.Config ee = new AlgoritmoEvolutivo.Config();
        ee.gap = 2.0 / ee.poblacion;   // estado estacionario: 2 hijos por generación
        v.put("estacionario", ee);

        AlgoritmoEvolutivo.Config sh = new AlgoritmoEvolutivo.Config();
        sh.sigmaSharing = 6;
        v.put("sharing", sh);

        AlgoritmoEvolutivo.Config chc = new AlgoritmoEvolutivo.Config();
        chc.modelo = AlgoritmoEvolutivo.Modelo.CHC;
        v.put("chc", chc);

        AlgoritmoEvolutivo.Config mem = new AlgoritmoEvolutivo.Config();
        mem.memeticoCada = 5;
        v.put("memetico", mem);

        // Verificación independiente de D-A13: N = 10 con el mismo total de
        // simulaciones que la base (el triple de evaluaciones).
        AlgoritmoEvolutivo.Config n10 = new AlgoritmoEvolutivo.Config();
        n10.replicas = 10;
        v.put("n10", n10);
        return v;
    }

    public static void main(String[] args) throws IOException {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length > 0 && args[0].equals("analizar")) {
            if (args.length > 2) {
                analizar(args[1], Integer.parseInt(args[2]));
            } else {
                analizar("demo", args.length > 1 ? Integer.parseInt(args[1]) : 8000);
            }
            return;
        }
        // Forma nueva: correr <instancia> <variante> <desde> <hasta> <evals>.
        // Forma vieja (5 argumentos): correr <variante> <desde> <hasta> <evals>, sobre demo.
        int k = args.length == 6 ? 2 : 1;
        String nombreInstancia = args.length == 6 ? args[1] : "demo";
        String nombre = args[k];
        int desde = Integer.parseInt(args[k + 1]);
        int hasta = Integer.parseInt(args[k + 2]);
        int evaluaciones = Integer.parseInt(args[k + 3]);
        Path CSV = csv(nombreInstancia);

        Instancia inst = instancia(nombreInstancia);
        AlgoritmoEvolutivo.Config base = variantes().get(nombre);
        if (base == null) {
            throw new IllegalArgumentException("variante desconocida: " + nombre
                    + " (opciones: " + variantes().keySet() + ")");
        }

        for (int s = desde; s <= hasta; s++) {
            AlgoritmoEvolutivo.Config cfg = copiar(base);
            cfg.semilla = s;
            // Mismo total de simulaciones para todas las variantes.
            cfg.maxEvaluaciones = (int) ((long) evaluaciones * N_REFERENCIA / cfg.replicas);
            AlgoritmoEvolutivo.Resultado r = AlgoritmoEvolutivo.ejecutar(inst, cfg, P);
            List<Double> div = r.historialDiversidad;
            String linea = String.format(java.util.Locale.ROOT,
                    "%s,%d,%d,%.4f,%.4f,%d,%d,%.2f,%.2f,%d,%d,%.1f%n",
                    nombre, s, evaluaciones, r.fitnessMejor.valor, r.fitnessMejor.puntosMedios,
                    r.evaluaciones, r.generaciones, div.get(0), div.get(div.size() - 1),
                    r.cataclismos, r.mejorasLocales, r.segundos);
            if (!Files.exists(CSV)) {
                Files.writeString(CSV, "variante,semilla,presupuesto,fitness,puntos,"
                        + "evaluaciones,generaciones,div_inicial,div_final,cataclismos,"
                        + "mejoras_locales,segundos\n", StandardCharsets.UTF_8);
            }
            Files.writeString(CSV, linea, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            System.out.print(linea);
        }
    }

    private static AlgoritmoEvolutivo.Config copiar(AlgoritmoEvolutivo.Config o) {
        AlgoritmoEvolutivo.Config c = new AlgoritmoEvolutivo.Config();
        c.poblacion = o.poblacion;
        c.pC = o.pC;
        c.pM = o.pM;
        c.elite = o.elite;
        c.torneo = o.torneo;
        c.replicas = o.replicas;
        c.modelo = o.modelo;
        c.gap = o.gap;
        c.sigmaSharing = o.sigmaSharing;
        c.memeticoCada = o.memeticoCada;
        c.memeticoUmbral = o.memeticoUmbral;
        return c;
    }

    // ----------------------------------------------------------------
    // Análisis
    // ----------------------------------------------------------------

    private static void analizar(String nombreInstancia, int presupuesto) throws IOException {
        Path CSV = csv(nombreInstancia);
        Map<String, TreeMap<Integer, double[]>> datos = new LinkedHashMap<>();
        for (String v : variantes().keySet()) {
            datos.put(v, new TreeMap<>());
        }
        List<String> lineas = Files.readAllLines(CSV, StandardCharsets.UTF_8);
        for (String l : lineas.subList(1, lineas.size())) {
            String[] c = l.split(",");
            if (Integer.parseInt(c[2]) != presupuesto) {
                continue;   // cada análisis compara un único presupuesto
            }
            datos.get(c[0]).put(Integer.parseInt(c[1]), new double[] {
                    Double.parseDouble(c[3]), Double.parseDouble(c[4]),
                    Double.parseDouble(c[6]), Double.parseDouble(c[8]),
                    Double.parseDouble(c[9]), Double.parseDouble(c[10]),
                    Double.parseDouble(c[11]) });
        }

        final String linea = "=".repeat(98);
        System.out.println(linea);
        System.out.println("Variantes del AE (instancia " + nombreInstancia + ", " + presupuesto
                + " evaluaciones equivalentes con N=30, fitness revaluado con N=200)");
        System.out.println(linea);
        System.out.printf("%-14s%4s%10s%9s%9s%9s%8s%10s%8s%8s%7s%n", "variante", "n", "media",
                "desvío", "mejor", "puntos", "gen.", "div.final", "catacl.", "loc.", "seg");
        for (Map.Entry<String, TreeMap<Integer, double[]>> e : datos.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            List<double[]> filas = new ArrayList<>(e.getValue().values());
            double[] f = columna(filas, 0);
            System.out.printf("%-14s%4d%10.2f%9.2f%9.2f%9.1f%8.0f%10.1f%8.1f%8.1f%7.1f%n",
                    e.getKey(), f.length, Estadistica.media(f), Estadistica.desvio(f),
                    Estadistica.maximo(f), Estadistica.media(columna(filas, 1)),
                    Estadistica.media(columna(filas, 2)), Estadistica.media(columna(filas, 3)),
                    Estadistica.media(columna(filas, 4)), Estadistica.media(columna(filas, 5)),
                    Estadistica.media(columna(filas, 6)));
        }

        System.out.println();
        System.out.println(linea);
        System.out.println("Cada variante contra la base (Wilcoxon pareado por semilla, post-hoc de Holm)");
        System.out.println(linea);
        TreeMap<Integer, double[]> base = datos.get("base");
        int comparaciones = 0;
        for (String v : datos.keySet()) {
            if (!v.equals("base") && !datos.get(v).isEmpty()) {
                comparaciones++;
            }
        }
        // Primero los p crudos de todas las comparaciones; después Holm sobre
        // la familia completa (DECISIONES D-A5).
        List<String> nombres = new ArrayList<>();
        List<double[]> resultados = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Integer, double[]>> e : datos.entrySet()) {
            if (e.getKey().equals("base") || e.getValue().isEmpty()) {
                continue;
            }
            List<Double> a = new ArrayList<>();
            List<Double> b = new ArrayList<>();
            for (Map.Entry<Integer, double[]> fila : e.getValue().entrySet()) {
                if (base.containsKey(fila.getKey())) {
                    a.add(fila.getValue()[0]);
                    b.add(base.get(fila.getKey())[0]);
                }
            }
            double[] va = a.stream().mapToDouble(Double::doubleValue).toArray();
            double[] vb = b.stream().mapToDouble(Double::doubleValue).toArray();
            double[] w = Estadistica.wilcoxon(va, vb);
            double dif = Estadistica.media(va) - Estadistica.media(vb);
            nombres.add(e.getKey());
            resultados.add(new double[] { dif, w[0], w[1] });
        }
        double[] crudos = new double[resultados.size()];
        for (int i = 0; i < crudos.length; i++) {
            crudos[i] = resultados.get(i)[2];
        }
        double[] holm = Estadistica.holm(crudos);
        for (int i = 0; i < crudos.length; i++) {
            double dif = resultados.get(i)[0];
            String veredicto = holm[i] >= 0.05 ? "sin diferencia significativa"
                    : (dif > 0 ? "MEJOR que la base" : "PEOR que la base");
            System.out.printf("  %-14s dif. media %+7.2f  z = %6.2f  p = %.4f  p Holm = %.4f  -> %s%n",
                    nombres.get(i), dif, resultados.get(i)[1], crudos[i], holm[i], veredicto);
        }
        if (comparaciones != crudos.length) {
            throw new IllegalStateException("cantidad de comparaciones inconsistente");
        }
    }

    private static double[] columna(List<double[]> filas, int c) {
        double[] out = new double[filas.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = filas.get(i)[c];
        }
        return out;
    }
}

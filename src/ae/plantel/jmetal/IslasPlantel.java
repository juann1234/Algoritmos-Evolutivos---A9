package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.AlgoritmoEvolutivo;
import ae.plantel.AlgoritmoEvolutivo.Candidato;
import ae.plantel.AlgoritmoEvolutivo.Config;
import ae.plantel.Cromosoma;
import ae.plantel.Evaluador;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;
import ae.plantel.Parametros;
import org.uma.jmetal.util.comparator.ObjectiveComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Modelo de islas (subpoblaciones distribuidas) con migración en anillo.
 *
 * [Informe §III-C: "un esquema paralelo de subpoblaciones distribuidas con
 * migración en anillo [5], que permite absorber el costo de la simulación";
 * §IV-C: "speedup del esquema paralelo al variar los recursos"; Tema 11]
 * (Alen, P13; DECISIONES D-A14)
 *
 * La población total (cfg.poblacion) se reparte en k islas. Cada isla es un AE
 * generacional con elitismo, con los mismos operadores que {@link AGPlantel}
 * (OperadoresJMetal) y su propio generador. Cada {@code intervalo}
 * generaciones, cada isla manda copias de sus {@code migrantes} mejores a la
 * siguiente del anillo, donde reemplazan a los peores.
 *
 * Decisiones que importan:
 * <ul>
 *   <li><b>Sincrónico</b>: todas las islas hacen una generación, se espera a
 *       todas (barrera) y recién ahí se migra. Así el resultado de una semilla
 *       NO depende de cuántos hilos se usen, y el speedup mide solo tiempo.</li>
 *   <li><b>Escenarios comunes entre islas</b>: en cada generación todas las islas
 *       evalúan sobre el mismo conjunto de escenarios; un migrante llega con un
 *       fitness comparable al de los locales.</li>
 *   <li><b>Presupuesto global</b> en evaluaciones (M5), sumando todas las islas,
 *       más la selección final en dos etapas sobre los candidatos de todas.</li>
 * </ul>
 * jMetal 5.10 no trae modelo de islas: se arma con sus componentes (Problem,
 * Solution, operadores) y un ciclo propio.
 */
public final class IslasPlantel {

    private final Instancia inst;
    private final Config cfg;
    private final Parametros p;
    private final int islas;
    private final int intervalo;
    private final int migrantes;
    private final int hilos;
    private final Comparator<SolucionPlantel> porObjetivo = new ObjectiveComparator<>(0);

    /** Una isla: su población, su problema (contador y escenarios) y sus operadores. */
    private final class Isla {
        final ProblemaPlantel problema;
        final Aleatorio rng;
        final OperadoresJMetal.Cruzamiento cruce;
        final OperadoresJMetal.Mutacion mutacion;
        final OperadoresJMetal.Torneo torneo;
        final int tam;
        List<SolucionPlantel> pob;

        Isla(int indice, int tam) {
            this.tam = tam;
            this.rng = new Aleatorio(cfg.semilla * 1_000L + indice);
            this.problema = new ProblemaPlantel(inst, p, cfg.replicas, cfg.semilla * 1_000_003L, rng);
            this.cruce = new OperadoresJMetal.Cruzamiento(cfg.pC, rng, false);
            this.mutacion = new OperadoresJMetal.Mutacion(inst, cfg.pM, rng, false);
            this.torneo = new OperadoresJMetal.Torneo(cfg.torneo, porObjetivo, rng);
            this.pob = new ArrayList<>();
            for (Cromosoma c : Operadores.poblacionInicial(inst, tam, rng)) {
                pob.add(SolucionPlantel.desde(c, 1));
            }
        }

        void evaluarTodo(long semilla) {
            problema.fijarSemillaEscenarios(semilla);
            for (SolucionPlantel s : pob) {
                problema.evaluate(s);
            }
        }

        /** Una generación: torneo, cruzamiento, mutación, élite reevaluada. */
        Void generacion(long semilla) {
            int elite = Math.min(cfg.elite, tam - 1);
            List<SolucionPlantel> ordenada = new ArrayList<>(pob);
            ordenada.sort(porObjetivo);
            List<SolucionPlantel> hijos = new ArrayList<>(tam);
            while (hijos.size() < tam - elite) {
                SolucionPlantel a = torneo.execute(pob);
                SolucionPlantel b = torneo.execute(pob);
                for (SolucionPlantel h : cruce.execute(List.of(a, b))) {
                    if (hijos.size() < tam - elite) {
                        hijos.add(mutacion.execute(h));
                    }
                }
            }
            problema.fijarSemillaEscenarios(semilla);
            List<SolucionPlantel> nueva = new ArrayList<>(tam);
            for (int e = 0; e < elite; e++) {
                SolucionPlantel c = ordenada.get(e).copy();
                problema.evaluate(c);
                nueva.add(c);
            }
            for (SolucionPlantel h : hijos) {
                problema.evaluate(h);
                nueva.add(h);
            }
            pob = nueva;
            return null;
        }

        SolucionPlantel mejor() {
            return pob.stream().min(porObjetivo).orElseThrow();
        }
    }

    /**
     * @param islas     cantidad de subpoblaciones (k)
     * @param intervalo generaciones entre migraciones
     * @param migrantes individuos que viajan por migración
     * @param hilos     hilos de ejecución (no cambia el resultado, solo el tiempo)
     */
    public IslasPlantel(Instancia inst, Config cfg, Parametros p, int islas, int intervalo,
                        int migrantes, int hilos) {
        this.inst = inst;
        this.cfg = cfg;
        this.p = p;
        this.islas = islas;
        this.intervalo = intervalo;
        this.migrantes = migrantes;
        this.hilos = hilos;
    }

    public AlgoritmoEvolutivo.Resultado ejecutar() throws Exception {
        long t0 = System.nanoTime();
        AlgoritmoEvolutivo.Resultado out = new AlgoritmoEvolutivo.Resultado();
        List<Isla> archipielago = new ArrayList<>();
        int base = cfg.poblacion / islas;
        for (int i = 0; i < islas; i++) {
            archipielago.add(new Isla(i, base + (i < cfg.poblacion % islas ? 1 : 0)));
        }
        int presupuesto = Math.max(cfg.poblacion, cfg.maxEvaluaciones - cfg.costoSeleccion());
        long semilla = cfg.semilla * 1_000_003L;
        List<Candidato> candidatos = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            for (Isla is : archipielago) {
                is.evaluarTodo(semilla);
            }
            registrar(archipielago, candidatos, out);
            while (presupuesto - usadas(archipielago) >= cfg.poblacion) {
                semilla += cfg.replicas;   // escenarios nuevos, los mismos para todas las islas
                final long sem = semilla;
                List<Future<Void>> fs = new ArrayList<>();
                for (Isla is : archipielago) {
                    fs.add(pool.submit((Callable<Void>) () -> is.generacion(sem)));
                }
                for (Future<Void> f : fs) {
                    f.get();   // barrera: migración sincrónica
                }
                out.generaciones++;
                if (islas > 1 && out.generaciones % intervalo == 0) {
                    migrar(archipielago);
                }
                registrar(archipielago, candidatos, out);
            }
        } finally {
            pool.shutdown();
        }
        out.evaluaciones = (int) usadas(archipielago) + cfg.costoSeleccion();
        out.mejor = AlgoritmoEvolutivo.seleccionFinal(inst, candidatos, cfg, p);
        out.fitnessMejor = Evaluador.evaluar(inst, out.mejor, AlgoritmoEvolutivo.N_VALIDACION,
                AlgoritmoEvolutivo.SEMILLA_VALIDACION, p);
        out.segundos = (System.nanoTime() - t0) / 1e9;
        return out;
    }

    /** Anillo: los mejores de la isla i reemplazan a los peores de la isla i+1. */
    private void migrar(List<Isla> arch) {
        List<List<SolucionPlantel>> salientes = new ArrayList<>();
        for (Isla is : arch) {
            List<SolucionPlantel> ord = new ArrayList<>(is.pob);
            ord.sort(porObjetivo);
            List<SolucionPlantel> m = new ArrayList<>();
            for (int k = 0; k < Math.min(migrantes, ord.size()); k++) {
                m.add(ord.get(k).copy());
            }
            salientes.add(m);
        }
        for (int i = 0; i < arch.size(); i++) {
            Isla destino = arch.get((i + 1) % arch.size());
            destino.pob.sort(porObjetivo);
            List<SolucionPlantel> llegan = salientes.get(i);
            for (int k = 0; k < llegan.size(); k++) {
                destino.pob.set(destino.pob.size() - 1 - k, llegan.get(k));
            }
        }
    }

    private void registrar(List<Isla> arch, List<Candidato> cand, AlgoritmoEvolutivo.Resultado out) {
        SolucionPlantel mejor = null;
        double suma = 0;
        int n = 0;
        for (Isla is : arch) {
            SolucionPlantel m = is.mejor();
            cand.add(new Candidato(m.aCromosoma(), ProblemaPlantel.fitness(m)));
            if (mejor == null || porObjetivo.compare(m, mejor) < 0) {
                mejor = m;
            }
            for (SolucionPlantel s : is.pob) {
                suma += ProblemaPlantel.fitness(s);
                n++;
            }
        }
        out.historialMejor.add(ProblemaPlantel.fitness(mejor));
        out.historialMedia.add(suma / n);
    }

    private static long usadas(List<Isla> arch) {
        long u = 0;
        for (Isla is : arch) {
            u += is.problema.evaluaciones();
        }
        return u;
    }
}

package ae.plantel;

import ae.plantel.Modelo.Contrato;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.JugadorBase;
import ae.plantel.Modelo.Pertenencia;
import ae.plantel.Modelo.Puesto;

import java.util.ArrayList;
import java.util.List;

/**
 * Generador sintético de instancias.
 *
 * Sirve para desarrollar y validar el simulador antes de tener los datos
 * reales, y para construir las instancias de CONFIGURACIÓN paramétrica, que
 * según la metodología del curso deben ser distintas de las de evaluación.
 *
 * Las instancias de evaluación finales se armarán con datos públicos, pero
 * deben cumplir este mismo esquema para que el resto del código no cambie.
 */
public final class GeneradorInstancias {

    /** Proporción de cada puesto al armar un plantel. */
    private static final Puesto[] PUESTOS = Puesto.values();
    private static final double[] MEZCLA = { 0.12, 0.32, 0.32, 0.24 };

    /**
     * Duración de contrato: en un plantel real la mayoría firma por 3-5
     * temporadas. Con una uniforme 1-4 el plan statu quo se vacía entero en el
     * horizonte y el problema degenera.
     */
    private static final int[] ANIOS_CONTRATO = { 2, 3, 4, 5, 6 };
    private static final double[] PESOS_CONTRATO = { 0.15, 0.20, 0.30, 0.20, 0.15 };

    private GeneradorInstancias() {
    }

    /** Techo de nivel: amplio en juveniles, casi nulo pasados los 26. */
    private static double potencial(double theta, int edad, Aleatorio rng) {
        double margenMax = Math.max(0.0, 26 - edad) * 1.6;
        return Math.min(99.0, theta + rng.uniforme(0.3, 1.0) * margenMax);
    }

    private static final Parametros.Valor VALOR = new Parametros().valor;

    /** Salario de referencia en millones por temporada (misma curva que el simulador). */
    private static double salario(double theta) {
        return Math.round(Evolucion.salarioReferencia(theta, VALOR) * 100.0) / 100.0;
    }

    private static List<Puesto> repartoPuestos(int n, Aleatorio rng) {
        List<Puesto> puestos = new ArrayList<>();
        for (int i = 0; i < PUESTOS.length; i++) {
            int minimo = n >= 25 ? Modelo.MINIMOS_POR_PUESTO.get(PUESTOS[i]) : 1;
            int cantidad = Math.max(minimo, (int) Math.round(n * MEZCLA[i]));
            for (int k = 0; k < cantidad; k++) {
                puestos.add(PUESTOS[i]);
            }
        }
        while (puestos.size() > n) {
            puestos.remove(puestos.size() - 1);
        }
        while (puestos.size() < n) {
            puestos.add(PUESTOS[rng.entero(0, PUESTOS.length)]);
        }
        rng.mezclar(puestos);
        return puestos;
    }

    private static double recortar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Construye una instancia sintética reproducible.
     *
     * {@code edadMediaPropia} permite generar los escenarios de plantel
     * envejecido frente a plantel joven que el informe propone como eje de
     * dificultad.
     */
    public static Instancia generar(String nombre, int nPropios, int nMercado, long semilla,
                                    double thetaMedioPropio, double edadMediaPropia,
                                    double presupuesto, double holguraSalarial) {
        Aleatorio rng = new Aleatorio(semilla);
        List<JugadorBase> jugadores = new ArrayList<>();
        int[] idx = { 0 };

        for (Puesto puesto : repartoPuestos(nPropios, rng)) {
            jugadores.add(crear(puesto, Pertenencia.PROPIO, thetaMedioPropio,
                    edadMediaPropia, rng, idx));
        }
        for (Puesto puesto : repartoPuestos(nMercado, rng)) {
            jugadores.add(crear(puesto, Pertenencia.MERCADO, thetaMedioPropio - 1.0,
                    25.0, rng, idx));
        }

        double masa = 0;
        for (JugadorBase j : jugadores) {
            if (j.pertenencia() == Pertenencia.PROPIO) {
                masa += j.contrato().salario();
            }
        }
        double tope = Math.round(masa * holguraSalarial * 10.0) / 10.0;
        // En un plantel reducido los mínimos por puesto se escalan: exigirle
        // 20 jugadores a una micro-instancia de 4 haría que la penalización
        // fuera constante y tapara las diferencias entre planes.
        java.util.Map<Puesto, Integer> minimos = new java.util.EnumMap<>(Puesto.class);
        for (Puesto puesto : PUESTOS) {
            int base = Modelo.MINIMOS_POR_PUESTO.get(puesto);
            minimos.put(puesto, nPropios >= 25
                    ? base
                    : Math.max(1, (int) Math.round(base * nPropios / 25.0)));
        }
        int cupo = Math.max(nPropios, 25);
        return new Instancia(nombre, jugadores, presupuesto, tope, cupo, 7, minimos);
    }

    public static Instancia generar(String nombre, int nPropios, int nMercado, long semilla) {
        return generar(nombre, nPropios, nMercado, semilla, 78.0, 26.5, 120.0, 1.25);
    }

    private static JugadorBase crear(Puesto puesto, Pertenencia pertenencia,
                                     double thetaMedio, double edadMedia,
                                     Aleatorio rng, int[] idx) {
        idx[0]++;
        int edad = (int) recortar(rng.normal(edadMedia, 3.8), 17, 36);
        double theta = recortar(rng.normal(thetaMedio, 4.5), 55, 94);
        int anios = ANIOS_CONTRATO[rng.elegir(PESOS_CONTRATO)];
        double historial = Math.max(0.0, rng.normal(12.0, 18.0));
        // Cláusula heredada: entre 2 y 4 veces el valor de mercado actual. Un
        // juvenil que crezca la dejará chica, y ahí es donde un rival puede
        // llevárselo si el plan no lo renueva a tiempo.
        double clausula = pertenencia == Pertenencia.PROPIO
                ? rng.uniforme(2.0, 4.0) * Evolucion.valorMercado(theta, edad, anios, VALOR)
                : 0.0;
        return new JugadorBase(
                idx[0],
                String.format("J%04d", idx[0]),
                puesto,
                pertenencia,
                edad,
                theta,
                potencial(theta, edad, rng),
                new Contrato(anios, salario(theta), clausula),
                historial);
    }

    /** Instancias de evaluación: mercados de 50, 150 y 400 candidatos. */
    public static List<Instancia> evaluacion() {
        return List.of(
                generar("eval-chica", 25, 50, 101),
                generar("eval-media", 25, 150, 102),
                generar("eval-grande", 25, 400, 103));
    }

    /** Instancias de configuración paramétrica: más chicas y con otra semilla. */
    public static List<Instancia> configuracion() {
        return List.of(
                generar("conf-joven", 25, 30, 201, 78.0, 23.0, 120.0, 1.25),
                generar("conf-veterano", 25, 30, 202, 78.0, 29.5, 60.0, 1.05));
    }
}

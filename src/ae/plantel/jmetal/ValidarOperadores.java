package ae.plantel.jmetal;

import ae.plantel.Aleatorio;
import ae.plantel.Cromosoma;
import ae.plantel.GeneradorInstancias;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Operadores;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Validación de los operadores agregados por Alen (P10, P11, P12).
 *
 * [Informe §III-C: selección proporcional con escalado; escalado de fitness;
 * cruzamiento por bloques posicionales]
 *
 *  1. Escalado (Tema 3): pesos no negativos; con escalado lineal sin recorte, la
 *     media de los pesos es la media truncada y el mejor recibe C_MULT veces la
 *     media; el orden de los individuos se conserva.
 *  2. Ruleta con punteros equiespaciados: cada individuo sale floor o ceil de su
 *     número esperado de copias (propiedad que define a SUS).
 *  3. Cruzamiento por bloques: en cada hijo, todos los genes de un mismo puesto
 *     vienen del mismo padre (salvo los reales, que se promedian cuando los
 *     padres coinciden en la acción).
 *
 * Uso:  java -cp "$CP" ae.plantel.jmetal.ValidarOperadores
 */
public final class ValidarOperadores {

    private static int fallas;

    private ValidarOperadores() {
    }

    private static void chequear(String nombre, boolean ok, String detalle) {
        if (!ok) {
            fallas++;
        }
        System.out.printf("  %-60s %s  %s%n", nombre, ok ? "OK   " : "FALLA", detalle);
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        Aleatorio rng = new Aleatorio(7);

        // ---------------- 1. escalado ----------------
        System.out.println("1. Escalado del fitness (truncamiento sigma + lineal)");
        double[] f = new double[100];
        for (int i = 0; i < f.length; i++) {
            f[i] = 225 + rng.normal(0, 2);
        }
        double[] w = Operadores.escalar(f);
        double minW = Double.POSITIVE_INFINITY, maxW = 0, mediaW = 0;
        int iMax = 0, iMaxF = 0;
        boolean orden = true;
        for (int i = 0; i < w.length; i++) {
            minW = Math.min(minW, w[i]);
            if (w[i] > maxW) {
                maxW = w[i];
                iMax = i;
            }
            if (f[i] > f[iMaxF]) {
                iMaxF = i;
            }
            mediaW += w[i] / w.length;
            for (int j = 0; j < w.length; j++) {
                if (f[i] > f[j] && w[i] < w[j] - 1e-12) {
                    orden = false;
                }
            }
        }
        chequear("pesos no negativos", minW >= 0, String.format("mínimo %.4f", minW));
        chequear("el escalado conserva el orden", orden && iMax == iMaxF, "");
        chequear("el mejor recibe C_MULT veces la media (o el peor queda en 0)",
                Math.abs(maxW / mediaW - Operadores.C_MULT) < 1e-9 || minW < 1e-9,
                String.format("max/media = %.4f, mín = %.4f", maxW / mediaW, minW));
        double[] uniforme = { 3, 3, 3, 3 };
        double[] wu = Operadores.escalar(uniforme);
        chequear("población uniforme: pesos iguales", wu[0] == wu[3] && wu[0] > 0, "");

        // ---------------- 2. SUS ----------------
        System.out.println("2. Ruleta con punteros equiespaciados (SUS)");
        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        List<SolucionPlantel> pob = new ArrayList<>();
        double[] fit = { 230, 228, 226, 224, 222, 220, 218, 216, 214, 212 };
        for (double x : fit) {
            SolucionPlantel s = SolucionPlantel.desde(Cromosoma.statuQuo(inst), 1);
            s.setObjective(0, -x);
            pob.add(s);
        }
        double[] esperado = Operadores.escalar(fit);
        double tot = 0;
        for (double x : esperado) {
            tot += x;
        }
        int n = 40;
        boolean ok = true;
        OperadoresJMetal.ProporcionalEscalada sus = new OperadoresJMetal.ProporcionalEscalada(new Aleatorio(3));
        for (int rep = 0; rep < 50 && ok; rep++) {
            List<SolucionPlantel> sel = sus.seleccionar(pob, n);
            for (int i = 0; i < pob.size(); i++) {
                final SolucionPlantel si = pob.get(i);
                long copias = sel.stream().filter(x -> x == si).count();
                double e = esperado[i] / tot * n;
                if (copias < Math.floor(e - 1e-9) || copias > Math.ceil(e + 1e-9)) {
                    ok = false;
                }
            }
        }
        chequear("cada individuo sale floor o ceil de sus copias esperadas (50 giros)", ok, "");

        // ---------------- 3. bloques ----------------
        System.out.println("3. Cruzamiento por bloques posicionales");
        boolean bloquesOk = true;
        for (int rep = 0; rep < 200 && bloquesOk; rep++) {
            Cromosoma a = Operadores.aleatorioSesgado(inst, rng);
            Cromosoma b = Operadores.aleatorioSesgado(inst, rng);
            Cromosoma[] h = Operadores.cruzarPorBloques(inst, a, b, 1.0, rng);
            for (ae.plantel.Modelo.Puesto pu : ae.plantel.Modelo.Puesto.values()) {
                Boolean dePrimero = null;
                for (int i = 0; i < a.n; i++) {
                    if (inst.jugadores.get(i).puesto() != pu || a.accion[i] == b.accion[i]
                            && a.ventana[i] == b.ventana[i] && a.duracion[i] == b.duracion[i]
                            && a.prioridad[i] == b.prioridad[i]) {
                        continue;   // gen indistinguible: no informa de qué padre vino
                    }
                    boolean igualA = h[0].accion[i] == a.accion[i] && h[0].ventana[i] == a.ventana[i]
                            && h[0].duracion[i] == a.duracion[i] && h[0].prioridad[i] == a.prioridad[i];
                    if (dePrimero == null) {
                        dePrimero = igualA;
                    } else if (dePrimero != igualA) {
                        bloquesOk = false;
                    }
                }
            }
        }
        chequear("200 cruces: cada puesto viene entero de un mismo padre", bloquesOk, "");

        System.out.println(fallas == 0 ? "Operadores OK." : fallas + " FALLAS");
        if (fallas > 0) {
            System.exit(1);
        }
    }
}

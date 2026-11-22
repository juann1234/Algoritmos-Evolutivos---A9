package ae.plantel;

import ae.plantel.Modelo.Accion;
import ae.plantel.Modelo.Instancia;
import ae.plantel.Modelo.Pertenencia;
import java.util.*;

/**
 * Sondas de regresión contra los agujeros del simulador que ya se encontraron
 * y corrigieron. Cada sonda arma un plan "tramposo" a mano y mide si todavía
 * le rinde. Si alguna vuelve a dar ganancia sin mejorar los puntos de liga, se
 * reabrió un agujero.
 *
 * Valores esperados con el modelo actual (instancia demo, N=200):
 *   S1  primer sorteo en semillas consecutivas: rango amplio, aprox. [0.00, 1.00]
 *   S2  renovar a todos en V6: f aprox. -0.24 respecto del statu quo
 *       (préstamos/compras en V6 suben, pero porque suben los puntos de T3)
 *   S3  liberar a los 6 peores: f aprox. -11.34 y menos puntos
 *   S4  ceder (-1.07) mejor que vender (-5.46) y que liberar (-7.65)
 *   S5  correlación de escenarios entre planes: >= 0.99
 *   S6  penalización por puestos del statu quo: aprox. 1.10
 *   S7  cláusula baja no es explotable (Alen, validación 22/11):
 *       renovar a los propios de nivel >= 78 en V1 con cláusula 1x: f aprox. -12.80
 *       (pierde unos 3 jugadores por cláusula y puntos en T2-T3); con 3x/10x: aprox. -0.5/-0.3
 *   S8  cupo lleno: comprar 3 candidatos en V1 sin vender no concreta nada (f = statu quo);
 *       vender 3 y comprar 3 en la misma ventana sí: f aprox. +6.32
 *   S9  vender a los mayores de 29 en V1: f aprox. -11.94 (la caja sube, los puntos bajan)
 *
 * [Informe §I-B, §III-A y §III-B] Cada sonda verifica que el fitness solo sube
 * cuando suben los puntos de liga (la utilidad deportiva de la ec. 2).
 *
 * Uso:  java -cp out ae.plantel.ValidarAgujeros
 */
public final class ValidarAgujeros {
    static final Parametros P = new Parametros();
    static final int N = 200; static final long S = 777_000L;

    static Evaluador.Fitness f(Instancia inst, Cromosoma c) { return Evaluador.evaluar(inst, c, N, S, P); }

    static double[] puntos(Instancia inst, Cromosoma c) {
        double[] p = new double[3];
        for (int k = 0; k < N; k++) {
            Evaluador.Resultado r = Evaluador.simular(inst, c, new Escenario(S + k), P);
            for (int t = 0; t < 3; t++) p[t] += r.puntos[t] / N;
        }
        return p;
    }

    public static void main(String[] a) {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, java.nio.charset.StandardCharsets.UTF_8));
        Instancia inst = GeneradorInstancias.generar("demo", 25, 50, 101);
        int n = inst.jugadores.size();

        System.out.println("== S1. Primer uniforme con semillas consecutivas (generador nuevo)");
        StringBuilder sb = new StringBuilder("  ");
        for (int k = 0; k < 8; k++) sb.append(String.format("%.4f ", new Aleatorio(1_000_003L + k).uniforme()));
        System.out.println(sb);
        double[] u1 = new double[200];
        for (int k = 0; k < 200; k++) u1[k] = new Aleatorio(S + k).uniforme();
        System.out.printf("  rango en 200 semillas consecutivas: [%.4f, %.4f]%n",
            Arrays.stream(u1).min().getAsDouble(), Arrays.stream(u1).max().getAsDouble());

        Cromosoma sq = Cromosoma.statuQuo(inst);
        Evaluador.Fitness fsq = f(inst, sq);
        double[] psq = puntos(inst, sq);
        System.out.println("\n== Referencia statu quo: " + fsq);
        System.out.printf("   puntos T1..T3: %.1f %.1f %.1f%n", psq[0], psq[1], psq[2]);

        System.out.println("\n== S2. Operaciones en V6 (última ventana, antes de la segunda mitad de T3)");
        Cromosoma c = Cromosoma.statuQuo(inst);
        for (int i = 0; i < n; i++) if (inst.jugadores.get(i).pertenencia() == Pertenencia.MERCADO) {
            c.accion[i] = 2; c.ventana[i] = 6; c.salario[i] = 1.6; }
        report("todos los candidatos a PRÉSTAMO en V6", inst, c, fsq, psq);

        c = Cromosoma.statuQuo(inst);
        for (int i = 0; i < n; i++) if (inst.jugadores.get(i).pertenencia() == Pertenencia.PROPIO) {
            c.accion[i] = Accion.RENOVAR.ordinal(); c.ventana[i] = 6; c.duracion[i] = 5; c.salario[i] = 1.6; }
        report("todos los propios RENOVAR en V6 por 5 años", inst, c, fsq, psq);

        c = Cromosoma.statuQuo(inst);
        for (int i = 0; i < n; i++) if (inst.jugadores.get(i).pertenencia() == Pertenencia.MERCADO) {
            c.accion[i] = 1; c.ventana[i] = 6; c.duracion[i] = 5; c.salario[i] = 1.6; }
        report("todos los candidatos COMPRAR en V6 por 5 años", inst, c, fsq, psq);

        System.out.println("\n== S3. LIBERAR en V1 a los k propios de menor nivel");
        List<Integer> propios = new ArrayList<>();
        for (int i = 0; i < n; i++) if (inst.jugadores.get(i).pertenencia() == Pertenencia.PROPIO) propios.add(i);
        propios.sort(Comparator.comparingDouble(i -> inst.jugadores.get(i).thetaInicial()));
        for (int k : new int[]{2, 4, 6}) {
            c = Cromosoma.statuQuo(inst);
            for (int q = 0; q < k; q++) { int i = propios.get(q); c.accion[i] = Accion.LIBERAR.ordinal(); c.ventana[i] = 1; }
            report("liberar los " + k + " peores", inst, c, fsq, psq);
        }

        System.out.println("\n== S4. CEDER contra VENDER y LIBERAR (mismo jugador, V1)");
        int medio = propios.get(propios.size() / 2);
        for (Accion ac : new Accion[]{Accion.CEDER, Accion.VENDER, Accion.LIBERAR}) {
            c = Cromosoma.statuQuo(inst);
            c.accion[medio] = ac.ordinal(); c.ventana[medio] = 1;
            report(ac + " a " + inst.jugadores.get(medio).nombre(), inst, c, fsq, psq);
        }

        System.out.println("\n== S5. Desincronización de common random numbers");
        // Plan B = statu quo + UNA operación en V1. Bajo CRN bien hecho, los
        // puntos de T1 deberían ser casi idénticos escenario por escenario.
        Cromosoma b = Cromosoma.statuQuo(inst);
        int peor = propios.get(0);
        b.accion[peor] = Accion.VENDER.ordinal(); b.ventana[peor] = 1;
        Cromosoma d = Cromosoma.statuQuo(inst);
        d.prioridad[peor] = 0;   // cambio que no consume sorteos
        System.out.printf("  corr(U) statu quo vs +1 venta en V1:        %.3f%n", corr(inst, sq, b));
        System.out.printf("  corr(U) statu quo vs cambio de prioridad:   %.3f%n", corr(inst, sq, d));
        Cromosoma e2 = Cromosoma.statuQuo(inst);
        for (int i = 0; i < n; i++) if (inst.jugadores.get(i).pertenencia() == Pertenencia.MERCADO) { e2.accion[i] = 1; e2.ventana[i] = 1; e2.duracion[i] = 4; e2.salario[i] = 1.3; break; }
        System.out.printf("  corr(U) statu quo vs +1 compra en V1:       %.3f%n", corr(inst, sq, e2));

        System.out.println("\n== S6. Penalización por puestos en el statu quo");
        double pen = 0; int nf = 0;
        for (int k = 0; k < N; k++) { Evaluador.Resultado r = Evaluador.simular(inst, sq, new Escenario(S + k), P); pen += r.penalizacion / N; }
        System.out.printf("  penalización media del statu quo: %.2f puntos de utilidad%n", pen);

        sondasNuevas(inst, fsq, psq);
    }

    /** S7-S9: sondas agregadas en la validación de Alen (docs/VALIDACION.md §3). */
    static void sondasNuevas(Instancia inst, Evaluador.Fitness fsq, double[] psq) {
        int n = inst.jugadores.size();
        System.out.println("\n== S7. Cláusula de rescisión baja contra alta (renovar nivel >= 78 en V1)");
        for (double cl : new double[]{1.0, 3.0, 10.0}) {
            Cromosoma c = Cromosoma.statuQuo(inst);
            for (int i = 0; i < n; i++) {
                Modelo.JugadorBase j = inst.jugadores.get(i);
                if (j.pertenencia() == Pertenencia.PROPIO && j.thetaInicial() >= 78) {
                    c.accion[i] = Accion.RENOVAR.ordinal(); c.ventana[i] = 1; c.duracion[i] = 4;
                    c.salario[i] = 1.3; c.clausula[i] = cl;
                }
            }
            report("cláusula " + cl + "x", inst, c, fsq, psq);
        }

        System.out.println("\n== S8. Cupo de fichas lleno: comprar exige vender");
        List<Integer> mk = new ArrayList<>(), pr = new ArrayList<>();
        for (int i = 0; i < n; i++) (inst.jugadores.get(i).pertenencia() == Pertenencia.MERCADO ? mk : pr).add(i);
        mk.sort(Comparator.comparingDouble(i -> -inst.jugadores.get(i).thetaInicial()));
        pr.sort(Comparator.comparingDouble(i -> inst.jugadores.get(i).thetaInicial()));
        Cromosoma c = Cromosoma.statuQuo(inst);
        for (int q = 0; q < 3; q++) { int i = mk.get(q + 2); c.accion[i] = 1; c.ventana[i] = 1; c.duracion[i] = 4; c.salario[i] = 1.3; c.clausula[i] = 4; }
        report("comprar 3 sin vender", inst, c, fsq, psq);
        for (int q = 0; q < 3; q++) { int i = pr.get(q); c.accion[i] = Accion.VENDER.ordinal(); c.ventana[i] = 1; }
        report("vender 3 peores y comprar 3", inst, c, fsq, psq);

        System.out.println("\n== S9. Vender veteranos para hacer caja");
        c = Cromosoma.statuQuo(inst);
        int k = 0;
        for (int i : pr) if (inst.jugadores.get(i).edad() >= 29) { c.accion[i] = Accion.VENDER.ordinal(); c.ventana[i] = 1; k++; }
        report("vender los " + k + " mayores de 29 en V1", inst, c, fsq, psq);
    }

    static void report(String et, Instancia inst, Cromosoma c, Evaluador.Fitness fsq, double[] psq) {
        Evaluador.Fitness x = f(inst, c); double[] p = puntos(inst, c);
        System.out.printf("  %-44s f=%7.2f (%+6.2f)  pts %.1f %.1f %.1f  ops=%.1f%n",
            et, x.valor, x.valor - fsq.valor, p[0], p[1], p[2], x.concretadasMedia);
    }

    static double corr(Instancia inst, Cromosoma x, Cromosoma y) {
        double[] a = new double[N], b = new double[N];
        for (int k = 0; k < N; k++) {
            a[k] = Evaluador.simular(inst, x, new Escenario(S + k), P).utilidad;
            b[k] = Evaluador.simular(inst, y, new Escenario(S + k), P).utilidad;
        }
        double ma = Arrays.stream(a).average().getAsDouble(), mb = Arrays.stream(b).average().getAsDouble();
        double sab = 0, sa = 0, sb = 0;
        for (int k = 0; k < N; k++) { sab += (a[k]-ma)*(b[k]-mb); sa += (a[k]-ma)*(a[k]-ma); sb += (b[k]-mb)*(b[k]-mb); }
        return sab / Math.sqrt(sa * sb);
    }
}

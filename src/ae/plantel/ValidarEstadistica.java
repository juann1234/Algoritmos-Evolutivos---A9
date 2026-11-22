package ae.plantel;

/**
 * Valida los tests estadísticos implementados a mano contra valores de
 *
 * [Informe §IV-A/§IV-B: validación de los tests contra scipy]
 * referencia calculados con scipy.stats sobre los mismos datos. Si alguna
 * línea dice FALLA, hay un error en Estadistica.java.
 *
 * Uso:  java -cp out ae.plantel.ValidarEstadistica
 */
public final class ValidarEstadistica {
    private static int fallas = 0;

    private static void chequear(String nombre, double obtenido, double esperado) {
        boolean ok = Math.abs(obtenido - esperado) < 1e-5;
        if (!ok) {
            fallas++;
        }
        System.out.printf("  %-20s obtenido %.6f  scipy %.6f  %s%n", nombre, obtenido, esperado,
                ok ? "OK" : "FALLA");
    }

    public static void main(String[] a){
  double[][] g = {{1,2,2,3,5,6,7,7},{2,3,4,4,6,8,9,9},{5,5,6,7,8,9,10,12}};
  double[] kw = Estadistica.kruskalWallis(g);
  double[] an = Estadistica.anova(g);
  double[][] bl = {{3,2,1},{2,2,1},{3,1,2},{3,2,2},{1,2,3},{3,3,1},{2,1,1},{3,2,1}};
  double[] fr = Estadistica.friedman(bl);
  double[] x = {1.2,2.3,3.1,4.8,5.0,6.2,7.7,8.1,9.0,10.5,11.1,12.4,13.3,14.6,15.2,16.0,17.9,18.3,19.4,20.2,21.0,22.7};
  double[] y = {1.0,2.5,2.9,4.1,5.3,5.9,7.1,8.6,8.7,10.0,11.5,11.8,13.0,14.1,15.9,15.5,17.0,18.6,19.1,19.9,20.1,22.0};
  double[] w = Estadistica.wilcoxon(x,y);
  double[] norm = new double[40]; for(int i=0;i<40;i++) norm[i]=Math.sin(i*1.7)*3+Math.cos(i*0.3);
  chequear("Kruskal-Wallis H", kw[0], 5.872475);
  chequear("Kruskal-Wallis p", kw[1], 0.053065);
  chequear("ANOVA F", an[0], 4.031646);
  chequear("ANOVA p", an[1], 0.032975);
  chequear("Friedman chi2", fr[0], 6.0);
  chequear("Friedman p", fr[1], 0.049787);
  chequear("Wilcoxon p", w[1], 0.033194);
  chequear("Lilliefors D", Estadistica.estadisticoD(norm), 0.105558);

  // --- Agregados por Alen (DECISIONES D-A5). Valores de scipy 1.18.1. ---
  double[] sesg = new double[25]; for (int i = 0; i < 25; i++) sesg[i] = Math.exp((0.15 * i) % 3.0) + (i % 7) * 0.4;
  double[] chico = {2.1, 3.4, 1.9, 5.6, 4.4, 3.3, 2.8};
  double[] tres = {1.0, 2.0, 4.0};
  double[] sw40 = Estadistica.shapiroWilk(norm), sw25 = Estadistica.shapiroWilk(sesg);
  double[] sw7 = Estadistica.shapiroWilk(chico), sw3 = Estadistica.shapiroWilk(tres);
  chequear("Shapiro W (n=40)", sw40[0], 0.956356);
  chequear("Shapiro p (n=40)", sw40[1], 0.125575);
  chequear("Shapiro W (n=25)", sw25[0], 0.854641);
  chequear("Shapiro p (n=25)", sw25[1], 0.002160);
  chequear("Shapiro W (n=7)", sw7[0], 0.940137);
  chequear("Shapiro p (n=7)", sw7[1], 0.639951);
  chequear("Shapiro W (n=3)", sw3[0], 0.964286);
  chequear("Shapiro p (n=3)", sw3[1], 0.636887);
  double[] ma = {230.1,231.5,229.8,232.0,228.7,231.1,230.6,229.9,232.4,231.8,230.2,229.5};
  double[] mb = {228.0,229.1,227.5,230.2,228.8,226.9,229.4,228.1,227.7,229.9};
  double[] mw = Estadistica.mannWhitney(ma, mb);
  chequear("Mann-Whitney U", mw[0], 108.0);
  chequear("Mann-Whitney p", mw[2], 0.001725);
  double[] mwe = Estadistica.mannWhitney(new double[]{1,2,2,3,3,3,4,5}, new double[]{3,4,4,5,5,6,7,7,8});
  chequear("Mann-Whitney U emp.", mwe[0], 7.5);
  chequear("Mann-Whitney p emp.", mwe[2], 0.006373);
  double[] tw = Estadistica.tStudent(ma, mb);
  chequear("t de Welch t", tw[0], 4.369754);
  chequear("t de Welch gl", tw[1], 19.528395);
  chequear("t de Welch p", tw[2], 0.000311);
  double[] wx = {233.28,231.81,230.70,229.97,231.43,231.28,227.42,229.39,231.84,231.01};
  double[] wy = {229.71,228.41,228.41,227.20,226.72,225.98,227.27,228.50,230.31,227.98};
  chequear("Wilcoxon exacto n=10", Estadistica.wilcoxon(wx, wy)[1], 0.001953);
  double[] w14 = {1.1,2.3,0.4,-0.7,1.9,0.8,-0.2,1.5,2.7,-1.3,0.6,3.1,1.2,0.9};
  chequear("Wilcoxon exacto vs 0", Estadistica.wilcoxon(w14, new double[14])[1], 0.013428);
  double[] h = Estadistica.holm(new double[]{0.01, 0.04, 0.03, 0.005, 0.2});
  chequear("Holm p1", h[0], 0.04); chequear("Holm p2", h[1], 0.09); chequear("Holm p3", h[2], 0.09);
  chequear("Holm p4", h[3], 0.025); chequear("Holm p5", h[4], 0.2);
  chequear("cuantil normal 0.975", Estadistica.cuantilNormal(0.975), 1.959964);
  chequear("Phi preciso(-3.5)", Estadistica.phiPreciso(-3.5) * 1e4, 2.326291);

  System.out.println(fallas == 0 ? "Todos los tests coinciden con scipy." : fallas + " FALLAS");
  if (fallas > 0) {
      System.exit(1);
  }
}}

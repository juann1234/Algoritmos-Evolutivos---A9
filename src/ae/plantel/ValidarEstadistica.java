package ae.plantel;

/**
 * Valida los tests estadísticos implementados a mano contra valores de
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
  System.out.println(fallas == 0 ? "Todos los tests coinciden con scipy." : fallas + " FALLAS");
  if (fallas > 0) {
      System.exit(1);
  }
}}

package io.github.rocsg.segmentation.test;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

public class TransformClimateStripes {


    public static void main(String[] args) {
        // 1) Histogramme des couleurs
        /*Map<Color, Long> hist = listRGBColors("/home/rfernandez/Bureau/A_Test/ARIZE/final_withreduction.tif");
        System.out.println("Couleurs uniques : " + hist.size());
        for (Map.Entry<Color, Long> e : hist.entrySet()) {
            Color c = e.getKey();
            Long count = e.getValue();
            System.out.printf("Color(r=%3d,g=%3d,b=%3d) : %d pixels%n", c.getRed(), c.getGreen(), c.getBlue(), count);
        }*/

        Map<Color, Color> map = new LinkedHashMap<>();
        map.put(new Color(8,48,107), new Color(0, 61, 75));  // exemple
        map.put(new Color(8,81,156), new Color(0, 90, 86));  // exemple
        map.put(new Color(33,113,181), new Color(0, 115, 80));  // exemple
        map.put(new Color(66,146,198), new Color(0, 119, 60));  // exemple

        map.put(new Color(107,174,214), new Color(37, 137, 42));  // vert vert


        map.put(new Color(156,202,225), new Color(77, 155, 24));  // exemple
        map.put(new Color(198,219,239), new Color(155, 187, 20));  // exemple

        map.put(new Color(222,235,247), new Color(203, 191, 29));  //jaune vert




        map.put(new Color(254,224,210), new Color(252, 197, 37));  // exemple

        map.put(new Color(252,146,114), new Color(250, 181, 30));  //pour le rose

        map.put(new Color(252,187,161 ), new Color(250, 168, 23));  // exemple

        map.put(new Color(251,106,074), new Color(250, 117, 18));  // exemple
        map.put(new Color(203,24,229), new Color(236, 170, 12));  // exemple
        map.put(new Color(165,15,21), new Color(165,15,21));  // marron
        // 2) Remapping de couleurs avec tolérance

        remapColors("/home/rfernandez/Bureau/A_Test/ARIZE/final_withreduction.tif", "/home/rfernandez/Bureau/A_Test/ARIZE/final_withreduction_remap.tif", map, 15); 
    }

    /**
     * Ouvre une image avec ImageJ et renvoie l’histogramme des teintes RGB observées.
     * @param inputPath chemin du fichier image
     * @return Map<Color, Long> : chaque couleur et son nombre de pixels
     */
    public static Map<Color, Long> listRGBColors(String inputPath) {
        ImagePlus imp = IJ.openImage(inputPath);
        if (imp == null) throw new IllegalArgumentException("Impossible d’ouvrir : " + inputPath);

        // Assure un processeur couleur
        ImageProcessor ip = imp.getProcessor();
        ColorProcessor cp = (ip instanceof ColorProcessor) ? (ColorProcessor) ip : ip.convertToColorProcessor();

        int w = cp.getWidth(), h = cp.getHeight();
        Map<Color, Long> hist = new LinkedHashMap<>();

        // Lecture directe des pixels (ARGB int)
        int[] pixels = (int[]) cp.getPixels();
        for (int p : pixels) {
            // ColorProcessor stocke généralement en int 0xFFRRGGBB
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            Color c = new Color(r, g, b);
            hist.merge(c, 1L, Long::sum);
        }
        return hist;
    }

    /**
     * Remappe des couleurs d'une image vers d'autres couleurs (avec tolérance RGB).
     * @param inputPath  chemin de l’image source
     * @param outputPath chemin du fichier de sortie (format géré par ImageJ)
     * @param mapping    correspondance des couleurs : source -> cible (plusieurs sources peuvent pointer vers la même cible)
     * @param tolerance  tolérance en distance euclidienne RGB (0 = correspondance exacte)
     */
    public static void remapColors(String inputPath,
                                   String outputPath,
                                   Map<Color, Color> mapping,
                                   int tolerance) {
        ImagePlus imp = IJ.openImage(inputPath);
        if (imp == null) throw new IllegalArgumentException("Impossible d’ouvrir : " + inputPath);

        ImageProcessor ip = imp.getProcessor();
        ColorProcessor cp = (ip instanceof ColorProcessor) ? (ColorProcessor) ip : ip.convertToColorProcessor();

        int[] pixels = (int[]) cp.getPixels();
        // Pré-calcul des sources (r,g,b) en tableau pour rapidité
        int m = mapping.size();
        int[] sr = new int[m], sg = new int[m], sb = new int[m];
        int[] tr = new int[m], tg = new int[m], tb = new int[m];
        int idx = 0;
        for (Map.Entry<Color, Color> e : mapping.entrySet()) {
            Color s = e.getKey();
            Color t = e.getValue();
            sr[idx] = s.getRed();   sg[idx] = s.getGreen();   sb[idx] = s.getBlue();
            tr[idx] = t.getRed();   tg[idx] = t.getGreen();   tb[idx] = t.getBlue();
            idx++;
        }
        int tol2 = tolerance * tolerance;

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;

            int bestJ = -1;
            int bestD2 = Integer.MAX_VALUE;

            for (int j = 0; j < m; j++) {
                int dr = r - sr[j], dg = g - sg[j], db = b - sb[j];
                int d2 = dr*dr + dg*dg + db*db;
                if (d2 <= tol2 && d2 < bestD2) { bestD2 = d2; bestJ = j; }
            }

            if (bestJ >= 0) {
                int newRGB = (0xFF << 24) | (tr[bestJ] << 16) | (tg[bestJ] << 8) | tb[bestJ];
                pixels[i] = newRGB;
            }
        }

        imp.setProcessor(cp);
        IJ.save(imp, outputPath);
    }

    // ---
}

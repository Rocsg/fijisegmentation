package io.github.rocsg.segmentation.jeantrap;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ReconstructionTimeseries {
    private static final String BASE_PATH = "/Donnees/Jean_Trap/Data";
    private static final String OUT_PATH = "/Donnees/Jean_Trap/ReconstructedTimeSeries";
    private static final String[] DAYS = {"Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi"};

    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        new File(OUT_PATH).mkdirs();

        // Vérifie le nombre d'images dans chaque jour
        int[]nImagesTab=new int[]{0, 0, 0, 0, 0};
        int[]nImagesTabStart=new int[]{0, 0, 0, 0, 0};
        int index=-1;
        int indexTot=0;
        int nImages = -1;
        for (String day : DAYS) {
            index++;
            File dir = new File(BASE_PATH, day);
            String[] files = dir.list((d, name) -> name.toLowerCase().endsWith(".tif"));
            if (files == null || files.length == 0) {
                System.err.println("Aucune image trouvée dans " + dir);
                return;
            }
            Arrays.sort(files, (a,b) -> Integer.compare(
                Integer.parseInt(a.replace(".TIF", "").replace(".tif", "")), 
                Integer.parseInt(b.replace(".TIF", "").replace(".tif", ""))
            ));
            nImagesTab[index] = files.length;
            if (nImages == -1) nImages = files.length;
            else if (nImages != files.length) {
                System.err.println("Nombre d'images inégal dans " + dir);
            }
            indexTot=indexTot+nImagesTab[index];
            if(!day.equals("Vendredi"))nImagesTabStart[index+1]=indexTot;
        }

        // Parcours chaque index d'image
        for (int i = 1; i <= nImages; i++) {
            ImageStack stack = null;
            String outName = String.format("Boite_%02d_timeseries.tif", i);

            for (int d = 0; d < DAYS.length; d++) {
                String day = DAYS[d];
                String imgName = (i+nImagesTabStart[d]) + ".TIF";
                File file = new File(BASE_PATH + "/" + day + "/" + imgName);
                if (!file.exists()) {
                    // Tente en .tif
                    imgName = i + ".tif";
                    file = new File(BASE_PATH + "/" + day + "/" + imgName);
                }
                if (!file.exists()) {
                    System.err.println("Image manquante: " + file);
                    continue;
                }
                ImagePlus imp = new Opener().openImage(file.getAbsolutePath());
                if (imp == null) {
                    System.err.println("Erreur ouverture: " + file);
                    continue;
                }
                if (stack == null) stack = new ImageStack(imp.getWidth(), imp.getHeight());
                stack.addSlice(day, imp.getProcessor().duplicate());
            }
            if (stack != null && stack.getSize() == DAYS.length) {
                ImagePlus ts = new ImagePlus("Timeseries_" + i, stack);
                new FileSaver(ts).saveAsTiffStack(OUT_PATH + "/" + outName);
                System.out.println("Saved: " + OUT_PATH + "/" + outName);
            } else {
                System.err.println("Stack incomplète pour " + outName);
            }
        }
        System.out.println("Séries temporelles reconstruites.");
    }
}

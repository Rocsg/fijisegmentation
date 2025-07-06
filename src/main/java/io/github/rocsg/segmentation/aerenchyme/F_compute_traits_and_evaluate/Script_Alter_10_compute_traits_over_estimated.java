package io.github.rocsg.segmentation.aerenchyme.F_compute_traits_and_evaluate;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Script_Alter_10.java
 *
 * Pour chaque CodeObj dans les dossiers de segmentation :
 *   - lit toutes les tranches de lacuna et de gaine
 *   - mesure la surface (nombre de pixels blancs) par tranche
 *   - affiche deux lignes : série des surfaces lacuna, série des surfaces gaine
 *   - calcule mean, median, std, min, max pour chaque série
 *   - calcule ratio = median_lacuna / median_gaine
 *   - écrit une ligne dans un CSV récapitulatif
 */
public class Script_Alter_10_compute_traits_over_estimated {
    private static final String BASE_PATH       = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String GAINE_DIR       = BASE_PATH + "/slice_stacks_segmented_tissue_ALT7_single/raw";
    private static final String LACUNA_DIR      = BASE_PATH + "/slice_stacks_segmented_tissue_ALT9_lacuna_cleaned/raw";
    private static final String OUT_CSV         = BASE_PATH + "/stats_lacuna_gaine.csv";
    private static final String CORRESPONDANCES = BASE_PATH + "/correspondance_labels_hani_romain.csv";

    public static void main(String[] args) {
        IJ.log("=== Script_Alter_10: computing stats lacuna vs gaine ===");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUT_CSV))) {
            // header
            writer.write("Cube;Code;CodeSpec;Ratio_of_median;Median_of_ratio;mean_lacuna;median_lacuna;std_lacuna;min_lacuna;max_lacuna;"
                         +"mean_gaine;median_gaine;std_gaine;min_gaine;max_gaine\n");

            // read correspondances
            List<String[]> correspondances = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(CORRESPONDANCES))) {
                String line;
                //Read header
                br.readLine();
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",");
                    correspondances.add(parts);
                }
            }
            File gaineBase = new File(GAINE_DIR);
            for (File cubeDir : gaineBase.listFiles(File::isDirectory)) {
                String cube = cubeDir.getName();
                File lacunaCube = new File(LACUNA_DIR, cube);
                for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                    String code = objDir.getName();

                    //Get the equivalent code
                    String equivalentCode = null; 
                    for(String[] correspondance : correspondances){
                        if(correspondance[2].equals(code)){
                            equivalentCode = correspondance[1];
                            break;
                        }
                    }
                    //correspondancesMap.get(code);
                    if (equivalentCode == null) {
                        IJ.showMessage("Did not get the equivalent to "+code);
                        continue;
                    }
                    else{
                        //System.out.println("For code "+code+" equivalent is "+equivalentCode);
                    }


                    //IJ.log("Processing stats for " + cube + " / " + code);
                    File lacunaObjDir = new File(lacunaCube, code);

                    // gather per-slice areas
                    List<Integer> areasL = new ArrayList<>();
                    List<Integer> areasG = new ArrayList<>();
                    List<Double> ratios = new ArrayList<>();

                    for (File sliceFile : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
//                        System.out.println(sliceFile);
                        // gaine
                        ImagePlus impG = IJ.openImage(sliceFile.getAbsolutePath());
                        ByteProcessor bpG = (ByteProcessor)impG.getProcessor();
                        int countG = countWhite(bpG);
                        areasG.add(countG);
                        // lacuna
                        File lacunaFile = new File(lacunaObjDir, sliceFile.getName());
                        ImagePlus impL = IJ.openImage(lacunaFile.getAbsolutePath());
                        ByteProcessor bpL = (ByteProcessor)impL.getProcessor();
                        int countL = countWhite(bpL);
                        areasL.add(countL);
                        ratios.add(countL*1.0/(Math.max(1,countG)));
                    }

                    // print series
                    //System.out.println("Lacuna series for " + code + ": " + areasL);
                    System.out.println("Gaine series for " + code + ": " + areasG);

                    // compute stats
                    double meanL = mean(areasL);
                    int medL  = (int) median(areasL);
                    double stdL  = std(areasL, meanL);
                    int minL     = Collections.min(areasL);
                    int maxL     = Collections.max(areasL);

                    double meanG = mean(areasG);
                    int medG  = (int) median(areasG);
                    double stdG  = std(areasG, meanG);
                    int minG     = Collections.min(areasG);
                    int maxG     = Collections.max(areasG);

                    double ratio = medL *1.0 / medG;
                    double medratio= medianD(ratios);
                    // write CSV line
                    writer.write(String.format("%s ; %s ; %s ; %.4f ; %.4f ; %.2f ; %d ; %.2f ; %d ; %d ; %.2f ; %d ; %.2f ; %d ; %d\n",
                                               cube, code, equivalentCode,ratio,medratio,meanL,        medL,         stdL,           minL,      maxL,         meanG,    medG,        stdG,    minG,     maxG,      ratio  ));
                  //             writer.write("Cube, Code, mean_lacuna,  median_lacuna,std_lacuna,    min_lacuna,max_lacuna, mean_gaine, median_gaine,std_gaine,min_gaine,max_gaine, ratio_median\n");

                }
            }
            IJ.log("=== Stats CSV saved to " + OUT_CSV + " ===");
        } catch (IOException e) {
            IJ.error("Error writing CSV: " + e.getMessage());
        }
    }

    private static int countWhite(ByteProcessor bp) {
        int w = bp.getWidth(), h = bp.getHeight(), cnt = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bp.get(x,y) > 0) cnt++;
            }
        }
        return cnt;
    }

    private static double mean(List<Integer> vals) {
        double sum=0;
        for (int v: vals) sum += v;
        return sum/vals.size();
    }

    private static double median(List<Integer> vals) {
        Collections.sort(vals);
        int n = vals.size();
        if (n%2==1) return vals.get(n/2);
        else return (vals.get(n/2-1) + vals.get(n/2)) / 2.0;
    }


    private static double medianD(List<Double> vals) {
        Collections.sort(vals);
        int n = vals.size();
        if (n%2==1) return vals.get(n/2);
        else return (vals.get(n/2-1) + vals.get(n/2)) / 2.0;
    }

    private static double std(List<Integer> vals, double mean) {
        double sum=0;
        for (int v: vals) sum += (v-mean)*(v-mean);
        return Math.sqrt(sum/vals.size());
    }
}

package io.github.rocsg.segmentation.aerenchyme.F_compute_traits_and_evaluate;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.FolderOpener;
import ij.process.ByteProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Script_Alter_Eval.java
 *
 * Calcule et compare les statistiques de surface (mean, median, std, min, max)
 * et le ratio médiane lacuna / médiane gaine pour :
 *  - les annotations (prise uniquement des fichiers *_rot000.tif dans les dossiers)
 *  - les inférences (prise des stacks dans exemples_128_*_aug/raw)
 *
 * Écrit un CSV récapitulatif (séparateur ;) à BASE_PATH + "/eval_annotations_vs_inference.csv".
 */
public class Script_Alter_Eval {
    private static final String BASE_PATH         = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String INFER_GAINE_DIR   = BASE_PATH + "/slice_stacks_segmented_tissue_ALT6_closure/raw";
    private static final String INFER_LACUNA_DIR  = BASE_PATH + "/slice_stacks_segmented_tissue_ALT9_lacuna_cleaned/raw";
    private static final String ANNOT_GAINE_DIR   = BASE_PATH + "/exemples_128_gaine_aug/";
    private static final String ANNOT_LACUNA_DIR  = BASE_PATH + "/exemples_128_lacuna_aug/";
    private static final String OUT_CSV           = BASE_PATH + "/eval_annotations_vs_inference.csv";

    public static void main(String[] args) {
        ImageJ ij=new ImageJ();
        IJ.log("=== Script_Alter_Eval: Stats annotations vs inference ===");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUT_CSV))) {
            // Header with semicolon separators
            writer.write("Code;MEDLAC_INF;MEDLAC_ANNOT;MEDGAIN_INF;MEDGAIN_ANNOT;RATIO_INF;RATIO_ANNOT;");
            writer.write("meanL_inf;meanL_annot;meanG_inf;meanG_annot;");
            writer.write("stdL_inf;stdL_annot;stdG_inf;stdG_annot;");
            writer.write("minL_inf;minL_annot;minG_inf;minG_annot;");
            writer.write("maxL_inf;maxL_annot;maxG_inf;maxG_annot;POUET");
            writer.newLine();

            File gaineFolder = new File(ANNOT_GAINE_DIR);
            for (File fi : gaineFolder.listFiles(File::isDirectory)) {
                File[] annGFiles = fi.listFiles((d,n)->n.endsWith("_rot000.tif"));
                if (annGFiles == null) {
                    IJ.error("Aucun fichier annoté trouvé dans " + ANNOT_GAINE_DIR);
                    return;
                }
                for (File annGFile : annGFiles) {
                    String fname = annGFile.getName();
                    String code = fname.replaceFirst("_rot000\\.tif$", "");
                    IJ.log("Processing Code: " + code);
                    File annLFile = new File(ANNOT_LACUNA_DIR+"/"+fi.getName(), code + "_rot000.tif");
                    File infGFile = new File(INFER_GAINE_DIR+"/"+fi.getName(), code);
                    File infLFile = new File(INFER_LACUNA_DIR+"/"+fi.getName(), code);
                     if (!infGFile.exists()) {
                        IJ.log("  Missing infGFile files for " + code + ", skipping.");
                        continue;
                    }
                    if (!infLFile.exists()) {
                        IJ.log("  Missing infLFile files for " + code + ", skipping.");
                        continue;
                    }
                    if (!annLFile.exists() || !infGFile.exists() || !infLFile.exists()) {
                        IJ.log("  Missing files for " + code + ", skipping.");
                        continue;
                    }
                    // load stacks

                    ImagePlus ipAG = IJ.openImage(annGFile.getAbsolutePath());
                    ImagePlus ipAL = IJ.openImage(annLFile.getAbsolutePath());
                    ipAG=VitimageUtils.thresholdByteImage(ipAG, 2, 255);
                    ipAL=VitimageUtils.thresholdByteImage(ipAL, 2, 255);
                    FolderOpener opener = new FolderOpener();
                    ImagePlus ipIG = opener.open(infGFile.getAbsolutePath());
                    ImagePlus ipIL = opener.open(infLFile.getAbsolutePath());

/* 
                    ipAG.setTitle("ipAG");
                    ipAL.setTitle("ipAL");
                    ipIG.setTitle("ipIG");
                    ipIL.setTitle("ipIL");
                    ipAG.show();
                    ipAL.show();
                    ipIG.show();
                    ipIL.show();*/
                    // compute areas per slice
                    List<Integer> areasL_annot = collectAreas(ipAL);
                    List<Integer> areasG_annot = collectAreas(ipAG);
                    List<Integer> areasL_inf   = collectAreas(ipIL);
                    List<Integer> areasG_inf   = collectAreas(ipIG);
                    // compute stats
                    Stats sL_annot = computeStats(areasL_annot);
                    Stats sG_annot = computeStats(areasG_annot);
                    double ratio_annot = sL_annot.median / sG_annot.median;
                    if(sG_annot.median<0.000001)ratio_annot=0;
                    Stats sL_inf = computeStats(areasL_inf);
                    Stats sG_inf = computeStats(areasG_inf);
                    double ratio_inf = sL_inf.median / sG_inf.median;
                    if(sG_inf.median<0.000001)ratio_inf=0;
                    if(sL_annot.median<100)continue;
                    if(sG_annot.median<500)continue;
                    // write line with semicolons
                    writer.write(String.format("%s;%.2f;%.2f;%.2f;%.2f;%.4f;%.4f;", // Code;MEDLAC_INF;MEDLAC_ANNOT;MEDGAIN_INF;MEDGAIN_ANNOT;RATIO_INF;RATIO_ANNOT
                    code,
                    sL_inf.median, sL_annot.median,
                    sG_inf.median, sG_annot.median,
                    ratio_inf, ratio_annot
                    ));
                    // then meanL, meanG
                     writer.write(String.format("%.2f;%.2f;%.2f;%.2f;",
                        sL_inf.mean, sL_annot.mean,
                         sG_inf.mean, sG_annot.mean
                     ));
                    // then stdL, stdG
                     writer.write(String.format("%.2f;%.2f;%.2f;%.2f;",
                        sL_inf.std, sL_annot.std,
                        sG_inf.std, sG_annot.std
                     ));
                      // then minL, minG
                     writer.write(String.format("%d;%d;%d;%d;",
                       sL_inf.min, sL_annot.min,
                       sG_inf.min, sG_annot.min
                    ));
                    // then maxL, maxG and newline
                    writer.write(String.format("%d;%d;%d;%d;",
                       sL_inf.max, sL_annot.max,
                       sG_inf.max, sG_annot.max  ));
                   
                    writer.newLine();
                    //System.out.println(sL_inf.median+" , "+sL_annot.median+" , "+sG_inf.median+" , "+sG_annot.median+" , "+ratio_inf+" , "+ratio_annot);
                    //VitimageUtils.waitFor(100000);    
                }
            }
            IJ.log("CSV saved to " + OUT_CSV);
        } catch (IOException e) {
            IJ.error("Error writing CSV: " + e.getMessage());
        }
        IJ.log("=== Done ===");
    }

    private static List<Integer> collectAreas(ImagePlus imp) {
        List<Integer> list = new ArrayList<>();
        int n = imp.getNSlices();
        for (int z = 1; z <= n; z++) {
            imp.setSlice(z);
            ByteProcessor bp = (ByteProcessor) imp.getProcessor();
            int count = 0;
            for (int y = 0; y < bp.getHeight(); y++) {
                for (int x = 0; x < bp.getWidth(); x++) {
                    if (bp.get(x,y) > 0) count++;
                }
            }
            list.add(count);
        }
        return list;
    }

    private static Stats computeStats(List<Integer> vals) {
        Collections.sort(vals);
        int n = vals.size();
        double sum = 0;
        for (int v : vals) sum += v;
        double mean = sum / n;
        double median = (n % 2 == 1)
            ? vals.get(n/2)
            : (vals.get(n/2 - 1) + vals.get(n/2)) / 2.0;
        double var = 0;
        for (int v : vals) var += (v - mean)*(v - mean);
        double std = Math.sqrt(var / n);
        return new Stats(mean, median, std, vals.get(0), vals.get(n-1));
    }

    private static class Stats {
        double mean, median, std;
        int min, max;
        Stats(double mean, double median, double std, int min, int max) {
            this.mean = mean; this.median = median;
            this.std = std; this.min = min; this.max = max;
        }
    }
}

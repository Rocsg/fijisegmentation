package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Script_13_IOU_mono.java
 *
 * Calcule l'IoU entre ground truth (exemples_128_gaine_aug) et prédictions
 * (inference/{split}/seg_gaine) pour chaque slice, pour les splits train et test.
 * Affiche la moyenne et l'écart-type de l'IoU.
 * Skip les slices sans prédiction.
 */
public class Script_13_IOU_Mono {
    private static final String BASE_PATH     = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String GT_DIR        = BASE_PATH + "/slice_stacks";
    private static final String INF_DIR       = BASE_PATH + "/inference";
    private static final String TRAIN_CSV     = BASE_PATH + "/train_split.csv";
    private static final String TEST_CSV      = BASE_PATH + "/test_split.csv";
    private static final int    PATCH_SIZE    = 128;

    public static void main(String[] args) {
        evaluateSplit("train", TRAIN_CSV);
        evaluateSplit("test",  TEST_CSV);
    }

    private static void evaluateSplit(String splitName, String csvPath) {
        IJ.log("\n=== Evaluating split: " + splitName + " ===");
        List<Double> ious_gaine = new ArrayList<>();
        List<Double> ious_lacuna = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            br.readLine(); // header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                String cube = parts[0];
                String code = parts[1];
                File gtGaineFolder  = new File(GT_DIR  + "/maskleaf/" + cube + "/" + code);
                File infGaineFolder = new File(INF_DIR + "/" + splitName + "/seg_gaine/" + cube + "/" + code);
                File gtLacunaFolder  = new File(GT_DIR  + "/maskLacuna/" + cube + "/" + code);
                File infLacunaFolder = new File(INF_DIR + "/" + splitName + "/seg_lacuna/" + cube + "/" + code);

                //Print the folders


                //PROCESSING GAINE
                if (!gtGaineFolder.isDirectory() || !infGaineFolder.isDirectory()) {
                    IJ.log("Skipping missing folders for " + cube + " / " + code);
                    continue;
                }
                else{
                    IJ.log("Processing existing folders for " + cube + " / " + code);

                }
                for (File gtFile : gtGaineFolder.listFiles((d,n)->n.endsWith(".tif"))) {
                    String sliceName = gtFile.getName(); // e.g. slice_001.tif
                    File predFile = new File(infGaineFolder, sliceName.replace(".tif","_gaine_seg.tif"));
                    if (!predFile.exists()) {
                        IJ.log("  Missing prediction: " + predFile.getPath());
                        continue;
                    }
                    // load images
                    ByteProcessor gtIp   = (ByteProcessor) IJ.openImage(gtFile.getAbsolutePath()).getProcessor();
                    ByteProcessor predIp = (ByteProcessor) IJ.openImage(predFile.getAbsolutePath()).getProcessor();
                    // compute IoU
                    int w = PATCH_SIZE, h = PATCH_SIZE;
                    int inter = 0, uni = 0;
                    for (int y=0; y<h; y++) {
                        for (int x=0; x<w; x++) {
                            boolean g = gtIp.get(x,y) > 0;
                            boolean p = predIp.get(x,y) > 0;
                            if (g && p) inter++;
                            if (g || p) uni++;
                        }
                    }
                    if (uni > 0) {
                        ious_gaine.add((double)inter/uni);
                    }
                }
                if (ious_gaine.isEmpty()) {
                    IJ.log("No IoU values computed for split " + splitName);
                }
                else{
                    // compute mean and std
                    double sum=0;
                    for (double v : ious_gaine) sum += v;
                    double mean = sum / ious_gaine.size();
                    double var=0;
                    for (double v : ious_gaine) var += (v-mean)*(v-mean);
                    double std = Math.sqrt(var / ious_gaine.size());
                    IJ.log(String.format("Split %s: mean IoU = %.4f, std = %.4f (n=%d)", splitName, mean, std, ious_gaine.size()));
                }
        









                //PROCESSING LACUNA
                if (!gtLacunaFolder.isDirectory() || !infLacunaFolder.isDirectory()) {
                    IJ.log("Skipping missing folders for " + cube + " / " + code);
                    continue;
                }
                for (File gtFile : gtLacunaFolder.listFiles((d,n)->n.endsWith(".tif"))) {
                    String sliceName = gtFile.getName(); // e.g. slice_001.tif
                    File predFile = new File(infLacunaFolder, sliceName.replace(".tif","_gaine_seg.tif"));
                    if (!predFile.exists()) {
                        IJ.log("  Missing prediction: " + predFile.getPath());
                        continue;
                    }
                    // load images
                    ByteProcessor gtIp   = (ByteProcessor) IJ.openImage(gtFile.getAbsolutePath()).getProcessor();
                    ByteProcessor predIp = (ByteProcessor) IJ.openImage(predFile.getAbsolutePath()).getProcessor();
                    // compute IoU
                    int w = PATCH_SIZE, h = PATCH_SIZE;
                    int inter = 0, uni = 0;
                    for (int y=0; y<h; y++) {
                        for (int x=0; x<w; x++) {
                            boolean g = gtIp.get(x,y) > 0;
                            boolean p = predIp.get(x,y) > 0;
                            if (g && p) inter++;
                            if (g || p) uni++;
                        }
                    }
                    if (uni > 0) {
                        ious_lacuna.add((double)inter/uni);
                    }
                }
                if (ious_lacuna.isEmpty()) {
                    IJ.log("No IoU values computed for split " + splitName);
                }
                else{
                    // compute mean and std
                    double sum=0;
                    for (double v : ious_lacuna) sum += v;
                    double mean = sum / ious_lacuna.size();
                    double var=0;
                    for (double v : ious_lacuna) var += (v-mean)*(v-mean);
                    double std = Math.sqrt(var / ious_lacuna.size());
                    IJ.log(String.format("Split %s: mean IoU = %.4f, std = %.4f (n=%d)", splitName, mean, std, ious_lacuna.size()));
                }

            }
        } catch (IOException e) {
            IJ.error("Error reading CSV " + csvPath + ": " + e.getMessage());
            return;
        }
    }
}

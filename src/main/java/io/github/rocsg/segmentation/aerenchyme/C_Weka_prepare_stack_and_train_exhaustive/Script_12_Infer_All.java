package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import io.github.rocsg.fijiyama.common.Timer;
import trainableSegmentation.WekaSegmentation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Script_12_Infer_All.java
 *
 * Utilise les stacks de slices générées (toutes augmentations empilées) pour
 * effectuer l'inférence slice par slice, en une seule passe par slice,
 * avec sauvegarde des cartes de probabilité et des segmentations binaires.
 */
public class Script_12_Infer_All {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String SLICE_DIR_RAW  = BASE_PATH + "/slice_stacks/raw";
    private static final String TRAIN_CSV      = BASE_PATH + "/train_split.csv";
    private static final String TEST_CSV       = BASE_PATH + "/test_split.csv";
    private static final String MODEL_GAINE    = BASE_PATH + "/trained_gaine_all.model.model";
    private static final String MODEL_LACUNA   = BASE_PATH + "/trained_lacuna_all.model.model";
    private static final String OUT_DIR        = BASE_PATH + "/inference";

    public static void main(String[] args) {
        // Initialisation unique des classifieurs Weka
        // On prend un exemple de slice stack pour initialiser
        String sample = SLICE_DIR_RAW + "/Cube_01/Cube_01_L1_C1/slice_001.tif";
        IJ.log("Init Weka with sample slice: " + sample);
        ImagePlus dummy = IJ.openImage(sample);
        WekaSegmentation segGaine = new WekaSegmentation(dummy);
        segGaine.loadClassifier(MODEL_GAINE);
        WekaSegmentation segLacuna = new WekaSegmentation(dummy);
        segLacuna.loadClassifier(MODEL_LACUNA);

        // Inférence sur train et test
        runInference("train", TRAIN_CSV, segGaine, segLacuna);
        runInference("test",  TEST_CSV,  segGaine, segLacuna);

        IJ.log("=== Inference terminée pour tous les slices ===");
    }

    private static void runInference(String splitName,
                                     String csvPath,
                                     WekaSegmentation segG,
                                     WekaSegmentation segL) {
        IJ.log("\n--- Inference sur split: " + splitName + " ---");
        IJ.log("Number estimation");
        int nb = 0;
        List<String[]> entries = readCsv(csvPath);
        for (String[] entry : entries) {
            String cube = entry[0], code = entry[1];
            IJ.log("Process cube=" + cube + " obj=" + code);
            File sliceFolder = new File(SLICE_DIR_RAW + "/" + cube + "/" + code);
            if (!sliceFolder.isDirectory()) {
                IJ.log("  Aucune stack pour " + code);
                continue;
            }
            for (File sliceFile : sliceFolder.listFiles((d,n)->n.endsWith(".tif"))) {
                nb++;
            }
        }

        int NB=nb;
        IJ.log("Total number="+nb);
        Timer timer = new Timer();
        timer.print("Start");

        nb=0;
        entries = readCsv(csvPath);
        for (String[] entry : entries) {
            String cube = entry[0], code = entry[1];
            IJ.log("Process cube=" + cube + " obj=" + code);
            File sliceFolder = new File(SLICE_DIR_RAW + "/" + cube + "/" + code);
            if (!sliceFolder.isDirectory()) {
                IJ.log("  Aucune stack pour " + code);
                continue;
            }
            for (File sliceFile : sliceFolder.listFiles((d,n)->n.endsWith(".tif"))) {
                nb++;
                timer.print("Processing slice "+nb+"/"+NB);
                String sliceName = sliceFile.getName().replace(".tif", "");

                File dirBin  = new File(OUT_DIR + "/" + splitName + "/seg_"  + "lacuna" + "/" + cube + "/" + code);
                String nameSeg = sliceName + "_" + "lacuna" + "_seg.tif";
                if(new File(dirBin, nameSeg).exists()){
                    System.out.println("Already ok");
                    continue;
                }
                System.out.println("  Slice: " + sliceName);
                // Charge la stack de toutes les augmentations pour cette slice
                ImagePlus augStack = IJ.openImage(sliceFile.getAbsolutePath());
                if (augStack == null) {
                    System.err.println("    Impossible d'ouvrir: " + sliceFile);
                    continue;
                }
                // Inférence Gaine
                ImagePlus probG = segG.applyClassifier(augStack, 0, true);
                FloatProcessor fpG = (FloatProcessor) probG.getStack().getProcessor(2);
                ByteProcessor binG = threshold(fpG);
                save(splitName, cube, code, sliceName, "gaine", fpG, binG);
                // Inférence Lacuna
                ImagePlus probL = segL.applyClassifier(augStack, 0, true);
                FloatProcessor fpL = (FloatProcessor) probL.getStack().getProcessor(2);
                ByteProcessor binL = threshold(fpL);
                save(splitName, cube, code, sliceName, "lacuna", fpL, binL);
            }
        }
    }

    private static ByteProcessor threshold(FloatProcessor fp) {
        int w = fp.getWidth(), h = fp.getHeight();
        ByteProcessor bp = new ByteProcessor(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (fp.getf(x, y) > 0.5f) bp.set(x, y, 255);
            }
        }
        return bp;
    }
        
    private static void save(String split,
                             String cube,
                             String code,
                             String slice,
                             String model,
                             FloatProcessor fp,
                             ByteProcessor bp) {
        // Directories: inf/{split}/prob_{model}/{cube}/{code}
        File dirProb = new File(OUT_DIR + "/" + split + "/prob_" + model + "/" + cube + "/" + code);
        File dirBin  = new File(OUT_DIR + "/" + split + "/seg_"  + model + "/" + cube + "/" + code);
        dirProb.mkdirs(); dirBin.mkdirs();
        // Save probability map
        String nameProb = slice + "_" + model + "_prob.tif";
        new FileSaver(new ImagePlus(nameProb, fp)).saveAsTiff(
            new File(dirProb, nameProb).getAbsolutePath()
        );
        // Save binary segmentation
        String nameSeg = slice + "_" + model + "_seg.tif";
        new FileSaver(new ImagePlus(nameSeg, bp)).saveAsTiff(
            new File(dirBin, nameSeg).getAbsolutePath()
        );
    }

    private static List<String[]> readCsv(String path) {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split(","); if (p.length >= 2) list.add(p);
            }
        } catch (IOException e) {
            IJ.error("Lecture CSV échouée: " + e.getMessage());
        }
        return list;
    }
}

package io.github.rocsg.segmentation.aerenchyme.D_Weka_modelling_lightweight_by_hand;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import trainableSegmentation.WekaSegmentation;

import java.io.File;

public class Script_Alter_01 {
    private static final String BASE_PATH     = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String SLICE_RAW_DIR = BASE_PATH + "/slice_stacks/raw";
    private static final String MODEL_PATH    = BASE_PATH + "/classifier_bare.model";
    private static final String OUT_DIR       = BASE_PATH + "/slice_stacks_segmented_tissue";
    private static final double THRESHOLD     = 0.5;

    public static void main(String[] args) {
        // Initialize WekaSegmentation once
        // Use first available slice to init
        File rawBase = new File(SLICE_RAW_DIR);
        File firstCube = rawBase.listFiles(File::isDirectory)[0];
        File firstObj = firstCube.listFiles(File::isDirectory)[0];
        File sampleSlice = firstObj.listFiles((d,n)->n.endsWith(".tif"))[0];
        IJ.log("Initializing classifier with sample: " + sampleSlice.getPath());
        ImagePlus dummy = IJ.openImage(sampleSlice.getAbsolutePath());
        WekaSegmentation seg = new WekaSegmentation(dummy);
        seg.loadClassifier(MODEL_PATH);

        // Traverse raw slice stacks
        for (File cubeDir : rawBase.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                //if(!code.contains("Cube_05_L10_C1")) continue;
                IJ.log("Processing cube=" + cube + " code=" + code);
                File outObjDir = new File(OUT_DIR + "/raw/" + cube + "/" + code);
                outObjDir.mkdirs();
                for (File sliceFile : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    String sliceName = sliceFile.getName().replaceFirst("\\.tif$", "");
                    IJ.log("  Segmenting " + sliceName);
                    ImagePlus sliceStack = IJ.openImage(sliceFile.getAbsolutePath());
                    //extract the first slice
                    sliceStack = new ImagePlus("",sliceStack.getStack().getProcessor(1).duplicate());
                    
                    if (sliceStack == null) {
                        IJ.error("Cannot open slice stack: " + sliceFile.getPath());
                        continue;
                    }
                    // Apply classifier: returns prob map hyperstack
                    ImagePlus proba = seg.applyClassifier(sliceStack, 0, true);
                    // Extract probability channel 2
                    FloatProcessor fp = (FloatProcessor) proba.getStack().getProcessor(2);
                    // Threshold to binary
                    ByteProcessor bin = new ByteProcessor(fp.getWidth(), fp.getHeight());
                    for (int y = 0; y < fp.getHeight(); y++) {
                        for (int x = 0; x < fp.getWidth(); x++) {
                            if (fp.getf(x, y) > THRESHOLD) bin.set(x, y, 255);
                        }
                    }
                    // Save binary segmentation
                    String outPath = new File(outObjDir, sliceName + "_pred.tif").getAbsolutePath();
                    new FileSaver(new ImagePlus(sliceName + "_pred", bin)).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Segmentation complete: output in " + OUT_DIR + "/raw ===");
    }
}

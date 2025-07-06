package io.github.rocsg.segmentation.aerenchyme.D_Weka_modelling_lightweight_by_hand;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Script_annex_make_manual.java
 *
 * Ouvre RawAll.tif, extrait aléatoirement 1000 slices,
 * et sauvegarde la sous-stack RawFullExtract.tif
 */
public class Script_annex_make_manual {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String RAW_ALL_PATH   = BASE_PATH + "/RawAll.tif";
    private static final String OUTPUT_PATH    = BASE_PATH + "/RawAllExtract.tif";
    private static final int    N_EXTRACT      = 1000;
    private static final int    RANDOM_SEED    = 42;

    public static void main(String[] args) {
        IJ.log("Loading full raw stack: " + RAW_ALL_PATH);
        ImagePlus impAll = IJ.openImage(RAW_ALL_PATH);
        if (impAll == null) {
            IJ.error("Cannot open rawAll: " + RAW_ALL_PATH);
            return;
        }
        int total = impAll.getNSlices();
        IJ.log("Total slices available: " + total);
        int n = Math.min(N_EXTRACT, total);

        // generate random unique indices
        List<Integer> indices = new ArrayList<>();
        for (int i = 1; i <= total; i++) indices.add(i);
        Collections.shuffle(indices, new Random(RANDOM_SEED));
        List<Integer> sample = indices.subList(0, n);
        Collections.sort(sample);

        IJ.log("Extracting " + n + " random slices");
        ImageStack outStack = new ImageStack(impAll.getWidth(), impAll.getHeight());
        for (int z : sample) {
            impAll.setSlice(z);
            IJ.log("  Adding slice " + z);
            outStack.addSlice(impAll.getStack().getSliceLabel(z), impAll.getProcessor().duplicate());
        }

        ImagePlus impExt = new ImagePlus("RawFullExtract", outStack);
        new FileSaver(impExt).saveAsTiffStack(OUTPUT_PATH);
        IJ.log("Saved extracted stack: " + OUTPUT_PATH);
    }
}

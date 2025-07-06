package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;

public class Script_09_Tiers_Stack_All {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String TRAIN_RAW      = BASE_PATH + "/RawTrain.tif";
    private static final String TEST_RAW       = BASE_PATH + "/RawTest.tif";
    private static final String TRAIN_GAINE    = BASE_PATH + "/examplesGaineTrain.tif";
    private static final String TEST_GAINE     = BASE_PATH + "/examplesGaineTest.tif";
    private static final String TRAIN_LACUNA   = BASE_PATH + "/examplesLacunaTrain.tif";
    private static final String TEST_LACUNA    = BASE_PATH + "/examplesLacunaTest.tif";
    private static final String OUT_RAW_ALL    = BASE_PATH + "/RawAll.tif";
    private static final String OUT_GAINE_ALL  = BASE_PATH + "/gaineAll.tif";
    private static final String OUT_LACUNA_ALL = BASE_PATH + "/lacunaAll.tif";

    public static void main(String[] args) {
        IJ.log("=== Combining train and test stacks for super-model training ===");
        combineStacks(TRAIN_RAW, TEST_RAW, OUT_RAW_ALL, "RawAll");
        combineStacks(TRAIN_GAINE, TEST_GAINE, OUT_GAINE_ALL, "GaineAll");
        combineStacks(TRAIN_LACUNA, TEST_LACUNA, OUT_LACUNA_ALL, "LacunaAll");
        IJ.log("=== All modality stacks combined ===");
    }

    private static void combineStacks(String trainPath, String testPath, String outPath, String title) {
        IJ.log("Combining " + trainPath + " + " + testPath + " -> " + outPath);
        ImagePlus impTrain = IJ.openImage(trainPath);
        ImagePlus impTest  = IJ.openImage(testPath);
        if (impTrain == null || impTest == null) {
            IJ.error("Cannot open train or test stack: " + trainPath + ", " + testPath);
            return;
        }
        ImageStack stackAll = new ImageStack(impTrain.getWidth(), impTrain.getHeight());
        IJ.log("  Adding train slices: " + impTrain.getNSlices());
        for (int z = 1; z <= impTrain.getNSlices(); z++) {
            impTrain.setSlice(z);
            stackAll.addSlice(impTrain.getStack().getSliceLabel(z), impTrain.getProcessor().duplicate());
        }
        IJ.log("  Adding test slices: " + impTest.getNSlices());
        for (int z = 1; z <= impTest.getNSlices(); z++) {
            impTest.setSlice(z);
            stackAll.addSlice(impTest.getStack().getSliceLabel(z), impTest.getProcessor().duplicate());
        }
        ImagePlus impAll = new ImagePlus(title, stackAll);
        new FileSaver(impAll).saveAsTiffStack(outPath);
    }
}

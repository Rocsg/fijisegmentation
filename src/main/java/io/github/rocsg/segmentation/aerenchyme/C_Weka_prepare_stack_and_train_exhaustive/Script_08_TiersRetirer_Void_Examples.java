package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ImageProcessor;

public class Script_08_TiersRetirer_Void_Examples {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String TRAIN_RAW    = BASE_PATH + "/RawTrain.tif";
    private static final String TRAIN_GAINE  = BASE_PATH + "/examplesGaineTrain.tif";
    private static final String TRAIN_LACUNA = BASE_PATH + "/examplesLacunaTrain.tif";
    private static final String TEST_RAW     = BASE_PATH + "/RawTest.tif";
    private static final String TEST_GAINE   = BASE_PATH + "/examplesGaineTest.tif";
    private static final String TEST_LACUNA  = BASE_PATH + "/examplesLacunaTest.tif";
    private static final String OUT_DIR      = BASE_PATH + "/stackselected";
    private static final int    CHECK_SIZE   = 30;

    public static void main(String[] args) {
        processSplit("Train", TRAIN_RAW, TRAIN_GAINE, TRAIN_LACUNA);
        processSplit("Test",  TEST_RAW,  TEST_GAINE,  TEST_LACUNA);
        IJ.log("=== Void slices removed for all splits ===");
    }

    private static void processSplit(String name,
                                     String rawPath,
                                     String gainePath,
                                     String lacunaPath) {
        IJ.log("\n-- Processing " + name + " split --");
        System.out.println("Loading stacks for " + name);
        ImagePlus rawImp    = IJ.openImage(rawPath);
        ImagePlus gaineImp  = IJ.openImage(gainePath);
        ImagePlus lacImp    = IJ.openImage(lacunaPath);
        if (rawImp==null||gaineImp==null||lacImp==null) {
            IJ.error("Cannot open one of the stacks for " + name);
            return;
        }
        ImageStack rawStack    = new ImageStack(rawImp.getWidth(), rawImp.getHeight());
        ImageStack gaineStack  = new ImageStack(rawImp.getWidth(), rawImp.getHeight());
        ImageStack lacunaStack = new ImageStack(rawImp.getWidth(), rawImp.getHeight());
        int centerX = rawImp.getWidth()/2;
        int centerY = rawImp.getHeight()/2;
        int half = CHECK_SIZE/2;
        int n = rawImp.getNSlices();
        for (int z=1; z<=n; z++) {
            System.out.println(" Slice " + z + "/" + n);
            rawImp.setSlice(z);
            gaineImp.setSlice(z);
            lacImp.setSlice(z);
            ImageProcessor gip = gaineImp.getProcessor();
            boolean hasTrue = false;
            for (int y=centerY-half; y<=centerY+half && !hasTrue; y++) {
                for (int x=centerX-half; x<=centerX+half; x++) {
                    if (gip.getPixel(x,y) > 0) { hasTrue = true; break; }
                }
            }
            if (hasTrue) {
                rawStack.addSlice(rawImp.getStack().getSliceLabel(z), rawImp.getProcessor().duplicate());
                gaineStack.addSlice(gaineImp.getStack().getSliceLabel(z), gip.duplicate());
                lacunaStack.addSlice(lacImp.getStack().getSliceLabel(z), lacImp.getProcessor().duplicate());
            } else {
                System.out.println("  Skipping void slice " + z);
            }
        }
        // Save selected stacks
        new FileSaver(new ImagePlus("Raw"+name+"Selected", rawStack))
            .saveAsTiffStack(OUT_DIR+"/Raw"+name+"_selected.tif");
        new FileSaver(new ImagePlus("Gaine"+name+"Selected", gaineStack))
            .saveAsTiffStack(OUT_DIR+"/Gaine"+name+"_selected.tif");
        new FileSaver(new ImagePlus("Lacuna"+name+"Selected", lacunaStack))
            .saveAsTiffStack(OUT_DIR+"/Lacuna"+name+"_selected.tif");
        IJ.log(name+" split: kept " + rawStack.getSize() + " slices");
    }
}

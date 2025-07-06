package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ByteProcessor;

import java.io.File;

public class Script_Tiers_Deux_Correction_Annotation_Lacuna {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_GAINE_DIR   = BASE_PATH + "/patches_256_tiers/maskleaf";
    private static final String IN_LACUNA_DIR  = BASE_PATH + "/patches_256/masklacuna";
    private static final String OUT_LACUNA_DIR = BASE_PATH + "/patches_256_tiers/masklacuna";
    private static final int    PATCH_SIZE     = 256;

    public static void main(String[] args) {
        File gaineBase = new File(IN_GAINE_DIR);
        for (File cubeDir : gaineBase.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            File lacunaCubeIn = new File(IN_LACUNA_DIR, cube);
            File lacunaCubeOut = new File(OUT_LACUNA_DIR, cube);
            lacunaCubeOut.mkdirs();

            for (File gaineFile : cubeDir.listFiles((d,n)->n.endsWith(".tif"))) {
                String code = gaineFile.getName().replaceFirst("\\.tif$", "");
                IJ.log("Masking lacuna with corrected gaine: " + cube + " / " + code);
                ImagePlus gaineImp = IJ.openImage(gaineFile.getAbsolutePath());
                ImagePlus lacunaImp = IJ.openImage(
                    lacunaCubeIn.getAbsolutePath() + "/" + code + ".tif"
                );
                if (gaineImp == null || lacunaImp == null) {
                    IJ.log("  Missing gaine or lacuna for " + code);
                    continue;
                }
                int nSlices = gaineImp.getNSlices();
                ImageStack outStack = new ImageStack(PATCH_SIZE, PATCH_SIZE);

                for (int z = 1; z <= nSlices; z++) {
                    gaineImp.setSlice(z);
                    lacunaImp.setSlice(z);
                    ByteProcessor gp = (ByteProcessor)gaineImp.getProcessor();
                    ByteProcessor lp = (ByteProcessor)lacunaImp.getProcessor().duplicate();

                    // Mask lacuna: keep only where gp==255
                    for (int y = 0; y < PATCH_SIZE; y++) {
                        for (int x = 0; x < PATCH_SIZE; x++) {
                            if (gp.get(x,y) != 255) {
                                lp.set(x,y,0);
                            }
                        }
                    }
                    outStack.addSlice(lacunaImp.getStack().getSliceLabel(z), lp);
                }

                // Save corrected lacuna stack
                String outPath = lacunaCubeOut.getAbsolutePath() + "/" + code + ".tif";
                new FileSaver(new ImagePlus(code, outStack)).saveAsTiffStack(outPath);
                IJ.log("Saved corrected lacuna: " + outPath);
            }
        }
        IJ.log("=== Lacuna correction completed ===");
    }
}

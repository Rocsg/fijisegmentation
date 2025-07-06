package io.github.rocsg.segmentation.aerenchyme.C_Weka_prepare_stack_and_train_exhaustive;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Script_11_Stack_Aug_Slices.java
 *
 * Pour chaque cube, chaque objet et chaque tranche, construit une stack
 * contenant toutes les augmentations (angles) empilées.
 * Génère l'arborescence:
 * BASE_PATH/slice_stacks/{modality}/{cube}/{codeObj}/slice_ZZZ.tif
 */
public class Script_11_Stack_Aug_Slices {
    private static final String BASE_PATH      = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR_RAW     = BASE_PATH + "/patches_128_aug/raw";
    private static final String IN_DIR_LACUNA  = BASE_PATH + "/patches_128_aug/masklacuna";
    private static final String IN_DIR_LEAF    = BASE_PATH + "/patches_128_aug/maskleaf";
    private static final String OUT_DIR        = BASE_PATH + "/slice_stacks";
    private static final int    AUG_FACTOR     = 16;
    private static final double ANGLE_STEP     = 360.0 / AUG_FACTOR;

    private enum Modality {
        RAW("raw", IN_DIR_RAW),
        LACUNA("masklacuna", IN_DIR_LACUNA),
        GAINE("maskleaf", IN_DIR_LEAF);

        final String name;
        final String dir;
        Modality(String name, String dir) { this.name = name; this.dir = dir; }
    }

/*************  ✨ Windsurf Command ⭐  *************/
/*******  7f9d7c18-be53-4182-ad6d-0f1532b9d26a  *******/
    public static void main(String[] args) {
        List<Modality> mods = Arrays.asList(Modality.RAW, Modality.LACUNA, Modality.GAINE);
        for (Modality mod : mods) {
            System.out.println("Now processing modality " +mod);
            File modIn = new File(mod.dir);
            if (!modIn.isDirectory()) {
                System.out.println("Input directory not found: " + mod.dir);
                continue;
            }
            for (File cubeDir : modIn.listFiles(File::isDirectory)) {

                String cube = cubeDir.getName();
                System.out.println("Now processing dir " +cube);
                for (File patchFile : cubeDir.listFiles((d,n)-> n.endsWith(".tif"))) {
                    System.out.println("Now processing patch " +patchFile.getAbsolutePath());
                    String codeObj = patchFile.getName().split("_rot")[0];
                    ImagePlus imp = IJ.openImage(patchFile.getAbsolutePath());
                    VitimageUtils.printImageResume(imp);
                    if (imp == null) continue;
                    int nSlices = imp.getNSlices();
                    for (int z = 1; z <= nSlices; z++) {
                        // créer stack pour cette tranche
                        ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight());
                        for (int i = 0; i < AUG_FACTOR; i++) {
                            double angle = i * ANGLE_STEP;
                            String fname = String.format("%s_rot%03d.tif", codeObj, (int)Math.round(angle));
                            File augFile = new File(cubeDir, fname);
                            if (!augFile.exists()) System.out.println(augFile.getAbsolutePath() + " does not exist" );
                            ImagePlus augImp = IJ.openImage(augFile.getAbsolutePath());
                            if (augImp == null) continue;
                            augImp.setSlice(z);
                            ImageProcessor ip = augImp.getProcessor();
                            stack.addSlice(augImp.getStack().getSliceLabel(z), ip.duplicate());
                        }
                        // sauvegarde
                        File outFolder = new File(OUT_DIR + "/" + mod.name + "/" + cube + "/" + codeObj);
                        if (!outFolder.exists()) outFolder.mkdirs();
                        String outName = String.format("slice_%03d.tif", z);
                        IJ.saveAsTiff(new ImagePlus(codeObj + "_slice" + z, stack),new File(outFolder, outName).getAbsolutePath());
                    }
                    IJ.log("Processed stacks for " + mod.name + " / " + cube + " / " + codeObj);
                }
            }
        }
        IJ.log("Script 11 completed: slice stacks generated.");
    }
}

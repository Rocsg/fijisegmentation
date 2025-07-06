package io.github.rocsg.segmentation.aerenchyme.B_Prepare_patches;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ImageProcessor;

import java.io.File;
import java.util.Arrays;
import java.util.List;


public class Script_05_Extract_Patches_128 {
    private static final String BASE_PATH = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR = BASE_PATH + "/patches_256_old";
    private static final String OUT_DIR = BASE_PATH + "/patches_128_aug";
    private static final int IN_SIZE = 256;
    private static final int OUT_SIZE = 128;
    private static final int HALF_IN = IN_SIZE / 2;
    private static final int HALF_OUT = OUT_SIZE / 2;
    private static final int AUG_FACTOR = 16;
    private static final double ANGLE_STEP = 360.0 / AUG_FACTOR;

    private enum Modality {
        RAW("raw"),
        LACUNA("masklacuna"),
        GAINE("maskleaf");

        final String name;
        Modality(String name) { this.name = name; }
    }

    public static void main(String[] args) {
        List<Modality> modalities = Arrays.asList(Modality.RAW, Modality.LACUNA, Modality.GAINE);
        for (Modality mod : modalities) {
            File inModDir = new File(IN_DIR, mod.name);
            if (!inModDir.isDirectory()) {
                IJ.log("Input modality not found: " + inModDir);
                continue;
            }
            for (File cubeDir : inModDir.listFiles(File::isDirectory)) {
                String cubeName = cubeDir.getName();
                File outModCube = new File(OUT_DIR + "/" + mod.name, cubeName);
                if (!outModCube.exists()) outModCube.mkdirs();

                for (File patchFile : cubeDir.listFiles((d,n) -> n.endsWith(".tif"))) {
                    String codeObj = patchFile.getName().replace(".tif", "");
                    //if(!codeObj.contains("Cube_05_L10_C1"))continue;
                    // generate augmented patches
                    for (int i = 0; i < AUG_FACTOR; i++) {
                        double angle = i * ANGLE_STEP;
                        ImageStack stack = new ImageStack(OUT_SIZE, OUT_SIZE);
                        ImagePlus inp = IJ.openImage(patchFile.getAbsolutePath());
                        int nSlices = inp.getNSlices();
                           if (inp == null) {
                            IJ.log("Cannot open patch: " + patchFile);
                            continue;
                        }
                        for (int z = 1; z <= nSlices; z++) {
                            inp.setSlice(z);
                            ImageProcessor ip = inp.getProcessor();
                            // rotate
                            if(mod == Modality.RAW) {
                                ip.setInterpolationMethod(ImageProcessor.BILINEAR);
                                ip.rotate(angle);
                            }
                            else {
                                ip.setInterpolationMethod(ImageProcessor.NONE);
                                ip.rotate(angle);
                            }
                            // crop center OUT_SIZE x OUT_SIZE
                            int cx = HALF_IN;
                            int cy = HALF_IN;
                            int x0 = cx - HALF_OUT;
                            int y0 = cy - HALF_OUT;
                            ip.setRoi(x0, y0, OUT_SIZE, OUT_SIZE);
                            ImageProcessor crop = ip.crop();
                            stack.addSlice(String.format("z%02d", z), crop);
                        }
                        // save stack
                        String outName = String.format("%s_rot%03d.tif", codeObj, (int)Math.round(angle));
                        File outFile = new File(outModCube, outName);
                        new FileSaver(new ImagePlus(codeObj, stack)).saveAsTiffStack(outFile.getAbsolutePath());
                        IJ.log(String.format("Saved %s [%s, angle=%.1f]", outName, mod.name, angle));
                    }
                }
            }
        }
        IJ.log("Augmented 128x128 patch extraction completed.");
    }
}

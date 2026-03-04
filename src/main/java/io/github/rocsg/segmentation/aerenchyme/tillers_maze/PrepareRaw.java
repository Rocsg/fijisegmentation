package io.github.rocsg.segmentation.aerenchyme.tillers_maze;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.io.File;

public class PrepareRaw {

    static final String OUTPUT_DIR = "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/Registered_manual";

    static final int CROP_WIDTH = 1479;
    static final int CROP_HEIGHT = 1400;
    static final int CROP_SLICES = 2019;

    static final CropJob[] JOBS = new CropJob[]{
            new CropJob(
                    "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/01_Anato_full/01_Anato_full_SlicesY_8bit.tif",
                    "000.tif",
                    600+0, 600+10, 14
            ),
            new CropJob(
                    "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/02_Fast_Ref/02_Fast_Ref_SlicesY_8bit.tif",
                    "001.tif",
                    600+89, 600+13, 63
            ),
            new CropJob(
                    "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/03_Fast-00/03_Fast-00_SlicesY_8bit.tif",
                    "002.tif",
                    600+0, 600+0, 0
            ),
            new CropJob(
                    "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/03_Fast-01/03_Fast-01_SlicesY_8bit.tif",
                    "003.tif",
                    600+4, 600+12, 5
            ),
            new CropJob(
                    "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/03_Fast-02/03_Fast-02_SlicesY_8bit.tif",
                    "004.tif",
                    600+7, 600+9, 26
            )
    };

    public static void main(String[] args) {
        File outDir = new File(OUTPUT_DIR);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new RuntimeException("Impossible de créer le dossier de sortie : " + OUTPUT_DIR);
        }

        System.out.println("=== PrepareRaw : début ===");
        System.out.println("Dossier de sortie : " + OUTPUT_DIR);
        System.out.println("Crop fixe : width=" + CROP_WIDTH + ", height=" + CROP_HEIGHT + ", nbSlices=" + CROP_SLICES);

        for (CropJob job : JOBS) {
            cropAndSave(job);
        }

        System.out.println("=== PrepareRaw : terminé ===");
    }

    static void cropAndSave(CropJob job) {
        System.out.println("\nLecture : " + job.inputPath);
        ImagePlus input = IJ.openImage(job.inputPath);
        if (input == null) {
            throw new RuntimeException("Impossible d'ouvrir : " + job.inputPath);
        }

        int inWidth = input.getWidth();
        int inHeight = input.getHeight();
        int inSlices = input.getNSlices();

        int x0 = job.x0;
        int y0 = job.y0;
        int z0 = job.z0;

        int x1Exclusive = x0 + CROP_WIDTH;
        int y1Exclusive = y0 + CROP_HEIGHT;
        int z1Exclusive = z0 + CROP_SLICES;

        if (x0 < 0 || y0 < 0 || z0 < 0) {
            input.close();
            throw new IllegalArgumentException("Coordonnées négatives pour " + job.outputName + " : (" + x0 + "," + y0 + "," + z0 + ")");
        }
        if (x1Exclusive > inWidth || y1Exclusive > inHeight || z1Exclusive > inSlices) {
            input.close();
            throw new IllegalArgumentException(
                    "Crop hors limites pour " + job.outputName
                            + " | stack source=" + inWidth + "x" + inHeight + "x" + inSlices
                            + " | demandé x=[" + x0 + "," + (x1Exclusive - 1) + "]"
                            + " y=[" + y0 + "," + (y1Exclusive - 1) + "]"
                            + " z=[" + z0 + "," + (z1Exclusive - 1) + "]"
            );
        }

        ImageStack inStack = input.getStack();
        ImageStack outStack = new ImageStack(CROP_WIDTH, CROP_HEIGHT);

        for (int z = z0; z < z1Exclusive; z++) {
            int stackIndex = z + 1;
            ImageProcessor ip = inStack.getProcessor(stackIndex);
            ip.setRoi(x0, y0, CROP_WIDTH, CROP_HEIGHT);
            ImageProcessor cropped = ip.crop();
            outStack.addSlice(inStack.getSliceLabel(stackIndex), cropped);
        }

        ImagePlus output = new ImagePlus(job.outputName, outStack);
        output.setCalibration(input.getCalibration());

        String outPath = new File(OUTPUT_DIR, job.outputName).getAbsolutePath();
        IJ.saveAsTiff(output, outPath);
        System.out.println("Sauvé : " + outPath + " (" + CROP_WIDTH + "x" + CROP_HEIGHT + "x" + CROP_SLICES + ")");

        output.close();
        input.close();
    }

    static class CropJob {
        final String inputPath;
        final String outputName;
        final int x0;
        final int y0;
        final int z0;

        CropJob(String inputPath, String outputName, int x0, int y0, int z0) {
            this.inputPath = inputPath;
            this.outputName = outputName;
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
        }
    }
}

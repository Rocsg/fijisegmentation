package io.github.rocsg.segmentation.aerenchyme.tillers_maze;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.process.ByteProcessor;
import ij.plugin.Duplicator;
import ij.plugin.ImageCalculator;
import java.io.File;

/**
 * TimeLapseXylems - Time-lapse 3D analysis of xylem vessel filling.
 * 
 * Pipeline to analyze contrast agent progression in rice plant xylem vessels
 * imaged by micro-CT. Computes successive differences from a reference image,
 * segments the appearing vessels, labels them temporally, and produces a
 * color-coded overlay on the reference anatomy.
 * 
 * Quadrant numbering for temporal labels:
 *   1 = appeared at t1 (6 min)
 *   2 = appeared at t2 (12 min)
 *   3 = appeared at t3 (18 min)
 *   4 = appeared at t4 (24 min)
 */
public class TimeLapseXylems {

    // ===================== USER SETTINGS =====================

    /** Directory containing the raw 3D stacks */
    static String DIR_RAW = "/home/rfernandez/Bureau/A_Test/TimeLapse/Raw";

    /** Directory for computed results */
    static String DIR_COMPUTED = "/home/rfernandez/Bureau/A_Test/TimeLapse/Computed";

    /** Reference image filename */
    static String REF_NAME = "ImgRef.tif";

    /** Successive image filenames */
    static String[] IMG_NAMES = {"Img_0.tif", "Img_1.tif", "Img_2.tif", "Img_3.tif"};

    /** End-of-acquisition time in minutes for each image */
    static double[] TIME_MINUTES = {6, 12, 18, 24};

    /** Duration of each acquisition in minutes */
    static double ACQ_DURATION = 4;

    /** Threshold for vessel segmentation on difference images (to adjust) */
    static double VESSEL_THRESHOLD = 500;

    static double[] WORKING_AREA = new double[]{0.1,0.9,0.1,0.9,0.1,0.9}; // [xMin,xMax,yMin,yMax,zMin,zMax] as fractions of dimensions
    // ===================== PIPELINE SWITCHES =====================

    /** Step 1: Compute difference images (Img_i - ImgRef) */
    static boolean DO_STEP1_COMPUTE_DIFFERENCES = true;

    /** Step 2: Segment vessels from difference images */
    static boolean DO_STEP2_SEGMENTATION = false;

    /** Step 3: Build temporal labeling (1,2,3,4) */
    static boolean DO_STEP3_TEMPORAL_LABELING = false;

    /** Step 4: Apply Fire colormap and overlay on reference */
    static boolean DO_STEP4_COLORMAP_AND_OVERLAY = false;

    // ===================== OPTIONAL FILTERS =====================

    /** Apply 3D median filter on difference images before segmentation */
    static boolean DO_MEDIAN_FILTER = false;

    /** Radius of the 3D median filter */
    static double MEDIAN_RADIUS = 2.0;


    // ===================== MAIN =====================

    public static void main(String[] args) {
        System.out.println("=== TimeLapseXylems Pipeline ===");
        System.out.println("Raw dir:      " + DIR_RAW);
        System.out.println("Computed dir: " + DIR_COMPUTED);
        System.out.println("Threshold:    " + VESSEL_THRESHOLD);
        System.out.println("");

        // Ensure output directory exists
        new File(DIR_COMPUTED).mkdirs();

        int nImages = IMG_NAMES.length;

        if (DO_STEP1_COMPUTE_DIFFERENCES) {
            System.out.println("--- STEP 1: Computing differences ---");
            step1_computeDifferences(nImages);
        }

        if (DO_STEP2_SEGMENTATION) {
            System.out.println("--- STEP 2: Segmenting vessels ---");
            step2_segmentVessels(nImages);
        }

        if (DO_STEP3_TEMPORAL_LABELING) {
            System.out.println("--- STEP 3: Temporal labeling ---");
            step3_temporalLabeling(nImages);
        }

        if (DO_STEP4_COLORMAP_AND_OVERLAY) {
            System.out.println("--- STEP 4: Colormap and overlay ---");
            step4_colormapAndOverlay();
        }

        System.out.println("=== Pipeline complete ===");
    }


    // ===================== STEP 1: COMPUTE DIFFERENCES =====================

    /**
     * For each Img_i, compute Diff_i = Img_i - ImgRef in 32-bit float.
     * Saves Diff_i.tif into DIR_COMPUTED.
     */
    static void step1_computeDifferences(int nImages) {
        // Load reference image (16-bit)
        ImagePlus impRef = IJ.openImage(DIR_RAW + File.separator + REF_NAME);
        if (impRef == null) {
            System.out.println("ERROR: Cannot open reference image: " + DIR_RAW + File.separator + REF_NAME);
            return;
        }
        System.out.println("  Loaded reference: " + REF_NAME + " (" + impRef.getWidth() + "x" + impRef.getHeight() + "x" + impRef.getNSlices() + ")");

        // Convert reference to 32-bit
        ImagePlus refFloat = new Duplicator().run(impRef);
        IJ.run(refFloat, "32-bit", "");

        for (int i = 0; i < nImages; i++) {
            String name = IMG_NAMES[i];
            System.out.println("  Processing " + name + "...");

            // Load time-point image
            ImagePlus impI = IJ.openImage(DIR_RAW + File.separator + name);
            if (impI == null) {
                System.out.println("  ERROR: Cannot open " + name + ", skipping.");
                continue;
            }

            // Convert to 32-bit
            IJ.run(impI, "32-bit", "");

            // Compute difference: Img_i - ImgRef
            ImagePlus diff = computeDifference(impI, refFloat);
            diff.setTitle("Diff_" + i);

            // Save
            String outPath = DIR_COMPUTED + File.separator + "Diff_" + i + ".tif";
            IJ.saveAsTiff(diff, outPath);
            System.out.println("  Saved: " + outPath);

            impI.close();
            diff.close();
        }

        refFloat.close();
        impRef.close();
        System.out.println("  Step 1 done.\n");
    }

    /**
     * Compute pixel-by-pixel difference between two 32-bit stacks.
     * Returns a new 32-bit ImagePlus = imgA - imgB.
     */
    static ImagePlus computeDifference(ImagePlus imgA, ImagePlus imgB) {
        int w = imgA.getWidth();
        int h = imgA.getHeight();
        int nSlices = imgA.getNSlices();
        ImageStack stackA = imgA.getStack();
        ImageStack stackB = imgB.getStack();
        ImageStack stackResult = new ImageStack(w, h);

        for (int z = 1; z <= nSlices; z++) {
            float[] pixA = (float[]) stackA.getProcessor(z).getPixels();
            float[] pixB = (float[]) stackB.getProcessor(z).getPixels();
            float[] pixR = new float[w * h];
            for (int j = 0; j < pixR.length; j++) {
                pixR[j] = pixA[j] - pixB[j];
            }
            stackResult.addSlice("", new FloatProcessor(w, h, pixR));
        }
        return new ImagePlus("Difference", stackResult);
    }


    // ===================== STEP 2: SEGMENT VESSELS =====================

    /**
     * For each Diff_i, apply threshold to produce binary Mask_i (8-bit: 0/255).
     * Optionally applies a 3D median filter before thresholding.
     * Saves Mask_i.tif into DIR_COMPUTED.
     */
    static void step2_segmentVessels(int nImages) {
        for (int i = 0; i < nImages; i++) {
            String diffPath = DIR_COMPUTED + File.separator + "Diff_" + i + ".tif";
            ImagePlus diff = IJ.openImage(diffPath);
            if (diff == null) {
                System.out.println("  ERROR: Cannot open " + diffPath + ", skipping.");
                continue;
            }
            System.out.println("  Segmenting Diff_" + i + " with threshold=" + VESSEL_THRESHOLD + "...");

            // Optional 3D median filter
            if (DO_MEDIAN_FILTER) {
                System.out.println("    Applying 3D median filter (radius=" + MEDIAN_RADIUS + ")...");
                IJ.run(diff, "Median 3D...", "x=" + MEDIAN_RADIUS + " y=" + MEDIAN_RADIUS + " z=" + MEDIAN_RADIUS);
            }

            // Threshold: pixels above VESSEL_THRESHOLD -> 255, else -> 0
            ImagePlus mask = thresholdStack(diff, VESSEL_THRESHOLD,WORKING_AREA);
            mask.setTitle("Mask_" + i);

            // Save
            String outPath = DIR_COMPUTED + File.separator + "Mask_" + i + ".tif";
            IJ.saveAsTiff(mask, outPath);
            System.out.println("  Saved: " + outPath);

            diff.close();
            mask.close();
        }
        System.out.println("  Step 2 done.\n");
    }

    /**
     * Threshold a 32-bit stack: pixels >= threshold -> 255, else -> 0.
     * Returns an 8-bit ImagePlus.
     */
    static ImagePlus thresholdStack(ImagePlus imp, double threshold, double[] workingArea) {
        int w = imp.getWidth();
        int h = imp.getHeight();
        int nSlices = imp.getNSlices();
        ImageStack stackIn = imp.getStack();
        ImageStack stackOut = new ImageStack(w, h);

        int xMin = (int) (workingArea[0] * w);
        int xMax = (int) (workingArea[1] * w);
        int yMin = (int) (workingArea[2] * h);
        int yMax = (int) (workingArea[3] * h);
        int zMin = (int) (workingArea[4] * nSlices);
        int zMax = (int) (workingArea[5] * nSlices);
        for (int z = Math.max(1, zMin); z <= Math.min(nSlices, zMax); z++) {
            float[] pix = (float[]) stackIn.getProcessor(z).getPixels();
            byte[] out = new byte[w * h];

            //Change this to only threshold within the working area
            for (int y = yMin; y < yMax; y++) {
                for (int x = xMin; x < xMax; x++) {
                    int j = y * w + x;
                    out[j] = (pix[j] >= threshold) ? (byte) 255 : 0;
                }
            }
            stackOut.addSlice("", new ByteProcessor(w, h, out, null));
        }
        return new ImagePlus("Mask", stackOut);
    }


    // ===================== STEP 3: TEMPORAL LABELING =====================

    /**
     * Create a 3D 8-bit image where each voxel value encodes the time of first
     * appearance: 1 = appeared in Mask_0, 2 = appeared in Mask_1, etc.
     * Background stays 0. Saves TemporalLabel.tif into DIR_COMPUTED.
     */
    static void step3_temporalLabeling(int nImages) {
        // Load first mask to get dimensions
        ImagePlus mask0 = IJ.openImage(DIR_COMPUTED + File.separator + "Mask_0.tif");
        if (mask0 == null) {
            System.out.println("  ERROR: Cannot open Mask_0.tif");
            return;
        }
        int w = mask0.getWidth();
        int h = mask0.getHeight();
        int nSlices = mask0.getNSlices();
        mask0.close();

        // Create result stack (8-bit, initialized to 0)
        ImageStack stackLabel = new ImageStack(w, h);
        byte[][] labelData = new byte[nSlices][w * h];
        for (int z = 0; z < nSlices; z++) {
            stackLabel.addSlice("", new ByteProcessor(w, h, labelData[z], null));
        }

        // Iterate from LAST mask to FIRST: later values get overwritten by earlier ones
        // This ensures each voxel gets the label of its FIRST appearance
        for (int i = nImages - 1; i >= 0; i--) {
            String maskPath = DIR_COMPUTED + File.separator + "Mask_" + i + ".tif";
            ImagePlus mask = IJ.openImage(maskPath);
            if (mask == null) {
                System.out.println("  ERROR: Cannot open " + maskPath + ", skipping.");
                continue;
            }
            System.out.println("  Applying Mask_" + i + " with label " + (i + 1) + "...");

            ImageStack stackMask = mask.getStack();
            for (int z = 1; z <= nSlices; z++) {
                byte[] pixMask = (byte[]) stackMask.getProcessor(z).getPixels();
                byte[] pixLabel = labelData[z - 1];
                for (int j = 0; j < pixMask.length; j++) {
                    if ((pixMask[j] & 0xFF) == 255) {
                        pixLabel[j] = (byte) (i + 1);  // Label: 1, 2, 3, 4
                    }
                }
            }
            mask.close();
        }

        ImagePlus impLabel = new ImagePlus("TemporalLabel", stackLabel);
        String outPath = DIR_COMPUTED + File.separator + "TemporalLabel.tif";
        IJ.saveAsTiff(impLabel, outPath);
        System.out.println("  Saved: " + outPath);
        impLabel.close();

        System.out.println("  Step 3 done.\n");
    }


    // ===================== STEP 4: COLORMAP AND OVERLAY =====================

    /**
     * Apply Fire LUT to TemporalLabel with display range [-2, 5],
     * convert to RGB, divide R/G/B by 2, then blend with reference image
     * (gray, 8-bit, also divided by 2). Saves FinalOverlay.tif into DIR_COMPUTED.
     */
    static void step4_colormapAndOverlay() {
        // Load temporal label
        ImagePlus impLabel = IJ.openImage(DIR_COMPUTED + File.separator + "TemporalLabel.tif");
        if (impLabel == null) {
            System.out.println("  ERROR: Cannot open TemporalLabel.tif");
            return;
        }

        // Apply Fire LUT with display range [-2, 5]
        IJ.run(impLabel, "Fire", "");
        impLabel.setDisplayRange(-2, 5);
        System.out.println("  Applied Fire LUT with range [-2, 5]");

        // Convert to RGB (flattens the LUT into pixel values)
        IJ.run(impLabel, "RGB Color", "");
        System.out.println("  Converted to RGB");

        // Load reference image and convert to 8-bit gray
        ImagePlus impRef = IJ.openImage(DIR_RAW + File.separator + REF_NAME);
        if (impRef == null) {
            System.out.println("  ERROR: Cannot open reference image");
            impLabel.close();
            return;
        }
        IJ.run(impRef, "8-bit", "");
        System.out.println("  Loaded reference as 8-bit gray");

        // Build the final overlay: for each voxel, R_final = R_fire/2 + gray/2, same for G, B
        int w = impLabel.getWidth();
        int h = impLabel.getHeight();
        int nSlices = impLabel.getNSlices();
        ImageStack stackRGB = impLabel.getStack();
        ImageStack stackRef = impRef.getStack();
        ImageStack stackOut = new ImageStack(w, h);

        for (int z = 1; z <= nSlices; z++) {
            int[] pixRGB = (int[]) stackRGB.getProcessor(z).getPixels();
            byte[] pixGray = (byte[]) stackRef.getProcessor(z).getPixels();
            int[] pixOut = new int[w * h];

            for (int j = 0; j < pixRGB.length; j++) {
                int rgb = pixRGB[j];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = pixGray[j] & 0xFF;

                // Divide fire colors by 2, divide gray by 2, add together
                int rOut = Math.min(255, (r / 2) + (gray / 2));
                int gOut = Math.min(255, (g / 2) + (gray / 2));
                int bOut = Math.min(255, (b / 2) + (gray / 2));

                pixOut[j] = (rOut << 16) | (gOut << 8) | bOut;
            }
            stackOut.addSlice("", new ij.process.ColorProcessor(w, h, pixOut));
        }

        ImagePlus impFinal = new ImagePlus("FinalOverlay", stackOut);
        String outPath = DIR_COMPUTED + File.separator + "FinalOverlay.tif";
        IJ.saveAsTiff(impFinal, outPath);
        System.out.println("  Saved: " + outPath);

        impLabel.close();
        impRef.close();
        impFinal.close();

        System.out.println("  Step 4 done.\n");
    }
}

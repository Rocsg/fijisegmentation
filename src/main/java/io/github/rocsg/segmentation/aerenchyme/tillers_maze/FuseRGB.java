package io.github.rocsg.segmentation.aerenchyme.tillers_maze;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;

import java.io.File;

/**
 * Fuse registered 8-bit stacks into RGB composites.
 * Part 1: all 2-channel combinations (i,j) with 0<=i<j<=4  ->  R=i, G=j, B=0
 * Part 2: three 3-channel combinations (0,1,2), (1,2,3), (2,3,4)  ->  R, G, B
 */
public class FuseRGB {

    static final String DIR = "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/Registered_manual";

    public static void main(String[] args) {

        // ── Part 1 : all pairs (i, j) with 0 <= i < j <= 4 ──
        System.out.println("========== PART 1 : 2-channel RGB fusions (R=i, G=j, B=0) ==========");
        int pairCount = 0;
        for (int i = 0; i <= 4; i++) {
            for (int j = i + 1; j <= 4; j++) {
                pairCount++;
                if(pairCount<11) continue; // --- IGNORE : skip first 2 pairs for testing ---
                if(pairCount>=11) return; // --- IGNORE : skip first 2 pairs for testing ---
                System.out.println("\n--- Pair " + pairCount + "/10 : indices (" + i + ", " + j + ") ---");
                fuseAndSave2(i, j);
                flushMemory();
            }
        }
        System.out.println("\n========== PART 1 DONE : " + pairCount + " pair(s) processed ==========\n");
        // ── Part 2 : three 3-channel combinations ──
        System.out.println("========== PART 2 : 3-channel RGB fusions (R, G, B) ==========");
        int[][] triplets = { {0, 1, 2}, {1, 2, 3}, {2, 3, 4} };
        for (int t = 0; t < triplets.length-3; t++) {
            int[] tri = triplets[t];
            System.out.println("\n--- Triplet " + (t + 1) + "/" + triplets.length
                    + " : indices (" + tri[0] + ", " + tri[1] + ", " + tri[2] + ") ---");
            fuseAndSave3(tri[0], tri[1], tri[2]);
            flushMemory();
        }
        System.out.println("\n========== PART 2 DONE : " + triplets.length + " triplet(s) processed ==========");

        // ── Part 3 : difference between successive images, centered on 128 ──
        System.out.println("\n========== PART 3 : successive differences (centered on 128) ==========");
        for (int d = 0; d < 4; d++) {
            System.out.println("\n--- Diff " + (d + 1) + "/4 : image " + d + " - image " + (d + 1) + " ---");
            diffAndSave(d, d + 1);
            flushMemory();
        }
        System.out.println("\n========== PART 3 DONE : 4 difference(s) processed ==========");
    }

    // ── 2-channel fusion : R = index1, G = index2, B = 0 ──────────────────────
    static void fuseAndSave2(int index1, int index2) {
        String path1 = new File(DIR, String.format("%03d.tif", index1)).getAbsolutePath();
        String path2 = new File(DIR, String.format("%03d.tif", index2)).getAbsolutePath();

        System.out.println("  Opening R : " + path1);
        ImagePlus imp1 = IJ.openImage(path1);
        if (imp1 == null) throw new RuntimeException("Cannot open : " + path1);

        System.out.println("  Opening G : " + path2);
        ImagePlus imp2 = IJ.openImage(path2);
        if (imp2 == null) { imp1.close(); throw new RuntimeException("Cannot open : " + path2); }

        checkDimensions(imp1, imp2);
        int w = imp1.getWidth(), h = imp1.getHeight(), nSlices = imp1.getNSlices();

        ImageStack stack1 = imp1.getStack();
        ImageStack stack2 = imp2.getStack();
        ImageStack rgbStack = new ImageStack(w, h);

        for (int z = 1; z <= nSlices; z++) {
            if (z % 100 == 0) System.out.println("  Slice " + z + " / " + nSlices);
            byte[] red   = (byte[]) stack1.getProcessor(z).getPixels();
            byte[] green = (byte[]) stack2.getProcessor(z).getPixels();
            int[] rgb = new int[w * h];
            for (int p = 0; p < rgb.length; p++) {
                int r = red[p]   & 0xFF;
                int g = green[p] & 0xFF;
                rgb[p] = (r << 16) | (g << 8);
            }
            rgbStack.addSlice("", new ColorProcessor(w, h, rgb));
        }

        ImagePlus result = new ImagePlus("fuse_" + index1 + "_" + index2, rgbStack);
        result.setCalibration(imp1.getCalibration());

        String outPath = new File(DIR, "fuse_" + index1 + "_" + index2 + ".tif").getAbsolutePath();
        IJ.saveAsTiff(result, outPath);
        System.out.println("  Saved : " + outPath + " (" + w + "x" + h + "x" + nSlices + " RGB)");

        result.close(); imp1.close(); imp2.close();
    }

    // ── 3-channel fusion : R = indexR, G = indexG, B = indexB ─────────────────
    static void fuseAndSave3(int indexR, int indexG, int indexB) {
        String pathR = new File(DIR, String.format("%03d.tif", indexR)).getAbsolutePath();
        String pathG = new File(DIR, String.format("%03d.tif", indexG)).getAbsolutePath();
        String pathB = new File(DIR, String.format("%03d.tif", indexB)).getAbsolutePath();

        System.out.println("  Opening R : " + pathR);
        ImagePlus impR = IJ.openImage(pathR);
        if (impR == null) throw new RuntimeException("Cannot open : " + pathR);

        System.out.println("  Opening G : " + pathG);
        ImagePlus impG = IJ.openImage(pathG);
        if (impG == null) { impR.close(); throw new RuntimeException("Cannot open : " + pathG); }

        System.out.println("  Opening B : " + pathB);
        ImagePlus impB = IJ.openImage(pathB);
        if (impB == null) { impR.close(); impG.close(); throw new RuntimeException("Cannot open : " + pathB); }

        checkDimensions(impR, impG);
        checkDimensions(impR, impB);
        int w = impR.getWidth(), h = impR.getHeight(), nSlices = impR.getNSlices();

        ImageStack stackR = impR.getStack();
        ImageStack stackG = impG.getStack();
        ImageStack stackB = impB.getStack();
        ImageStack rgbStack = new ImageStack(w, h);

        for (int z = 1; z <= nSlices; z++) {
            if (z % 100 == 0) System.out.println("  Slice " + z + " / " + nSlices);
            byte[] red   = (byte[]) stackR.getProcessor(z).getPixels();
            byte[] green = (byte[]) stackG.getProcessor(z).getPixels();
            byte[] blue  = (byte[]) stackB.getProcessor(z).getPixels();
            int[] rgb = new int[w * h];
            for (int p = 0; p < rgb.length; p++) {
                int r = red[p]   & 0xFF;
                int g = green[p] & 0xFF;
                int b = blue[p]  & 0xFF;
                rgb[p] = (r << 16) | (g << 8) | b;
            }
            rgbStack.addSlice("", new ColorProcessor(w, h, rgb));
        }

        ImagePlus result = new ImagePlus("fuse_" + indexR + "_" + indexG + "_" + indexB, rgbStack);
        result.setCalibration(impR.getCalibration());

        String outPath = new File(DIR, "fuse_" + indexR + "_" + indexG + "_" + indexB + ".tif").getAbsolutePath();
        IJ.saveAsTiff(result, outPath);
        System.out.println("  Saved : " + outPath + " (" + w + "x" + h + "x" + nSlices + " RGB)");

        result.close(); impR.close(); impG.close(); impB.close();
    }

    // ── Successive difference : diff = (imgA - imgB) + 128, clamped to [0,255] ─
    static void diffAndSave(int indexA, int indexB) {
        String pathA = new File(DIR, String.format("%03d.tif", indexA)).getAbsolutePath();
        String pathB = new File(DIR, String.format("%03d.tif", indexB)).getAbsolutePath();

        System.out.println("  Opening A : " + pathA);
        ImagePlus impA = IJ.openImage(pathA);
        if (impA == null) throw new RuntimeException("Cannot open : " + pathA);

        System.out.println("  Opening B : " + pathB);
        ImagePlus impB = IJ.openImage(pathB);
        if (impB == null) { impA.close(); throw new RuntimeException("Cannot open : " + pathB); }

        checkDimensions(impA, impB);
        int w = impA.getWidth(), h = impA.getHeight(), nSlices = impA.getNSlices();

        ImageStack stackA = impA.getStack();
        ImageStack stackB = impB.getStack();
        ImageStack diffStack = new ImageStack(w, h);

        for (int z = 1; z <= nSlices; z++) {
            if (z % 100 == 0) System.out.println("  Slice " + z + " / " + nSlices);
            byte[] pixA = (byte[]) stackA.getProcessor(z).getPixels();
            byte[] pixB = (byte[]) stackB.getProcessor(z).getPixels();
            byte[] diff = new byte[w * h];
            for (int p = 0; p < diff.length; p++) {
                int val = (pixA[p] & 0xFF) - (pixB[p] & 0xFF) + 128;
                if (val < 0)   val = 0;
                if (val > 255) val = 255;
                diff[p] = (byte) val;
            }
            diffStack.addSlice("", new ByteProcessor(w, h, diff, null));
        }

        ImagePlus result = new ImagePlus("diff_" + indexA + "_" + indexB, diffStack);
        result.setCalibration(impA.getCalibration());

        String outPath = new File(DIR, "diff_" + indexA + "_" + indexB + ".tif").getAbsolutePath();
        IJ.saveAsTiff(result, outPath);
        System.out.println("  Saved : " + outPath + " (" + w + "x" + h + "x" + nSlices + " 8-bit, centered on 128)");

        result.close(); impA.close(); impB.close();
    }

    // ── Memory flush helper ──────────────────────────────────────────────────
    private static void flushMemory() {
        System.gc();
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        System.gc();
        Runtime rt = Runtime.getRuntime();
        long usedMB  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long freeMB  = rt.freeMemory() / (1024 * 1024);
        long totalMB = rt.totalMemory() / (1024 * 1024);
        long maxMB   = rt.maxMemory()   / (1024 * 1024);
        System.out.println("  [GC] Memory — used: " + usedMB + " MB | free: " + freeMB
                + " MB | total: " + totalMB + " MB | max: " + maxMB + " MB");
    }

    // ── Dimension check helper ────────────────────────────────────────────────
    private static void checkDimensions(ImagePlus a, ImagePlus b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()
                || a.getNSlices() != b.getNSlices()) {
            String msg = "Dimension mismatch : "
                    + a.getWidth() + "x" + a.getHeight() + "x" + a.getNSlices() + " vs "
                    + b.getWidth() + "x" + b.getHeight() + "x" + b.getNSlices();
            a.close(); b.close();
            throw new IllegalArgumentException(msg);
        }
    }
}

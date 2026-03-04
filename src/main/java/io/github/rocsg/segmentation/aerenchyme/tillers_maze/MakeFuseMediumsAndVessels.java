package io.github.rocsg.segmentation.aerenchyme.tillers_maze;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileInfo;
import ij.io.TiffDecoder;
import ij.measure.Calibration;
import ij.plugin.FileInfoVirtualStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Fuse a segmentation image (Fire LUT, range [0,4]) and a vessel image
 * into a combined RGB composite.
 * <p>
 * Uses virtual TIFF stacks for input (near-zero RAM) and processes slices
 * in blocks of {@link #BLOCK_SIZE} with aggressive memory cleanup between
 * blocks.  Each block is saved to a temp TIFF; a final merge pass produces
 * the single output file.
 * <p>
 * imgSeg : display range [0, 4], Fire LUT → RGB, each channel × FACTOR_SEG<br>
 * imgVes : RGB, each channel × FACTOR_VES<br>
 * Result : per-channel sum, clamped [0, 255], merged RGB.
 */
public class MakeFuseMediumsAndVessels {

    /** Set to true for testing paths, false for production paths. */
    static final boolean TESTING = false;

    // ── Testing paths ──
    static final String PATH_VES_TEST = "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/Test_AllFuse/FuseVessels.tif";
    static final String PATH_SEG_TEST = "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/Test_AllFuse/Seg.tif";

    // ── Production paths ──
    static final String PATH_VES_PROD = "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/Composed/fuse_0_1_2.tif";
    static final String PATH_SEG_PROD = "/media/rfernandez/Crucial X9/Tillers-Maze_Exp01/ML_test_1/000_Simple Segmentation.tiff";

    static final double FACTOR_SEG = 0.53;
    static final double FACTOR_VES = 1.55;

    /** Number of slices processed per block. Tune vs. available RAM. */
    static final int BLOCK_SIZE = 200;

    /**
     * Per-label colour overrides.  Key = raw label value (0–4),
     * value = {R, G, B} in [0, 255] <b>before</b> FACTOR_SEG scaling.
     * <p>Example: replace label 3 (yellow in Fire) with grey:</p>
     * <pre>LABEL_COLOR_OVERRIDES.put(3, new int[]{122, 122, 122});</pre>
     * Leave empty to use the Fire LUT for every label.
     */
    static final Map<Integer, int[]> LABEL_COLOR_OVERRIDES = new HashMap<>();
    static {
        // ──── Tweak individual label colours here ────
        LABEL_COLOR_OVERRIDES.put(3, new int[]{112, 92, 32});   // label 3 → grey instead of yellow
        // LABEL_COLOR_OVERRIDES.put(0, new int[]{0, 0, 0});      // example: keep black
        // LABEL_COLOR_OVERRIDES.put(1, new int[]{255, 0, 0});     // example: pure red
        // LABEL_COLOR_OVERRIDES.put(2, new int[]{0, 255, 0});     // example: pure green
        // LABEL_COLOR_OVERRIDES.put(4, new int[]{0, 0, 255});     // example: pure blue
    }

    // =========================================================================
    public static void main(String[] args) {
        ImageJ ij = new ImageJ();

        String pathSeg = TESTING ? PATH_SEG_TEST : PATH_SEG_PROD;
        String pathVes = TESTING ? PATH_VES_TEST : PATH_VES_PROD;

        // ── 1. Build Fire-LUT lookup tables ──────────────────────────────────
        // Pre-compute, for every raw 8-bit value v, the Fire-LUT colour after
        // display-range [0,4] mapping, already scaled by FACTOR_SEG.
        System.out.println("Building Fire LUT lookup tables (display range [0, 4])...");
        int[] fireLutR = new int[256], fireLutG = new int[256], fireLutB = new int[256];
        buildFireLUT(fireLutR, fireLutG, fireLutB);

        // segScaled*[v] = round(colour[v] * FACTOR_SEG)
        // where colour comes from LABEL_COLOR_OVERRIDES if present, else Fire LUT.
        int[] segScaledR = new int[256];
        int[] segScaledG = new int[256];
        int[] segScaledB = new int[256];
        for (int v = 0; v < 256; v++) {
            int[] override = LABEL_COLOR_OVERRIDES.get(v);
            if (override != null) {
                segScaledR[v] = (int) Math.round(override[0] * FACTOR_SEG);
                segScaledG[v] = (int) Math.round(override[1] * FACTOR_SEG);
                segScaledB[v] = (int) Math.round(override[2] * FACTOR_SEG);
            } else {
                int idx = Math.min(255, (int) Math.round(v * 255.0 / 4.0));
                segScaledR[v] = (int) Math.round(fireLutR[idx] * FACTOR_SEG);
                segScaledG[v] = (int) Math.round(fireLutG[idx] * FACTOR_SEG);
                segScaledB[v] = (int) Math.round(fireLutB[idx] * FACTOR_SEG);
            }
        }
        // vesScaled[v] = round(v * FACTOR_VES)   (same for R, G, B)
        int[] vesScaled = new int[256];
        for (int v = 0; v < 256; v++) {
            vesScaled[v] = (int) Math.round(v * FACTOR_VES);
        }
        // segRGBScaled[v] = round(v * FACTOR_SEG)  (for the case imgSeg is already RGB)
        int[] segRGBScaled = new int[256];
        for (int v = 0; v < 256; v++) {
            segRGBScaled[v] = (int) Math.round(v * FACTOR_SEG);
        }

        // ── 2. Open both images as virtual TIFF stacks ──────────────────────
        System.out.println("Opening imgSeg as virtual stack: " + pathSeg);
        ImagePlus imgSeg = openAsVirtualStack(pathSeg);
        int segType = imgSeg.getType();
        System.out.println("  imgSeg: " + imgSeg.getWidth() + "x" + imgSeg.getHeight()
                + "x" + imgSeg.getNSlices() + "  type=" + typeStr(segType));

        System.out.println("Opening imgVes as virtual stack: " + pathVes);
        ImagePlus imgVes = openAsVirtualStack(pathVes);
        int vesType = imgVes.getType();
        System.out.println("  imgVes: " + imgVes.getWidth() + "x" + imgVes.getHeight()
                + "x" + imgVes.getNSlices() + "  type=" + typeStr(vesType));

        printMemory("After opening virtual stacks");

        // ── 3. Dimension check ───────────────────────────────────────────────
        int w = imgSeg.getWidth(), h = imgSeg.getHeight(), nSlices = imgSeg.getNSlices();
        if (imgVes.getWidth() != w || imgVes.getHeight() != h || imgVes.getNSlices() != nSlices) {
            throw new IllegalArgumentException("Dimension mismatch: imgSeg="
                    + w + "x" + h + "x" + nSlices + " vs imgVes="
                    + imgVes.getWidth() + "x" + imgVes.getHeight() + "x" + imgVes.getNSlices());
        }

        int nPixels = w * h;
        Calibration cal = imgSeg.getCalibration().copy();

        // ── 4. Block processing ──────────────────────────────────────────────
        int nBlocks = (nSlices + BLOCK_SIZE - 1) / BLOCK_SIZE;
        System.out.println("\nProcessing " + nSlices + " slices (" + w + "x" + h
                + ") in " + nBlocks + " blocks of up to " + BLOCK_SIZE + " slices...\n");

        String outDir = new File(pathSeg).getParent();
        String blockDirPath = new File(outDir, "FuseMediumsAndVessels_blocks").getAbsolutePath();
        new File(blockDirPath).mkdirs();

        ImageStack stackSeg = imgSeg.getStack();  // virtual – no RAM
        ImageStack stackVes = imgVes.getStack();   // virtual – no RAM

        for (int b = 0; b < nBlocks; b++) {
            int startZ = b * BLOCK_SIZE + 1;   // 1-based inclusive
            int endZ   = Math.min(startZ + BLOCK_SIZE - 1, nSlices);
            int blockSliceCount = endZ - startZ + 1;

            System.out.println("── Block " + (b + 1) + "/" + nBlocks
                    + " : slices " + startZ + "–" + endZ
                    + " (" + blockSliceCount + " slices) ──");
            printMemory("  Start");

            ImageStack blockStack = new ImageStack(w, h);

            for (int z = startZ; z <= endZ; z++) {
                // Virtual stacks load each slice from disk on demand
                ImageProcessor ipSeg = stackSeg.getProcessor(z);
                ImageProcessor ipVes = stackVes.getProcessor(z);

                int[] resPix = new int[nPixels];
                fuseSlice(ipSeg, ipVes, resPix, nPixels,
                          segType, vesType,
                          segScaledR, segScaledG, segScaledB,
                          segRGBScaled, vesScaled);

                blockStack.addSlice("", new ColorProcessor(w, h, resPix));

                // Drop references → GC can reclaim virtual-loaded data
                ipSeg = null;
                ipVes = null;
                resPix = null;

                if ((z - startZ + 1) % 50 == 0)
                    System.out.println("    slice " + z + "/" + nSlices);
            }

            // Save block to disk
            ImagePlus blockImg = new ImagePlus("block", blockStack);
            blockImg.setCalibration(cal);
            String blockPath = new File(blockDirPath,
                    String.format("block_%04d.tif", b)).getAbsolutePath();
            IJ.saveAsTiff(blockImg, blockPath);
            System.out.println("  Saved: " + blockPath);

            // Aggressive cleanup
            blockImg.close();
            blockImg = null;
            blockStack = null;
            flushMemory();
            printMemory("  After flush");
        }

        // Close virtual stacks
        imgSeg.close();  imgSeg = null;  stackSeg = null;
        imgVes.close();  imgVes = null;  stackVes = null;
        flushMemory();

        // ── 5. Merge blocks into a single TIFF ──────────────────────────────
        System.out.println("\n── Merging " + nBlocks + " blocks into final output... ──");
        printMemory("Before merge");

        try {
            ImageStack finalStack = new ImageStack(w, h);

            for (int b = 0; b < nBlocks; b++) {
                String blockPath = new File(blockDirPath,
                        String.format("block_%04d.tif", b)).getAbsolutePath();
                System.out.println("  Loading block " + (b + 1) + "/" + nBlocks);
                ImagePlus blockImg = IJ.openImage(blockPath);
                if (blockImg == null)
                    throw new RuntimeException("Cannot reopen block: " + blockPath);

                ImageStack bs = blockImg.getStack();
                for (int z = 1; z <= bs.getSize(); z++) {
                    // Transfer processor reference (no copy) – pixel arrays survive
                    finalStack.addSlice("", bs.getProcessor(z));
                }
                // Don't close blockImg: finalStack holds refs to pixel arrays.
                blockImg = null;
                bs = null;
            }

            ImagePlus finalImg = new ImagePlus("FuseMediumsAndVessels", finalStack);
            finalImg.setCalibration(cal);

            String finalPath = new File(outDir, "FuseMediumsAndVessels.tif").getAbsolutePath();
            System.out.println("Saving final result: " + finalPath);
            IJ.saveAsTiff(finalImg, finalPath);
            System.out.println("Saved: " + finalPath
                    + " (" + w + "x" + h + "x" + nSlices + " RGB)");

            finalImg.close();
            finalImg = null;
            finalStack = null;
            flushMemory();

            // Clean up block files
            System.out.println("Cleaning up temporary block files...");
            for (int b = 0; b < nBlocks; b++) {
                new File(blockDirPath, String.format("block_%04d.tif", b)).delete();
            }
            new File(blockDirPath).delete();
            System.out.println("Cleanup done.");

        } catch (OutOfMemoryError oom) {
            System.err.println("WARNING: not enough memory to merge all blocks "
                    + "into a single file.");
            System.err.println("Block files are preserved at: " + blockDirPath);
            System.err.println("Open them in Fiji via  File > Import > Image Sequence.");
            flushMemory();
        }

        System.out.println("\nDone.");
    }

    // =====================================================================
    //  Per-slice fusion
    // =====================================================================
    /**
     * Fuse one slice from imgSeg and imgVes into resPix.
     * All lookup arrays are pre-computed so the inner loop is pure integer work.
     */
    private static void fuseSlice(ImageProcessor ipSeg, ImageProcessor ipVes,
                                   int[] resPix, int nPixels,
                                   int segType, int vesType,
                                   int[] segScaledR, int[] segScaledG, int[] segScaledB,
                                   int[] segRGBScaled, int[] vesScaled) {

        // ── Extract pixel arrays once ──
        boolean segIsRGB = (segType == ImagePlus.COLOR_RGB);
        int[]   segInt   = segIsRGB ? (int[])  ipSeg.getPixels() : null;
        byte[]  segByte  = (!segIsRGB && segType == ImagePlus.GRAY8)  ? (byte[])  ipSeg.getPixels() : null;
        short[] segShort = (!segIsRGB && segType == ImagePlus.GRAY16) ? (short[]) ipSeg.getPixels() : null;

        boolean vesIsRGB = (vesType == ImagePlus.COLOR_RGB);
        int[]   vesInt   = vesIsRGB ? (int[])  ipVes.getPixels() : null;
        byte[]  vesByte  = (!vesIsRGB && vesType == ImagePlus.GRAY8)  ? (byte[])  ipVes.getPixels() : null;
        short[] vesShort = (!vesIsRGB && vesType == ImagePlus.GRAY16) ? (short[]) ipVes.getPixels() : null;

        for (int p = 0; p < nPixels; p++) {
            // ── imgSeg contribution ──
            int sR, sG, sB;
            if (segIsRGB) {
                int px = segInt[p];
                sR = segRGBScaled[(px >> 16) & 0xFF];
                sG = segRGBScaled[(px >>  8) & 0xFF];
                sB = segRGBScaled[ px        & 0xFF];
            } else if (segByte != null) {
                int raw = segByte[p] & 0xFF;
                sR = segScaledR[raw];
                sG = segScaledG[raw];
                sB = segScaledB[raw];
            } else { // 16-bit
                int raw = Math.min(255, segShort[p] & 0xFFFF);
                sR = segScaledR[raw];
                sG = segScaledG[raw];
                sB = segScaledB[raw];
            }

            // ── imgVes contribution ──
            int vR, vG, vB;
            if (vesIsRGB) {
                int px = vesInt[p];
                vR = vesScaled[(px >> 16) & 0xFF];
                vG = vesScaled[(px >>  8) & 0xFF];
                vB = vesScaled[ px        & 0xFF];
            } else if (vesByte != null) {
                int val = vesByte[p] & 0xFF;
                vR = vG = vB = vesScaled[val];
            } else { // 16-bit
                int val = Math.min(255, vesShort[p] & 0xFFFF);
                vR = vG = vB = vesScaled[val];
            }

            // ── Sum + clamp ──
            resPix[p] = (clamp(sR + vR) << 16)
                      | (clamp(sG + vG) <<  8)
                      |  clamp(sB + vB);
        }
    }

    // =====================================================================
    //  Virtual TIFF stack opening
    // =====================================================================
    /** Open a TIFF file as a virtual stack (slices loaded on demand, ~0 RAM). */
    private static ImagePlus openAsVirtualStack(String path) {
        File f = new File(path);
        String dir  = f.getParent() + File.separator;
        String name = f.getName();
        try {
            TiffDecoder td = new TiffDecoder(dir, name);
            FileInfo[] fi = td.getTiffInfo();
            if (fi == null || fi.length == 0)
                throw new RuntimeException("No TIFF info found in: " + path);

            System.out.println("  TiffDecoder: " + fi.length + " FileInfo entries, fi[0].nImages=" + fi[0].nImages);

            FileInfoVirtualStack vs;
            if (fi.length == 1 && fi[0].nImages > 1) {
                // Multi-page TIFF stored as a single IFD with nImages > 1
                // The single-FileInfo constructor handles nImages correctly
                vs = new FileInfoVirtualStack(fi[0], false);
            } else {
                // One IFD per page → array constructor
                vs = new FileInfoVirtualStack(fi);
            }

            ImagePlus imp = new ImagePlus(name, vs);
            System.out.println("  Virtual stack size: " + vs.getSize()
                    + "  |  ImagePlus nSlices: " + imp.getNSlices());
            return imp;
        } catch (IOException e) {
            throw new RuntimeException("Error reading TIFF header: " + path, e);
        }
    }

    // =====================================================================
    //  Fire LUT builder
    // =====================================================================
    /**
     * Extract the Fire LUT from ImageJ into three int[256] arrays (R, G, B).
     * Works headlessly as long as {@code new ImageJ()} has been called.
     */
    private static void buildFireLUT(int[] r, int[] g, int[] b) {
        // Create a tiny temp image, apply "Fire" LUT, extract colour model
        ByteProcessor bp = new ByteProcessor(256, 1);
        for (int i = 0; i < 256; i++) bp.set(i, 0, i);
        ImagePlus tmp = new ImagePlus("tmp", bp);
        IJ.run(tmp, "Fire", "");
        IndexColorModel icm = (IndexColorModel) tmp.getProcessor().getColorModel();
        for (int i = 0; i < 256; i++) {
            r[i] = icm.getRed(i);
            g[i] = icm.getGreen(i);
            b[i] = icm.getBlue(i);
        }
        tmp.close();
    }

    // =====================================================================
    //  Utilities
    // =====================================================================
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /** Null-friendly flush: double-GC with a short pause. */
    private static void flushMemory() {
        System.gc();
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        System.gc();
    }

    private static void printMemory(String label) {
        Runtime rt = Runtime.getRuntime();
        long usedMB  = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long totalMB = rt.totalMemory() >> 20;
        long maxMB   = rt.maxMemory()   >> 20;
        System.out.println(label + "  [mem] used=" + usedMB
                + " MB | total=" + totalMB + " MB | max=" + maxMB + " MB");
    }

    private static String typeStr(int type) {
        switch (type) {
            case ImagePlus.GRAY8:     return "GRAY8";
            case ImagePlus.GRAY16:    return "GRAY16";
            case ImagePlus.GRAY32:    return "GRAY32";
            case ImagePlus.COLOR_RGB: return "COLOR_RGB";
            case ImagePlus.COLOR_256: return "COLOR_256";
            default:                  return "UNKNOWN(" + type + ")";
        }
    }
}

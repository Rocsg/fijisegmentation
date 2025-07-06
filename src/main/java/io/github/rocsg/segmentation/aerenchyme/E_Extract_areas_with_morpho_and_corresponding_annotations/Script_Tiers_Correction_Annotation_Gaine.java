package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.process.ByteProcessor;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class Script_Tiers_Correction_Annotation_Gaine {
    private static final String BASE_PATH     = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_GAINE_DIR  = BASE_PATH + "/patches_256/maskleaf";
    private static final String OUT_DIR       = BASE_PATH + "/patches_256_tiers/maskleaf";
    private static final int    PATCH_SIZE    = 256;

    public static void main(String[] args) {
        File base = new File(IN_GAINE_DIR);
        for (File cubeDir : base.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File gaineFile : cubeDir.listFiles((d,n)->n.endsWith(".tif"))) {
                String code = gaineFile.getName().replaceFirst("\\.tif$", "");
                IJ.log("Processing gaine: " + cube + " / " + code);
                ImagePlus gaineImp = IJ.openImage(gaineFile.getAbsolutePath());
                if (gaineImp == null) continue;
                int nSlices = gaineImp.getNSlices();
                ImageStack outStack = new ImageStack(PATCH_SIZE, PATCH_SIZE);

                for (int z = 1; z <= nSlices; z++) {
                    gaineImp.setSlice(z);
                    ByteProcessor gp = (ByteProcessor) gaineImp.getProcessor().duplicate();
                    // Label connected components
                    int w = PATCH_SIZE, h = PATCH_SIZE;
                    int[][] labels = new int[h][w];
                    List<Integer> counts = new ArrayList<>(); counts.add(0);
                    List<Double> sumX = new ArrayList<>(); sumX.add(0.0);
                    List<Double> sumY = new ArrayList<>(); sumY.add(0.0);
                    int lbl = 0;
                    Deque<int[]> stack = new ArrayDeque<>();

                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if (gp.get(x,y) == 255 && labels[y][x] == 0) {
                                lbl++;
                                counts.add(0); sumX.add(0.0); sumY.add(0.0);
                                stack.clear(); stack.push(new int[]{x,y});
                                labels[y][x] = lbl;
                                while (!stack.isEmpty()) {
                                    int[] p = stack.pop();
                                    int px = p[0], py = p[1];
                                    sumX.set(lbl, sumX.get(lbl) + px);
                                    sumY.set(lbl, sumY.get(lbl) + py);
                                    counts.set(lbl, counts.get(lbl) + 1);
                                    int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
                                    for (int[] d : dirs) {
                                        int nx = px + d[0], ny = py + d[1];
                                        if (nx >= 0 && nx < w && ny >= 0 && ny < h
                                         && gp.get(nx,ny) == 255 && labels[ny][nx] == 0) {
                                            labels[ny][nx] = lbl;
                                            stack.push(new int[]{nx,ny});
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Select central component
                    double cx0 = w/2.0, cy0 = h/2.0, bestDist = Double.MAX_VALUE;
                    int bestLabel = 0;
                    for (int L = 1; L <= lbl; L++) {
                        double cx = sumX.get(L) / counts.get(L);
                        double cy = sumY.get(L) / counts.get(L);
                        double d2 = (cx - cx0)*(cx - cx0) + (cy - cy0)*(cy - cy0);
                        if (d2 < bestDist) {
                            bestDist = d2;
                            bestLabel = L;
                        }
                    }
                    // Build corrected mask
                    ByteProcessor corrected = new ByteProcessor(w, h);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if (labels[y][x] == bestLabel) corrected.set(x,y,255);
                        }
                    }
                    outStack.addSlice(gaineImp.getStack().getSliceLabel(z), corrected);
                }
                // Save corrected gaine stack
                File outCube = new File(OUT_DIR + "/" + cube);
                outCube.mkdirs();
                String outPath = new File(outCube, code + ".tif").getAbsolutePath();
                new FileSaver(new ImagePlus(code, outStack)).saveAsTiffStack(outPath);
                IJ.log("Saved corrected gaine: " + outPath);
            }
        }
        IJ.log("=== Gaine correction completed ===");
    }
}

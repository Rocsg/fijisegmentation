package io.github.rocsg.segmentation.aerenchyme.E_Extract_areas_with_morpho_and_corresponding_annotations;

import ij.IJ;
import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Script_Alter_04.java
 *
 * Prend en entrée les images binaires remplies (fill holes) situées dans:
 *   BASE_PATH + "/slice_stacks_segmented_tissue_fill_holes/raw"
 * Pour chaque image, conserve uniquement la composante connexe blanche (255)
 * dont le barycentre est le plus proche du centre de l'image.
 * Sauvegarde les masques filtrés dans:
 *   BASE_PATH + "/slice_stacks_segmented_tissue_final/raw"
 */
public class Script_Alter_04 {
    private static final String BASE_PATH  = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_DIR     = BASE_PATH + "/slice_stacks_segmented_tissue_fill_holes/raw";
    private static final String OUT_DIR    = BASE_PATH + "/slice_stacks_segmented_tissue_ALT4_single/raw";
    private static final int    PATCH_SIZE = 256;

    public static void main(String[] args) {
        File root = new File(IN_DIR);
        for (File cubeDir : root.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
//                if(!code.contains("Cube_05_L10_C1")) continue;
                File outObj = new File(OUT_DIR + "/" + cube + "/" + code);
                outObj.mkdirs();
                IJ.log("Processing central CC for cube=" + cube + " code=" + code);
                for (File file : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    String name = file.getName();
                    IJ.log("  Filtering " + name);
                    ImagePlus imp = IJ.openImage(file.getAbsolutePath());
                    if (imp == null) {
                        IJ.error("Cannot open file: " + file.getPath());
                        continue;
                    }
                    ByteProcessor bp = (ByteProcessor)imp.getProcessor();
                    int w = bp.getWidth(), h = bp.getHeight();
                    int[][] labels = new int[h][w];
                    List<Integer> counts = new ArrayList<>(); counts.add(0);
                    List<Double> sumX = new ArrayList<>(); sumX.add(0.0);
                    List<Double> sumY = new ArrayList<>(); sumY.add(0.0);
                    int lbl = 0;
                    Deque<int[]> stack = new ArrayDeque<>();
                    // Label connected components
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if (bp.get(x,y) == 255 && labels[y][x] == 0) {
                                lbl++;
                                counts.add(0);
                                sumX.add(0.0);
                                sumY.add(0.0);
                                stack.clear();
                                stack.push(new int[]{x,y});
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
                                                && bp.get(nx,ny) == 255
                                                && labels[ny][nx] == 0) {
                                            labels[ny][nx] = lbl;
                                            stack.push(new int[]{nx,ny});
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Compute center
                    double cx0 = w / 2.0, cy0 = h / 2.0;
                    double bestDist = Double.MAX_VALUE;
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
                    // Build final mask
                    ByteProcessor outBp = new ByteProcessor(w, h);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            if (labels[y][x] == bestLabel) {
                                outBp.set(x,y,255);
                            }
                        }
                    }
                    // Save
                    String outPath = new File(outObj, name).getAbsolutePath();
                    new FileSaver(new ImagePlus(name, outBp)).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Central component filtering completed ===");
    }
}

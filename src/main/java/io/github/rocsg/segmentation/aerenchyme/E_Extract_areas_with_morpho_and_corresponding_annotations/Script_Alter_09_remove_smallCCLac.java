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

/**
 * Script_Alter_09_remove_smallCCLac.java
 *
 * Supprime les composantes connexes blanches de petite surface (<=25 px)
 * dans les masques de lacunes situés dans:
 *   BASE_PATH + "/slice_stacks_segmented_tissue_lacuna/raw"
 * Sauve les masques nettoyés dans:
 *   BASE_PATH + "/slice_stacks_segmented_tissue_lacuna_cleaned/raw"
 */
public class Script_Alter_09_remove_smallCCLac {
    private static final String BASE_PATH       = "/Donnees/DD_CIRS626_DATA/Data_Cube_HTP";
    private static final String IN_LACUNA_DIR   = BASE_PATH + "/slice_stacks_segmented_tissue_ALT8_lacuna/raw";
    private static final String OUT_CLEANED_DIR = BASE_PATH + "/slice_stacks_segmented_tissue_ALT9_lacuna_cleaned/raw";
    private static final int    MIN_AREA        = 25;

    public static void main(String[] args) {
        File base = new File(IN_LACUNA_DIR);
        for (File cubeDir : base.listFiles(File::isDirectory)) {
            String cube = cubeDir.getName();
            for (File objDir : cubeDir.listFiles(File::isDirectory)) {
                String code = objDir.getName();
                //if(!code.contains("Cube_05_L10_C1")) continue;
                File outObj = new File(OUT_CLEANED_DIR + "/" + cube + "/" + code);
                outObj.mkdirs();
                IJ.log("Cleaning small CC in lacuna masks for cube="+cube+" code="+code);
                for (File file : objDir.listFiles((d,n)->n.endsWith(".tif"))) {
                    String name = file.getName();
                    IJ.log("  Processing " + name);
                    ImagePlus imp = IJ.openImage(file.getAbsolutePath());
                    if (imp == null) {
                        IJ.error("Cannot open: " + file.getPath());
                        continue;
                    }
                    int w = imp.getWidth(), h = imp.getHeight(), nSlices = 1;//imp.getNSlices();
                    ImageStack outStack = new ImageStack(w, h);
                    // For each slice
                    for (int z = 1; z <= nSlices; z++) {
//                        imp.setSlice(z);
                        ByteProcessor bp = (ByteProcessor) imp.getProcessor().duplicate();
                        int[][] labels = new int[h][w];
                        List<Integer> area = new ArrayList<>(); area.add(0); // index 0 unused
                        int lbl = 0;
                        Deque<int[]> stack = new ArrayDeque<>();
                        // Label and count area
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                if (bp.get(x,y) == 255 && labels[y][x] == 0) {
                                    lbl++; area.add(0);
                                    stack.clear(); stack.push(new int[]{x,y});
                                    labels[y][x] = lbl;
                                    while (!stack.isEmpty()) {
                                        int[] p = stack.pop();
                                        int px = p[0], py = p[1];
                                        area.set(lbl, area.get(lbl) + 1);
                                        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
                                        for (int[] d : dirs) {
                                            int nx = px + d[0], ny = py + d[1];
                                            if (nx>=0 && nx<w && ny>=0 && ny<h
                                              && bp.get(nx,ny)==255 && labels[ny][nx]==0) {
                                                labels[ny][nx] = lbl;
                                                stack.push(new int[]{nx,ny});
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Remove small components
                        ByteProcessor clean = new ByteProcessor(w, h);
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                int l = labels[y][x];
                                if (l>0 && area.get(l) > MIN_AREA) {
                                    clean.set(x,y,255);
                                }
                            }
                        }
                        outStack.addSlice(""/*imp.getStack().getSliceLabel(z)"*/, clean);
                    }
                    // Save cleaned stack
                    String outPath = new File(outObj, name).getAbsolutePath();
                    new FileSaver(new ImagePlus(name, outStack)).saveAsTiff(outPath);
                }
            }
        }
        IJ.log("=== Small connected components removed (area<=25) ===");
    }
}

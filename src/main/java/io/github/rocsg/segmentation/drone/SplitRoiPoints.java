package io.github.rocsg.segmentation.drone;

import ij.IJ;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import ij.io.OpenDialog;

import java.awt.Rectangle;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SplitRoiPoints {

    public static void main(String[] args) {
        new SplitRoiPoints().process();
    }

    public void process() {
        // 1. Open ROI Set
        OpenDialog od = new OpenDialog("Select ROI Set (.zip)", "");
        String dir = od.getDirectory();
        String name = od.getFileName();
        if (dir == null || name == null) return;
        String path = dir + name;

        // Use RoiManager to open the zip file
        RoiManager rm = new RoiManager(false);
        if (!rm.runCommand("Open", path)) {
            IJ.error("Could not open ROI set: " + path);
            return;
        }
        
        Roi[] rois = rm.getRoisAsArray();
        if (rois.length == 0) {
            IJ.error("No ROIs found in " + path);
            return;
        }

        IJ.log("Opened " + rois.length + " ROIs from " + name);

        // 2. Extract points
        List<int[]> points = new ArrayList<>();
        for (Roi roi : rois) {
            Rectangle r = roi.getBounds();
            // We take the top-left of bounds which corresponds to the point coordinates
            // for single point ROIs.
            points.add(new int[]{r.x, r.y});
            System.out.println("Extracted point: (" + r.x + ", " + r.y + ")");
        }
        
        // 3. Split
        List<int[]> set1 = new ArrayList<>();
        List<int[]> set2 = new ArrayList<>();
        
        int n = points.size();
        if (n % 2 != 0) {
            IJ.log("Odd number of points (" + n + "), discarding the last one.");
            n--; 
        }
        
        for (int i = 0; i < n; i++) {
            if (i % 2 == 0) {
                set1.add(points.get(i));
            } else {
                set2.add(points.get(i));
            }
        }
        
        // 4. Save CSVs
        String baseName = name.toLowerCase().endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
        saveCsv(set1, dir + baseName + "_set1.csv");
        saveCsv(set2, dir + baseName + "_set2.csv");
        
        IJ.log("Done.");
        rm.close();
    }
    
    private void saveCsv(List<int[]> points, String path) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write("x,y\n");
            for (int[] p : points) {
                bw.write(p[0] + "," + p[1] + "\n");
            }
            IJ.log("Saved " + points.size() + " points to " + path);
        } catch (IOException e) {
            IJ.error("Error writing " + path + ": " + e.getMessage());
        }
    }
}

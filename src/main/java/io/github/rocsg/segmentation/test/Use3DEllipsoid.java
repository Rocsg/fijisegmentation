package io.github.rocsg.segmentation.test;
import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;

public class Use3DEllipsoid {
  public static void main(String[] args) {
    // Load your 8-bit binary (0 background, 255 foreground)
    String path = "/home/rfernandez/Bureau/A_Test/Gargee/Ellipsoid/Test.tif";
    ImagePlus imp = IJ.openImage(path);
    imp.show();

    // Run the 3D Ellipsoid measurement (requires 3D ImageJ Suite installed)
    // Command name may appear as "3D Ellipsoid" in recent builds.
    IJ.run(imp, "3D Ellipsoid", "");

    // Read the ResultsTable
    ResultsTable rt = ResultsTable.getResultsTable();
    if (rt == null || rt.size() == 0) {
      System.err.println("No results from 3D Ellipsoid.");
      return;
    }
    int r = rt.size() - 1; // last row
    double cx = rt.getValue("Cx", r);
    double cy = rt.getValue("Cy", r);
    double cz = rt.getValue("Cz", r);
    double R1 = rt.getValue("R1", r);
    double R2 = rt.getValue("R2", r);
    double R3 = rt.getValue("R3", r);

    System.out.printf("Center = [%.3f, %.3f, %.3f]%n", cx, cy, cz);
    System.out.printf("Radii  = [%.3f, %.3f, %.3f]%n", R1, R2, R3);
  }
}
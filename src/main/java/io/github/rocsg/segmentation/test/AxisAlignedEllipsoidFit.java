package io.github.rocsg.segmentation.test;
// AxisAlignedEllipsoidFit.java
// Fit a 3D, axis-aligned ellipsoid to SURFACE points (x,y,z) with equation:
//   ((x-x0)/rx)^2 + ((y-y0)/ry)^2 + ((z-z0)/rz)^2 = 1
// Unknowns: x0,y0,z0, rx,ry,rz. Axis-aligned (no rotations).
// Method: linear least-squares on the quadric with NO cross terms
//         A x^2 + B y^2 + C z^2 + D x + E y + F z + G = 0,
// then complete the square to recover center and radii.
//
// Also includes a helper to read an 8-bit ImageJ ImagePlus (0 background, 255 object),
// extract 6-neighborhood SURFACE points in physical units, and run the fit.
//
// Build:
//   javac -cp ij.jar:commons-math3-3.6.1.jar AxisAlignedEllipsoidFit.java
// Run (reads the path below by default; or pass another file as arg):
//   java  -cp .:ij.jar:commons-math3-3.6.1.jar AxisAlignedEllipsoidFit [path.tif]
//
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.*;
import org.apache.commons.math3.linear.*;

public class AxisAlignedEllipsoidFit {

    // --- Configuration for ImagePlus reading ---
    private static final String DEFAULT_PATH = "/home/rfernandez/Bureau/A_Test/Gargee/Ellipsoid/Test_bound_2.tif";
    private static final int FOREGROUND = 255;   // object value in the 8-bit image
    private static final double SUBSAMPLE = 1.0; // keep all surface points; set <1 to subsample

    // --- Result container ---
    public static class Result {
        public double[] center;      // [x0,y0,z0]
        public double[] radii;       // [rx,ry,rz]
        public double meanResidual;  // mean geometric distance from surface points to fitted ellipsoid (in physical units)
        public double stdResidual;   // standard deviation of geometric distances
        public int nPoints;
        public boolean ok;           // true if rx,ry,rz real positives
        public String note;          // diagnostics
    }

    // ----------------- Public API -----------------
    public static Result fitFromPoints(double[][] pts){
        if (pts==null || pts.length < 6) throw new IllegalArgumentException("Need >= 6 points");
        // Build linear design matrix for parameters [A,B,C,D,E,F,G]
        final int n = pts.length;
        double[][] D = new double[n][7];
        for (int i=0;i<n;i++){
            double x=pts[i][0], y=pts[i][1], z=pts[i][2];
            D[i][0] = x*x;  // A
            D[i][1] = y*y;  // B
            D[i][2] = z*z;  // C
            D[i][3] = x;    // D
            D[i][4] = y;    // E
            D[i][5] = z;    // F
            D[i][6] = 1.0;  // G
        }
        RealMatrix DM = MatrixUtils.createRealMatrix(D);
        // Solve homogeneous least squares: find p (||p||=1) minimizing ||D p||
        SingularValueDecomposition svd = new SingularValueDecomposition(DM);
        RealVector p = svd.getV().getColumnVector(6); // smallest singular vector
        double A = p.getEntry(0), B = p.getEntry(1), C = p.getEntry(2);
        double Dp = p.getEntry(3), Ep = p.getEntry(4), Fp = p.getEntry(5), G = p.getEntry(6);

        // Complete the square to extract center and radii.
        // For 1D: A x^2 + D x = A (x - x0)^2 - A x0^2 with x0 = -D/(2A)
        // In 3D, after shifting to center, the constant becomes:
        //   Gc = G - D^2/(4A) - E^2/(4B) - F^2/(4C)
        // Canonical form: A (X)^2 + B (Y)^2 + C (Z)^2 + Gc = 0,
        // divide by -Gc (>0) to obtain (X^2/(S/A)) + ... = 1 with S = -Gc
        Result R = new Result();
        StringBuilder note = new StringBuilder();

        // Guard against zeros
        if (A==0 || B==0 || C==0) {
            R.ok=false; R.note = "A/B/C contains zero -> degenerate"; return R;
        }

        double x0 = -Dp/(2*A);
        double y0 = -Ep/(2*B);
        double z0 = -Fp/(2*C);

        double Gc = G - (Dp*Dp)/(4*A) - (Ep*Ep)/(4*B) - (Fp*Fp)/(4*C);
        // We want S = -Gc > 0; if not, flip the overall sign (p is defined up to scale)
        double S = -Gc;
        if (S <= 0) {
            A = -A; B = -B; C = -C; Dp = -Dp; Ep = -Ep; Fp = -Fp; G = -G;
            x0 = -Dp/(2*A); y0 = -Ep/(2*B); z0 = -Fp/(2*C);
            Gc = G - (Dp*Dp)/(4*A) - (Ep*Ep)/(4*B) - (Fp*Fp)/(4*C);
            S = -Gc; // try again
            note.append("(flipped sign)");
        }

        double rx2 = S / A;
        double ry2 = S / B;
        double rz2 = S / C;

        boolean ok = rx2>0 && ry2>0 && rz2>0;
        R.ok = ok;
        R.center = new double[]{x0,y0,z0};
        if (ok) {
            R.radii = new double[]{ Math.sqrt(rx2), Math.sqrt(ry2), Math.sqrt(rz2) };
        } else {
            R.radii = new double[]{Double.NaN, Double.NaN, Double.NaN};
            note.append(" non-positive radii^2");
        }

        // Residuals: evaluate geometric distance from fitted ellipsoid surface
        // Using the formula: distance = |f - 1| / ||grad(f)||
        // where f = (dx^2/rx^2 + dy^2/ry^2 + dz^2/rz^2)
        double sum=0, sum2=0; int m=0;
        if (ok) {
            for (double[] q : pts) {
                double dx = (q[0]-x0); double dy=(q[1]-y0); double dz=(q[2]-z0);
                double f = (dx*dx)/rx2 + (dy*dy)/ry2 + (dz*dz)/rz2;
                
                // Compute gradient of f: grad(f) = [2*dx/rx^2, 2*dy/ry^2, 2*dz/rz^2]
                double gradX = 2*dx/rx2;
                double gradY = 2*dy/ry2;
                double gradZ = 2*dz/rz2;
                double gradNorm = Math.sqrt(gradX*gradX + gradY*gradY + gradZ*gradZ);
                
                // Geometric distance (in physical units)
                double dist = Math.abs(f - 1.0) / (gradNorm + 1e-12);
                
                sum += dist; sum2 += dist*dist; m++;
            }
        }
        if (m>0) {
            double mean = sum/m; double var = Math.max(0, sum2/m - mean*mean);
            R.meanResidual = mean; R.stdResidual = Math.sqrt(var); R.nPoints=m;
        } else { R.meanResidual=Double.NaN; R.stdResidual=Double.NaN; R.nPoints=0; }
        R.note = note.toString();
        return R;
    }

    // Convenience: read 8-bit ImagePlus, get SURFACE points in physical coords, fit.
    public static Result fitFromImagePlus(ImagePlus imp, int foreground, double subsample){
        double pw = imp.getCalibration().pixelWidth;  if (pw<=0) pw=1;
        double ph = imp.getCalibration().pixelHeight; if (ph<=0) ph=1;
        double pd = imp.getCalibration().pixelDepth;  if (pd<=0) pd=1;
        double[][] pts = extractSurfacePoints(imp, foreground, subsample, pw, ph, pd);
        return fitFromPoints(pts);
    }

    // ----------------- Image surface extraction -----------------
    private static double[][] extractSurfacePoints(ImagePlus imp, int fg, double keepRate, double pw, double ph, double pd) {
        Random rnd = new Random(42);
        List<double[]> list = new ArrayList<>();
        ImageStack stack = imp.getStack();
        int w = imp.getWidth(); int h = imp.getHeight(); int d = imp.getNSlices();
        for (int z=0; z<d; z++) {
            ImageProcessor ip = stack.getProcessor(z+1);
            for (int y=1; y<h-1; y++) for (int x=1; x<w-1; x++) {
                int v = ip.get(x, y) & 0xFF; if (v != fg) continue;
                if (keepRate < 1.0 && rnd.nextDouble() > keepRate) continue;
                double X = (x + 0.5) * pw; double Y = (y + 0.5) * ph; double Z = (z + 0.5) * pd;
                list.add(new double[]{X, Y, Z});
            }
        }
        double[][] pts = new double[list.size()][]; for (int i=0;i<list.size();i++) pts[i] = list.get(i); return pts;
    }
    private static boolean isBoundary(ImagePlus imp, int x, int y, int z, int fg) {
        ImageStack st = imp.getStack(); ImageProcessor ip = st.getProcessor(z+1);
        if ((ip.get(x,y) & 0xFF) != fg) return false;
        if ((ip.get(x-1,y) & 0xFF) != fg) return true;
        if ((ip.get(x+1,y) & 0xFF) != fg) return true;
        if ((ip.get(x,y-1) & 0xFF) != fg) return true;
        if ((ip.get(x,y+1) & 0xFF) != fg) return true;
        if (z>0 && ((st.getProcessor(z).get(x,y) & 0xFF) != fg)) return true;
        if (z<imp.getNSlices()-1 && ((st.getProcessor(z+2).get(x,y) & 0xFF) != fg)) return true;
        return false;
    }

    // ----------------- Ellipsoid generation -----------------
    /**
     * Generate a binary ImagePlus representing the ellipsoid contour (surface shell).
     * @param center [x0,y0,z0] in physical units
     * @param radii [rx,ry,rz] in physical units
     * @param width image width in pixels
     * @param height image height in pixels
     * @param depth image depth in slices
     * @param pixelWidth physical size of a pixel in X
     * @param pixelHeight physical size of a pixel in Y
     * @param pixelDepth physical size of a pixel in Z
     * @param shellThickness thickness of the shell in physical units (e.g., 1-2 pixels worth)
     * @return 8-bit ImagePlus with 255 for ellipsoid surface, 0 elsewhere
     */
    public static ImagePlus generateEllipsoidContour(double[] center, double[] radii,
                                                      int width, int height, int depth,
                                                      double pixelWidth, double pixelHeight, double pixelDepth,
                                                      double shellThickness) {
        ImageStack stack = ImageStack.create(width, height, depth, 8);
        double x0 = center[0], y0 = center[1], z0 = center[2];
        double rx = radii[0], ry = radii[1], rz = radii[2];
        double rx2 = rx*rx, ry2 = ry*ry, rz2 = rz*rz;
        
        for (int z = 0; z < depth; z++) {
            ImageProcessor ip = stack.getProcessor(z + 1);
            double Z = (z + 0.5) * pixelDepth;
            double dz = Z - z0;
            
            for (int y = 0; y < height; y++) {
                double Y = (y + 0.5) * pixelHeight;
                double dy = Y - y0;
                
                for (int x = 0; x < width; x++) {
                    double X = (x + 0.5) * pixelWidth;
                    double dx = X - x0;
                    
                    // Compute normalized ellipsoid function value
                    double f = (dx*dx)/rx2 + (dy*dy)/ry2 + (dz*dz)/rz2;
                    
                    // Compute the gradient magnitude at this point to get proper distance
                    // gradient of f = [2*dx/rx^2, 2*dy/ry^2, 2*dz/rz^2]
                    double gradX = 2*dx/rx2;
                    double gradY = 2*dy/ry2;
                    double gradZ = 2*dz/rz2;
                    double gradNorm = Math.sqrt(gradX*gradX + gradY*gradY + gradZ*gradZ);
                    
                    // Approximate geometric distance to surface: |f - 1| / ||grad(f)||
                    double dist = Math.abs(f - 1.0) / (gradNorm + 1e-12);
                    
                    // If within shell thickness of the surface, mark as foreground
                    if (dist <= shellThickness) {
                        ip.set(x, y, 255);
                    }
                }
            }
        }
        
        ImagePlus imp = new ImagePlus("Ellipsoid_Contour", stack);
        imp.getCalibration().pixelWidth = pixelWidth;
        imp.getCalibration().pixelHeight = pixelHeight;
        imp.getCalibration().pixelDepth = pixelDepth;
        return imp;
    }
    
    /**
     * Generate a filled binary ImagePlus representing the entire ellipsoid volume.
     * @param center [x0,y0,z0] in physical units
     * @param radii [rx,ry,rz] in physical units
     * @param width image width in pixels
     * @param height image height in pixels
     * @param depth image depth in slices
     * @param pixelWidth physical size of a pixel in X
     * @param pixelHeight physical size of a pixel in Y
     * @param pixelDepth physical size of a pixel in Z
     * @return 8-bit ImagePlus with 255 for ellipsoid interior, 0 elsewhere
     */
    public static ImagePlus generateFilledEllipsoid(double[] center, double[] radii,
                                                     int width, int height, int depth,
                                                     double pixelWidth, double pixelHeight, double pixelDepth) {
        ImageStack stack = ImageStack.create(width, height, depth, 8);
        double x0 = center[0], y0 = center[1], z0 = center[2];
        double rx = radii[0], ry = radii[1], rz = radii[2];
        double rx2 = rx*rx, ry2 = ry*ry, rz2 = rz*rz;
        
        for (int z = 0; z < depth; z++) {
            ImageProcessor ip = stack.getProcessor(z + 1);
            double Z = (z + 0.5) * pixelDepth;
            double dz = Z - z0;
            
            for (int y = 0; y < height; y++) {
                double Y = (y + 0.5) * pixelHeight;
                double dy = Y - y0;
                
                for (int x = 0; x < width; x++) {
                    double X = (x + 0.5) * pixelWidth;
                    double dx = X - x0;
                    
                    // Check if point is inside ellipsoid
                    double f = (dx*dx)/rx2 + (dy*dy)/ry2 + (dz*dz)/rz2;
                    if (f <= 1.0) {
                        ip.set(x, y, 255);
                    }
                }
            }
        }
        
        ImagePlus imp = new ImagePlus("Ellipsoid_Filled", stack);
        imp.getCalibration().pixelWidth = pixelWidth;
        imp.getCalibration().pixelHeight = pixelHeight;
        imp.getCalibration().pixelDepth = pixelDepth;
        return imp;
    }

    // ----------------- Small demo -----------------
    public static void main(String[] args){
        ImageJ ij= new ImageJ();
        
        // Option 1: Test with random synthetic ellipsoid
        boolean useRandomTest = true;
        
        if (useRandomTest) {
            // Generate random ellipsoid parameters
            Random rnd = new Random();
            int imgWidth = 300, imgHeight = 300, imgDepth = 300;
            double pw = 1.0, ph = 1.0, pd = 1.0; // isotropic voxels
            
            // Random center near the center of the image (with some offset)
            double[] trueCenter = new double[]{
                imgWidth/2.0 * pw + (rnd.nextDouble() - 0.5) * 50,
                imgHeight/2.0 * ph + (rnd.nextDouble() - 0.5) * 50,
                imgDepth/2.0 * pd + (rnd.nextDouble() - 0.5) * 50
            };
            
            // Random radii (between 30 and 140 to fit within image)
            double[] trueRadii = new double[]{
                30 + rnd.nextDouble() * 110,
                30 + rnd.nextDouble() * 110,
                30 + rnd.nextDouble() * 110
            };
            
            System.out.println("=== GROUND TRUTH (randomly generated) ===");
            System.out.printf(java.util.Locale.US, "True center = [%.6f, %.6f, %.6f]\n", 
                trueCenter[0], trueCenter[1], trueCenter[2]);
            System.out.printf(java.util.Locale.US, "True radii  = [%.6f, %.6f, %.6f]\n", 
                trueRadii[0], trueRadii[1], trueRadii[2]);
            System.out.println();
            
            // Generate the synthetic ellipsoid contour
            double shellThickness = Math.max(pw, Math.max(ph, pd));
            ImagePlus syntheticContour = generateEllipsoidContour(trueCenter, trueRadii,
                imgWidth, imgHeight, imgDepth, pw, ph, pd, shellThickness);
            syntheticContour.setTitle("Synthetic_Ground_Truth");
            syntheticContour.show();
            
            // Fit the synthetic ellipsoid
            System.out.println("=== FITTING RESULTS ===");
            Result r = fitFromImagePlus(syntheticContour, FOREGROUND, SUBSAMPLE);
            System.out.printf(java.util.Locale.US, "ok=%s note=%s\n", r.ok, r.note);
            System.out.printf(java.util.Locale.US, "Fitted center = [%.6f, %.6f, %.6f]\n", 
                r.center[0], r.center[1], r.center[2]);
            System.out.printf(java.util.Locale.US, "Fitted radii  = [%.6f, %.6f, %.6f]\n", 
                r.radii[0], r.radii[1], r.radii[2]);
            System.out.printf(java.util.Locale.US, "mean residual = %.6g  std = %.6g  (N=%d)\n", 
                r.meanResidual, r.stdResidual, r.nPoints);
            System.out.println();
            
            // Compute errors
            if (r.ok) {
                System.out.println("=== ERRORS ===");
                double centerError = Math.sqrt(
                    Math.pow(r.center[0] - trueCenter[0], 2) +
                    Math.pow(r.center[1] - trueCenter[1], 2) +
                    Math.pow(r.center[2] - trueCenter[2], 2)
                );
                double radiiError = Math.sqrt(
                    Math.pow(r.radii[0] - trueRadii[0], 2) +
                    Math.pow(r.radii[1] - trueRadii[1], 2) +
                    Math.pow(r.radii[2] - trueRadii[2], 2)
                );
                System.out.printf(java.util.Locale.US, "Center error (Euclidean) = %.6f\n", centerError);
                System.out.printf(java.util.Locale.US, "Radii error (Euclidean)  = %.6f\n", radiiError);
                System.out.printf(java.util.Locale.US, "Center error X = %.6f\n", r.center[0] - trueCenter[0]);
                System.out.printf(java.util.Locale.US, "Center error Y = %.6f\n", r.center[1] - trueCenter[1]);
                System.out.printf(java.util.Locale.US, "Center error Z = %.6f\n", r.center[2] - trueCenter[2]);
                System.out.printf(java.util.Locale.US, "Radii error X  = %.6f\n", r.radii[0] - trueRadii[0]);
                System.out.printf(java.util.Locale.US, "Radii error Y  = %.6f\n", r.radii[1] - trueRadii[1]);
                System.out.printf(java.util.Locale.US, "Radii error Z  = %.6f\n", r.radii[2] - trueRadii[2]);
                System.out.println();
                
                // Generate fitted ellipsoid contour
                ImagePlus fittedContour = generateEllipsoidContour(r.center, r.radii,
                    imgWidth, imgHeight, imgDepth, pw, ph, pd, shellThickness);
                fittedContour.setTitle("Fitted_Contour");
                fittedContour.show();
                
                // Generate filled ellipsoid
                ImagePlus fittedFilled = generateFilledEllipsoid(r.center, r.radii,
                    imgWidth, imgHeight, imgDepth, pw, ph, pd);
                fittedFilled.setTitle("Fitted_Filled");
                fittedFilled.show();
            }
        } else {
            // Option 2: Original mode - load from file
            String path = (args.length>0) ? args[0] : DEFAULT_PATH;
            ImagePlus imp = IJ.openImage(path);
            if (imp == null){ System.err.println("Cannot open: "+path); return; }
            Result r = fitFromImagePlus(imp, FOREGROUND, SUBSAMPLE);
            System.out.printf(java.util.Locale.US, "ok=%s note=%s\n", r.ok, r.note);
            System.out.printf(java.util.Locale.US, "center = [%.6f, %.6f, %.6f]\n", r.center[0], r.center[1], r.center[2]);
            System.out.printf(java.util.Locale.US, "radii  = [%.6f, %.6f, %.6f]\n", r.radii[0], r.radii[1], r.radii[2]);
            System.out.printf(java.util.Locale.US, "mean residual = %.6g  std = %.6g  (N=%d)\n", r.meanResidual, r.stdResidual, r.nPoints);

            // Generate and display the fitted ellipsoid contour
            if (r.ok) {
                double pw = imp.getCalibration().pixelWidth;  if (pw<=0) pw=1;
                double ph = imp.getCalibration().pixelHeight; if (ph<=0) ph=1;
                double pd = imp.getCalibration().pixelDepth;  if (pd<=0) pd=1;
                
                // Generate contour with shell thickness of ~1 pixel
                double shellThickness = Math.max(pw, Math.max(ph, pd));
                ImagePlus contour = generateEllipsoidContour(r.center, r.radii, 
                    imp.getWidth(), imp.getHeight(), imp.getNSlices(),
                    pw, ph, pd, shellThickness);
                contour.show();
                
                // Optionally generate filled ellipsoid
                ImagePlus filled = generateFilledEllipsoid(r.center, r.radii,
                    imp.getWidth(), imp.getHeight(), imp.getNSlices(),
                    pw, ph, pd);
                filled.show();
            }
            imp.show();
        }
    }
}

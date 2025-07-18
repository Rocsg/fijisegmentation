/*
 * Image/J Plugins
 * Copyright (C) 2002-2004 Jarek Sacha
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Latest release available at http://sourceforge.net/projects/ij-plugins/
 */
package io.github.rocsg.segmentation.ndpilegacy;

import ij.ImagePlus;
import ij.measure.Calibration;
import ij.process.*;
import com.sun.media.jai.codecimpl.util.DataBufferDouble;
import com.sun.media.jai.codecimpl.util.DataBufferFloat;

import java.awt.image.*;

/**
 * Creates/converts ImageJ's image objects from Java2D/JAI representation.
 * 
 * @author Jarek Sacha
 * @version $Revision: 1.4.2 $
 */
public class ImagePlusCreator
{

    private ImagePlusCreator()
    {
    }


    /**
     * Forces a Rendered image to set all the tiles that it may have. In
     * multi-tile images not all tiles may be updated when a
     * RenderedImage is created.
     * 
     * @param ri image that may need tile update.
     * @return WritableRaster with all tiles updated.
     */
    public static WritableRaster forceTileUpdate(RenderedImage ri)
    {
        Raster r = ri.getData();
        if (!(r instanceof WritableRaster))
            r = Raster.createWritableRaster(r.getSampleModel(),
                    r.getDataBuffer(), null);

        WritableRaster wr = (WritableRaster) r;
        int xTiles = ri.getNumXTiles();
        int yTiles = ri.getNumYTiles();
        for (int ty = 0; ty < yTiles; ++ty)
            for (int tx = 0; tx < xTiles; ++tx)
                wr.setRect(ri.getTile(tx, ty));

        return wr;
    }


    /**
     * Creates an ImageProcessor object from a DataBuffer.
     * 
     * @param w      Image width.
     * @param h      Image height.
     * @param buffer Data buffer.
     * @param cm     Color model.
     * @return Image processor object.
     * @throws UnsupportedImageModelException If data buffer is in
     *     unknown format.
     */
    public static ImageProcessor createProcessor(int w, int h,
            DataBuffer buffer, ColorModel cm)
            throws UnsupportedImageModelException
    {

        if (buffer.getOffset() != 0)
            throw new UnsupportedImageModelException(
                "Expecting BufferData with no offset.");

        switch (buffer.getDataType())
        {
            case DataBuffer.TYPE_BYTE:
                return new ByteProcessor(w, h, ((DataBufferByte) buffer).getData(), cm);
            case DataBuffer.TYPE_USHORT:
                return new ShortProcessor(w, h, ((DataBufferUShort) buffer).getData(), cm);
            case DataBuffer.TYPE_SHORT:
                short[] pixels = ((DataBufferShort) buffer).getData();
                for (int i = 0; i < pixels.length; ++i)
                    pixels[i] = (short) (pixels[i] + 32768);
                return new ShortProcessor(w, h, pixels, cm);
            case DataBuffer.TYPE_INT:
                return new FloatProcessor(w, h, ((DataBufferInt) buffer).getData());
            case DataBuffer.TYPE_FLOAT:
                {
                    DataBufferFloat dbFloat = (DataBufferFloat) buffer;
                    return new FloatProcessor(w, h, dbFloat.getData(), cm);
                }
            case DataBuffer.TYPE_DOUBLE:
                return new FloatProcessor(w, h, ((DataBufferDouble) buffer).getData());
            case DataBuffer.TYPE_UNDEFINED:
                /* ENH: Should this be reported as data problem? */
                throw new UnsupportedImageModelException("Pixel type is undefined.");
            default:
                throw new UnsupportedImageModelException("Unrecognized DataBuffer data type");
        }
    }


    /**
     * Creates instance of ImagePlus from WritableRaster r and
     * ColorModel cm.
     * 
     * @param r  Raster containing pixel data.
     * @param cm Image color model (can be null).
     * @return ImagePlus object created from WritableRaster r and
     *         ColorModel cm
     * @throws UnsupportedImageModelException when enable to create ImagePlus.
     */
    public static ImagePlus create(WritableRaster r, ColorModel cm)
            throws UnsupportedImageModelException
    {
        DataBuffer db = r.getDataBuffer();

        int numBanks = db.getNumBanks();
        if (numBanks > 1 && cm == null)
            throw new UnsupportedImageModelException(
                    "Don't know what to do with image with no color " +
                    "model and multiple banks.");

        SampleModel sm = r.getSampleModel();
        int dbType = db.getDataType();
        if (numBanks > 1 || sm.getNumBands() > 1)
        {
            /* If image has multiple banks or multiple color components, 
              assume that it is a color image and relay on AWT for
              proper decoding. */
            BufferedImage bi = new BufferedImage(cm, r, false, null);
            return new ImagePlus(null, new ColorProcessor(bi));
        } else if (sm.getSampleSize(0) < 8) {
            /* Temporary fix for less-than-8-bit images */
            BufferedImage bi = new BufferedImage(cm, r, false, null);
            return new ImagePlus(null, new ByteProcessor(bi));
        } else {
            if (!(cm instanceof IndexColorModel))
            {
                /* ImageJ (as of version 1.26r) can not properly deal 
                  with non color images and ColorModel that is not an 
                  instance of IndexedColorModel. */
                cm = null;
            }

            ImageProcessor ip = createProcessor(r.getWidth(), r.getHeight(),
                    r.getDataBuffer(), cm);
            ImagePlus im = new ImagePlus(null, ip);

            /* Add calibration function for 'short' pixels */
            if (db.getDataType() == DataBuffer.TYPE_SHORT)
            {
                Calibration cal = new Calibration(im);
                double[] coeff = new double[2];
                coeff[0] = -32768.0;
                coeff[1] = 1.0;
                cal.setFunction(Calibration.STRAIGHT_LINE, coeff, "gray value");
                im.setCalibration(cal);
            } else if (cm == null) {
                Calibration cal = im.getCalibration();
                im.setCalibration(null);
                ImageStatistics stats = im.getStatistics();
                im.setCalibration(cal);
                ip.setMinAndMax(stats.min, stats.max);
                im.updateImage();
            }

            return im;
        }

    }
}

package org.sunflow.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;

import org.sunflow.PluginRegistry;
import org.sunflow.image.Bitmap;
import org.sunflow.image.BitmapReader;
import org.sunflow.image.Color;
import org.sunflow.image.BitmapReader.BitmapFormatException;
import org.sunflow.image.formats.BitmapBlack;
import org.sunflow.math.MathUtils;
import org.sunflow.math.OrthoNormalBasis;
import org.sunflow.math.Vector3;
import org.sunflow.system.FileUtils;
import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;

/**
 * Represents a 2D texture, typically used by {@link Shader shaders}.
 */
public class Texture {
    private String filename;
    private boolean isLinear;
    private Bitmap bitmap;
    private int loaded;
    // EP : Added bitmap transparency support
    private boolean isTransparent;

    /**
     * Creates a new texture from the specfied file.
     * 
     * @param filename image file to load
     * @param isLinear is the texture gamma corrected already?
     */
    Texture(String filename, boolean isLinear) {
        this.filename = filename;
        this.isLinear = isLinear;
        loaded = 0;
    }

    private synchronized void load() {
        if (loaded != 0)
            return;
        String extension = FileUtils.getExtension(filename);
        try {
            UI.printInfo(Module.TEX, "Reading texture bitmap from: \"%s\" ...", filename);
            BitmapReader reader = PluginRegistry.bitmapReaderPlugins.createObject(extension);
            // EP : Tolerate no extension in URLs
            if (reader == null) {
                try {
                    // Choose a reader depending on the magic number of the file
                    URL url = new URL(filename);
                    URLConnection connection = url.openConnection();
                    if (connection instanceof JarURLConnection) {
                        JarURLConnection urlConnection = (JarURLConnection) connection;
                        URL jarFileUrl = urlConnection.getJarFileURL();
                        if (jarFileUrl.getProtocol().equalsIgnoreCase("file")) {
                            try {
                                if (new File(jarFileUrl.toURI()).canWrite()) {
                                    // Refuse to use cache to be able to delete the writable files accessed with jar protocol,
                                    // as suggested in http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6962459
                                    connection.setUseCaches(false);
                                }
                            } catch (URISyntaxException ex) {
                                throw new IOException(ex);
                            }
                        }
                    }
                    InputStream in = connection.getInputStream();
                    int firstByte = in.read();
                    int secondByte = in.read();
                    in.close();                    
                    reader = firstByte == 0xFF && secondByte == 0xD8
                        ? PluginRegistry.bitmapReaderPlugins.createObject("jpg")
                        : PluginRegistry.bitmapReaderPlugins.createObject("png");
                } catch (IOException ex) {  
                    // Don't try to search an other reader
                }
            }
            // EP : End of modification
            if (reader != null) {
                bitmap = reader.load(filename, isLinear);
                if (bitmap.getWidth() == 0 || bitmap.getHeight() == 0)
                    bitmap = null;
            }
            // EP : Check transparency
            for (int x = 0; x < bitmap.getWidth(); x++) {
                for (int y = 0; y < bitmap.getHeight(); y++) {
                    if (bitmap.readAlpha(x, y) < 1) {
                        this.isTransparent = true;
                        break;
                    }
                }
            }
            // EP : End of modification
            if (bitmap == null) {
                UI.printError(Module.TEX, "Bitmap reading failed");
                bitmap = new BitmapBlack();
            } else
                UI.printDetailed(Module.TEX, "Texture bitmap reading complete: %dx%d pixels found", bitmap.getWidth(), bitmap.getHeight());
        } catch (IOException e) {
            UI.printError(Module.TEX, "%s", e.getMessage());
        } catch (BitmapFormatException e) {
            UI.printError(Module.TEX, "%s format error: %s", extension, e.getMessage());
        }
        loaded = 1;
    }

    public Bitmap getBitmap() {
        if (loaded == 0)
            load();
        return bitmap;
    }

    /**
     * Gets the color at location (x,y) in the texture. The lookup is performed
     * using the fractional component of the coordinates, treating the texture
     * as a unit square tiled in both directions. Bicubic filtering is performed
     * on the four nearest pixels to the lookup point.
     * 
     * @param x x coordinate into the texture
     * @param y y coordinate into the texture
     * @return filtered color at location (x,y)
     */
    public Color getPixel(float x, float y) {
        Bitmap bitmap = getBitmap();
        x = MathUtils.frac(x);
        y = MathUtils.frac(y);
        float dx = x * (bitmap.getWidth() - 1);
        float dy = y * (bitmap.getHeight() - 1);
        int ix0 = (int) dx;
        int iy0 = (int) dy;
        int ix1 = (ix0 + 1) % bitmap.getWidth();
        int iy1 = (iy0 + 1) % bitmap.getHeight();
        float u = dx - ix0;
        float v = dy - iy0;
        u = u * u * (3.0f - (2.0f * u));
        v = v * v * (3.0f - (2.0f * v));
        float k00 = (1.0f - u) * (1.0f - v);
        Color c00 = bitmap.readColor(ix0, iy0);
        float k01 = (1.0f - u) * v;
        Color c01 = bitmap.readColor(ix0, iy1);
        float k10 = u * (1.0f - v);
        Color c10 = bitmap.readColor(ix1, iy0);
        float k11 = u * v;
        Color c11 = bitmap.readColor(ix1, iy1);
        Color c = Color.mul(k00, c00);
        c.madd(k01, c01);
        c.madd(k10, c10);
        c.madd(k11, c11);
        return c;
    }
    
    // EP : Added bitmap transparency support
    public Color getOpacity(float x, float y) {
        Bitmap bitmap = getBitmap();
        x = MathUtils.frac(x);
        y = MathUtils.frac(y);
        float dx = x * (bitmap.getWidth() - 1);
        float dy = y * (bitmap.getHeight() - 1);
        int ix0 = (int) dx;
        int iy0 = (int) dy;
        int ix1 = (ix0 + 1) % bitmap.getWidth();
        int iy1 = (iy0 + 1) % bitmap.getHeight();
        float u = dx - ix0;
        float v = dy - iy0;
        u = u * u * (3.0f - (2.0f * u));
        v = v * v * (3.0f - (2.0f * v));
        float k00 = (1.0f - u) * (1.0f - v);
        float a00 = bitmap.readAlpha(ix0, iy0);
        float k01 = (1.0f - u) * v;
        float a01 = bitmap.readAlpha(ix0, iy1);
        float k10 = u * (1.0f - v);
        float a10 = bitmap.readAlpha(ix1, iy0);
        float k11 = u * v;
        float a11 = bitmap.readAlpha(ix1, iy1);
        float transparency = k00 * a00 +  k01 * a01 + k10 * a10 + k11 * a11;
        if (transparency < 0.005) {
            return Color.BLACK;
        } else if (transparency > 0.995) {
            return Color.WHITE; 
        } else {
            Color c00 = bitmap.readColor(ix0, iy0).mul(1 - a00);
            Color c01 = bitmap.readColor(ix0, iy1).mul(1 - a01);
            Color c10 = bitmap.readColor(ix1, iy0).mul(1 - a10);
            Color c11 = bitmap.readColor(ix1, iy1).mul(1 - a11);
            Color c = Color.mul(k00, c00);
            c.madd(k01, c01);
            c.madd(k10, c10);
            c.madd(k11, c11);
            return c.opposite();
        }
    }

    public float getOpacityAlpha(float x, float y) {
        Bitmap bitmap = getBitmap();
        x = MathUtils.frac(x);
        y = MathUtils.frac(y);
        float dx = x * (bitmap.getWidth() - 1);
        float dy = y * (bitmap.getHeight() - 1);
        int ix0 = (int) dx;
        int iy0 = (int) dy;
        int ix1 = (ix0 + 1) % bitmap.getWidth();
        int iy1 = (iy0 + 1) % bitmap.getHeight();
        float u = dx - ix0;
        float v = dy - iy0;
        u = u * u * (3.0f - (2.0f * u));
        v = v * v * (3.0f - (2.0f * v));
        float k00 = (1.0f - u) * (1.0f - v);
        float a00 = bitmap.readAlpha(ix0, iy0);
        float k01 = (1.0f - u) * v;
        float a01 = bitmap.readAlpha(ix0, iy1);
        float k10 = u * (1.0f - v);
        float a10 = bitmap.readAlpha(ix1, iy0);
        float k11 = u * v;
        float a11 = bitmap.readAlpha(ix1, iy1);
        return k00 * a00 +  k01 * a01 + k10 * a10 + k11 * a11;        
    }

    public boolean isTransparent() {
        return this.isTransparent;
    }
    // EP : End of modification
    
    public Vector3 getNormal(float x, float y, OrthoNormalBasis basis) {
        float[] rgb = getPixel(x, y).getRGB();
        return basis.transform(new Vector3(2 * rgb[0] - 1, 2 * rgb[1] - 1, 2 * rgb[2] - 1)).normalize();
    }

    public Vector3 getBump(float x, float y, OrthoNormalBasis basis, float scale) {
        Bitmap bitmap = getBitmap();
        float dx = 1.0f / bitmap.getWidth();
        float dy = 1.0f / bitmap.getHeight();
        float b0 = getPixel(x, y).getLuminance();
        float bx = getPixel(x + dx, y).getLuminance();
        float by = getPixel(x, y + dy).getLuminance();
        return basis.transform(new Vector3(scale * (b0 - bx), scale * (b0 - by), 1)).normalize();
    }
}
package com.mapbox.mapboxsdk.tileprovider.tilesource;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import com.google.common.base.Strings;
import com.mapbox.mapboxsdk.tileprovider.MapTile;
import com.mapbox.mapboxsdk.tileprovider.MapTileCache;
import com.mapbox.mapboxsdk.tileprovider.modules.MapTileDownloader;
import com.mapbox.mapboxsdk.util.NetworkUtils;
import com.mapbox.mapboxsdk.views.util.TileLoadedListener;
import com.mapbox.mapboxsdk.views.util.TilesLoadedListener;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An implementation of {@link TileLayer} that pulls tiles from the internet.
 */
public class WebSourceTileLayer extends TileLayer {
    private static final String TAG = "WebSourceTileLayer";

    // Tracks the number of threads active in the getBitmapFromURL method.
    private AtomicInteger activeThreads = new AtomicInteger(0);
    protected boolean mEnableSSL = false;

    public WebSourceTileLayer(final String pId, final String url) {
        this(pId, url, false);
    }

    public WebSourceTileLayer(final String pId, final String url, final boolean enableSSL) {
        super(pId, url);
        initialize(pId, url, enableSSL);
    }

    private boolean checkThreadControl() {
        return activeThreads.get() == 0;
    }

    @Override
    public TileLayer setURL(final String aUrl) {
        if (aUrl.contains(String.format("http%s://", (mEnableSSL ? "" : "s")))) {
            super.setURL(aUrl.replace(String.format("http%s://", (mEnableSSL ? "" : "s")),
                    String.format("http%s://", (mEnableSSL ? "s" : ""))));
        } else {
            super.setURL(aUrl);
        }
        return this;
    }

    protected void initialize(String pId, String aUrl, boolean enableSSL) {
        mEnableSSL = enableSSL;
        setURL(aUrl);
    }

    /**
     * Gets a list of Tile URLs used by this layer for a specific tile.
     *
     * @param aTile a map tile
     * @param hdpi a boolean that indicates whether the tile should be at 2x or retina size
     * @return a list of tile URLS
     */
    public String[] getTileURLs(final MapTile aTile, boolean hdpi) {
        String url = getTileURL(aTile, hdpi);
        if (!Strings.isNullOrEmpty(url)) {
            return new String[] { url };
        }
        return null;
    }

    /**
     * Get a single Tile URL for a single tile.
     *
     * @param aTile a map tile
     * @param hdpi a boolean that indicates whether the tile should be at 2x or retina size
     * @return a list of tile URLs
     */
    public String getTileURL(final MapTile aTile, boolean hdpi) {
        return parseUrlForTile(mUrl, aTile, hdpi);
    }

    protected String parseUrlForTile(String url, final MapTile aTile, boolean hdpi) {
        return url.replace("{z}", String.valueOf(aTile.getZ()))
                .replace("{x}", String.valueOf(aTile.getX()))
                .replace("{y}", String.valueOf(aTile.getY()))
                .replace("{2x}", hdpi ? "@2x" : "");
    }

    private static final Paint compositePaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private Bitmap compositeBitmaps(final Bitmap source, Bitmap dest) {
        Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, 0, 0, compositePaint);
        return dest;
    }

    @Override
    public Drawable getDrawableFromTile(final MapTileDownloader downloader, final MapTile aTile, boolean hdpi) {
        if (downloader.isNetworkAvailable()) {
            TilesLoadedListener listener = downloader.getTilesLoadedListener();

            String[] urls = getTileURLs(aTile, hdpi);
            Drawable result = null;
            Bitmap resultBitmap = null;
            if (urls != null) {
                MapTileCache cache = downloader.getCache();
                if (listener != null) {
                    listener.onTilesLoadStarted();
                }
                for (final String url : urls) {
                    Bitmap bitmap = getBitmapFromURL(url);
                    if (bitmap == null) {
                        continue;
                    }
                    if (resultBitmap == null) {
                        resultBitmap = bitmap;
                    } else {
                        resultBitmap = compositeBitmaps(bitmap, resultBitmap);
                    }
                }
                if (resultBitmap != null) {
                    //get drawable by putting it into cache (memory and disk)
                    result = cache.putTileBitmap(aTile, resultBitmap);
                }
                if (checkThreadControl()) {
                    if (listener != null) {
                        listener.onTilesLoaded();
                    }
                }
            }

            if (result != null) {
                TileLoadedListener listener2 = downloader.getTileLoadedListener();
                result = listener2 != null ? listener2.onTileLoaded(result) : result;
            }

            return result;
        } else {
            Log.d(TAG, "Skipping tile " + aTile.toString() + " due to NetworkAvailabilityCheck.");
        }
        return null;
    }

    /**
     * Requests and returns a bitmap object from a given URL, using aCache to decode it.
     *
     * @param url the map tile url. should refer to a valid bitmap resource.
     * @return the tile if valid, otherwise null
     */
    public Bitmap getBitmapFromURL(final String url) {
        // We track the active threads here, every exit point should decrement this value.
        Log.d(getClass().getCanonicalName(), "getBitmapFormURL() called with url = '" + url + "'");
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        return NetworkUtils.getImageLoader().loadImageSync(url);
    }
}

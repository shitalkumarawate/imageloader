package com.example.gse.imageloader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 *
 */
public class ImageLoader
{
    private static ImageLoader _instance;
    private LruCache<String, Bitmap> memoryCache;
    private ExecutorService executorService;
    private Map imageViewMap;
    private Handler handler;
    private static int screenWidth;
    private static int screenHeight;

    private SimpleDiskCache diskLruCache;
    private final Object diskCacheLock = new Object();
    private boolean diskCacheStarting = true;
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB

    /**
     *
     * @param context
     */
    private ImageLoader(Context context)
    {
        super();
        try {
            final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

            // 1/6th of the available memory for this memory cache.
            final int cacheSize = maxMemory / 6;

            memoryCache = new LruCache<String, Bitmap>(cacheSize) {
                @Override
                protected int sizeOf(String key, Bitmap bitmap) {
                    // The cache size will be measured in kilobytes rather than
                    // number of items.
                    return bitmap.getByteCount() / 1024;
                }
            };

            imageViewMap = Collections.synchronizedMap(new WeakHashMap());

            executorService = Executors.newFixedThreadPool(5, new ImageThreadFactory());
            handler = new Handler();

            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;

            /**
             * Disk cache initialization
             */
            File cacheDir = getDiskCacheDir(context);
            new InitDiskCacheTask().execute(cacheDir);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param context
     * @return
     */
    public static synchronized ImageLoader with(Context context)
    {
        if(context == null)
            throw new IllegalArgumentException("Context should not be null.");

        if(_instance == null)
            _instance = new ImageLoader(context);

        return _instance;
    }

    /**
     * For disk cache initialization
     */
    class InitDiskCacheTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... params) {
            synchronized (diskCacheLock)
            {
                try
                {
                    File cacheDir = params[0];
                    diskLruCache = SimpleDiskCache.open(cacheDir, 1, DISK_CACHE_SIZE);
                    diskCacheStarting = false; // Finished initialization
                    diskCacheLock.notifyAll(); // Wake any waiting threads
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    /**
     * Load image on image view
     *
     * @param imageView
     * @param imageUrl
     */
    public void load(ImageView imageView, String imageUrl)
    {
        try {
            if(imageView != null && imageUrl != null)
            {
                imageView.setImageResource(0);
                imageViewMap.put(imageView, imageUrl);

                /**
                 * Check in disk and disk cache
                 */
                Bitmap bitmap = getBitmapFromMemCache(imageUrl);

                if (bitmap != null)
                    loadImageIntoImageView(imageView, bitmap, imageUrl);
                else
                    executorService.submit(new LoaderImageTask(new ImageRequest(imageUrl, imageView)));
            }
            else
            {
                throw new IllegalArgumentException("Image View and Url should not be null.");
            }
        }catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param imageView
     * @param bitmap
     * @param imageUrl
     */
    private synchronized void loadImageIntoImageView(ImageView imageView, Bitmap bitmap, String imageUrl)
    {
        try
        {
            Bitmap scaledBitmap = scaleBitmapForLoad(bitmap, imageView.getWidth(), imageView.getHeight());
            if (scaledBitmap != null)
            {
                if (!isImageViewReused(new ImageRequest(imageUrl, imageView)))
                    imageView.setImageBitmap(scaledBitmap);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param imageRequest
     * @return
     */
    private boolean isImageViewReused(ImageRequest imageRequest) {
        String tag = (String)imageViewMap.get(imageRequest.getImageView());
        return tag == null || tag != imageRequest.getImgUrl();
    }

    /**
     *
     * @param key
     * @param bitmap
     */
    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        try {
            // Add to memory cache
            memoryCache.put(key, bitmap);

            // Also add to disk cache
            synchronized (diskCacheLock) {
                if (diskLruCache != null && diskLruCache.getBitmap(key) == null)
                {
                    /**
                     * Save in Disk Cache.
                     */
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100,  stream);
                    BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(stream.toByteArray()));

                    diskLruCache.put(key, inputStream);
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param key
     * @return
     */
    private synchronized Bitmap getBitmapFromMemCache(String key)
    {
        /**
         * Check in memory cache
         */
        Bitmap bitmap = memoryCache.get(key);

        /**
         * If not fount in memory cache check in disk cache
         */
        if(bitmap == null)
            bitmap = getBitmapFromDiskCache(key);
        else
            Log.e("@getBMFromMemCache()", "Retrieved from Memory cache");

        return bitmap;
    }

    /**
     *
     * @param key
     * @return
     */
    public Bitmap getBitmapFromDiskCache(String key)
    {
        synchronized (diskCacheLock)
        {
            try
            {
                // Wait while disk cache is started from background thread
                while (diskCacheStarting) {
                    try {
                        diskCacheLock.wait();
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                /**
                 * get bitmap if availble
                 */
                if (diskLruCache != null)
                {
                    if(diskLruCache.getBitmap(key) != null) {
                        Log.e("@getBMFromDiskCache()", "Retrieved from Disk cache");
                        return diskLruCache.getBitmap(key).getBitmap();
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Background thread to download image
     */
    public class LoaderImageTask implements Runnable
    {
        private ImageRequest imageRequest;

        /**
         *
         * @param imageRequest
         */
        public LoaderImageTask(ImageRequest imageRequest) {
            super();
            this.imageRequest = imageRequest;
        }

        @Override
        public void run()
        {
            try {
                if (isImageViewReused(imageRequest))
                    return;

                Bitmap bitmap = downloadBitmapFromURL(imageRequest.getImgUrl());
                addBitmapToMemoryCache(imageRequest.getImgUrl(), bitmap);

                if (isImageViewReused(imageRequest))
                    return;

                /**
                 * Update ImageView UI
                 */
                handler.post(new DisplayBitmap(imageRequest));
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     */
    public class ImageRequest {
        private String imgUrl;
        private ImageView imageView;

        public String getImgUrl() {
            return imgUrl;
        }

        public ImageView getImageView() {
            return imageView;
        }

        public ImageRequest(String imgUrl, ImageView imageView) {
            this.imgUrl = imgUrl;
            this.imageView = imageView;
        }
    }

    /**
     * Runnable to display image view on UI
     */
    public final class DisplayBitmap implements Runnable
    {
        private ImageRequest imageRequest;

        public void run() {
            if (!isImageViewReused(this.imageRequest)) {
                loadImageIntoImageView(this.imageRequest.getImageView(), getBitmapFromMemCache(this.imageRequest.getImgUrl()), this.imageRequest.getImgUrl());
            }
        }

        public DisplayBitmap(ImageRequest imageRequest) {
            super();
            this.imageRequest = imageRequest;
        }
    }

    /**
     *  Set tread priority as background one
     */
    public  class ImageThreadFactory implements ThreadFactory
    {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName("ImageLoader Thread");
            thread.setPriority(10);
            return thread;
        }
    }

    /**
     * Downloading BitMap from URL
     *
     * @param imageUrl
     * @return
     */
    public final Bitmap downloadBitmapFromURL(String imageUrl)
    {
        BufferedInputStream inputStream = null;
        try {
            URL url = new URL(imageUrl);
            inputStream = new BufferedInputStream(url.openConnection().getInputStream());
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return scaleBitmap(inputStream, screenWidth, screenHeight);
    }

    /**
     *
     * @param bitmap
     * @param width
     * @param height
     * @return
     */
    public final Bitmap scaleBitmapForLoad(Bitmap bitmap, int width, int height)
    {
        try {
            if (width != 0 && height != 0)
            {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100,  stream);
                BufferedInputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(stream.toByteArray()));
                return scaleBitmap(inputStream, width, height);
            } else {
                return bitmap;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     *
     * @param inputStream
     * @param width
     * @param height
     * @return
     */
    private Bitmap scaleBitmap(BufferedInputStream inputStream, int width, int height)
    {
        BitmapFactory.Options options = new BitmapFactory.Options();
        try {
            inputStream.mark(inputStream.available());
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(inputStream,null, options);
            options.inSampleSize = calculateInSampleSize(options, width, height);
            options.inJustDecodeBounds = false;
            inputStream.reset();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return BitmapFactory.decodeStream(inputStream, null, options);
    }

    /**
     *
     * @param options
     * @param reqWidth
     * @param reqHeight
     * @return
     */
    private  int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    /**
     *
     * Creates a unique subdirectory of the designated app cache directory. Tries to use external
     * but if not mounted, falls back on internal storage.
     *
     * @param context
     * @return
     */
    public File getDiskCacheDir(Context context)
    {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        File cacheDir;
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
            cacheDir=new File(android.os.Environment.getExternalStorageDirectory(),"ILImages_cache");
        else
            cacheDir=context.getCacheDir();

        if(!cacheDir.exists())
            cacheDir.mkdirs();

        return cacheDir;
    }
}

package com.bignerdranch.android.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by Jeff on 3/7/2016.
 */
public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String LOG_TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int MESSAGE_PRELOAD = 1;

    private Handler mRequestHandler;
    private ConcurrentMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private ThumbnailDownloadListener<T> mThumbnailDownloadListener;
    private LruCache<String, Bitmap> mLruCache;

    public ThumbnailDownloader(Handler responseHandler) {
        super(LOG_TAG);
        mResponseHandler = responseHandler;
        mLruCache = new LruCache<String, Bitmap>(16384);
    }

    public void setThumbnailDownloadListener(ThumbnailDownloadListener<T> listener) {
        mThumbnailDownloadListener = listener;
    }

    public void queueThumbnail(T target, String url) {
        Log.i(LOG_TAG, "Got a URL: " + url);

        if (url == null) {
            mRequestMap.remove(target);
        } else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target).sendToTarget();
        }
    }

    public void preloadImmage(String url) {
        mRequestHandler.obtainMessage(MESSAGE_PRELOAD, url).sendToTarget();
    }

    @Override
    protected void onLooperPrepared() {
        mRequestHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MESSAGE_DOWNLOAD:
                        T target = (T) msg.obj;
                        Log.i(LOG_TAG, "Got a request for URL: " + mRequestMap.get(target));
                        handleRequest(target);
                        break;
                    case MESSAGE_PRELOAD:
                        String url = (String) msg.obj;
                        downloadImage(url);
                        break;
                }
            }
        };
    }

    public void clearCache() {
        mLruCache.evictAll();
    }

    public Bitmap getCachedImage(String url) {
        return mLruCache.get(url);
    }

    private void handleRequest(final T target) {
            final String url = mRequestMap.get(target);
        final Bitmap bitmap;

        if (url == null) {
            return;
        }

        bitmap = downloadImage(url);
            Log.i(LOG_TAG, "Bitmap created");

            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mRequestMap.get(target) != url) {
                        return;
                    }

                    mRequestMap.remove(target);
                    mThumbnailDownloadListener.onThumbnailDownloaded(target, bitmap);
                }
            });
    }

    private Bitmap downloadImage(String url) {
        Bitmap bitmap;

        if (url == null) {
            return null;
        }

        bitmap = mLruCache.get(url);
        if (bitmap != null) {
            return bitmap;
        }

        try {
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            mLruCache.put(url, bitmap);
            Log.i(LOG_TAG, "Downloaded & cached image: " + url);
            return bitmap;
        } catch (IOException ioe) {
            Log.e(LOG_TAG, "Error downloading image", ioe);
            return null;
        }
    }

    public void clearQueue() {
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
    }

    public interface ThumbnailDownloadListener<T> {
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }
}

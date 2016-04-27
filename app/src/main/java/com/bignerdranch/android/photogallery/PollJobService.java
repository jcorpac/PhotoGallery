package com.bignerdranch.android.photogallery;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.util.List;

/**
 * Created by Jeff on 4/27/2016.
 */
public class PollJobService extends JobService {
    private static final String TAG = "PollJobService";
    private PollTask mCurrentTask;

    @Override
    public boolean onStartJob(JobParameters params) {
        mCurrentTask = new PollTask();
        mCurrentTask.execute(params);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (mCurrentTask != null) {
            mCurrentTask.cancel(true);
        }
        return false;
    }


    private class PollTask extends AsyncTask<JobParameters, Void, List<GalleryItem>> {


        @Override
        protected List<GalleryItem> doInBackground(JobParameters... params) {
            JobParameters jobParams = params[0];

            // Poll Flickr for new images
            Log.i(TAG, "doInBackground: Poll Flickr for new images");
            String query = QueryPreferences.getStoredQuery(PollJobService.this);

            List<GalleryItem> items;
            int page = 1;

            if(query == null) {
                Log.i(TAG, "query == null");
                items = new FlickrFetchr().fetchRecentPhotos();
            } else {
                Log.i(TAG, "query != null");
                items = new FlickrFetchr().searchPhotos(query);
            }

            jobFinished(jobParams, false);
            return items;
        }

        @Override
        protected void onPostExecute(List<GalleryItem> galleryItems) {
            if(galleryItems.size() == 0) {
                return;
            }
            String lastResultId = QueryPreferences.getPrefLastResultId(PollJobService.this);
            String resultId = galleryItems.get(0).getId();

            if (resultId.equals(lastResultId)) {
                Log.i(TAG, "Got an old resullt: " + resultId);
            } else {
                Log.i(TAG, "Got an new resullt: " + resultId);
                Intent i = PhotoGalleryActivity.newIntent(PollJobService.this);
                PendingIntent pi = PendingIntent.getActivity(PollJobService.this, 0, i, 0);

                Notification notification =  new NotificationCompat.Builder(PollJobService.this)
                        .setTicker(getString(R.string.new_pictures_title))
                        .setSmallIcon(android.R.drawable.ic_menu_report_image)
                        .setContentTitle(getString(R.string.new_pictures_title))
                        .setContentText(getString(R.string.new_pictures_text))
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build();

                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(PollJobService.this);
                notificationManager.notify(0, notification);
            }
            QueryPreferences.setPrefLastResultId(PollJobService.this, resultId);
        }
    }
}

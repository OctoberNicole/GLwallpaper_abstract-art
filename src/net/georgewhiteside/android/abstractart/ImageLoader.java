package net.georgewhiteside.android.abstractart;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import net.georgewhiteside.android.abstractart.settings.ThumbnailAdapter.ViewHolder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher;

public class ImageLoader extends Thread
{
	public interface ImageLoadListener
	{
		void onImageLoaded(final ViewHolder holder, final Bitmap bitmap, final int position);
	}
	
	private static final String TAG = "ImageLoader";
	ImageLoadListener mListener = null;
	private Handler handler;
	private Context context;
	private GLOffscreenSurface glOffscreenSurface;
	private Renderer renderer;
	
	public ImageLoader(Context context, Renderer renderer, GLOffscreenSurface glOffscreenSurface, ImageLoadListener lListener)
	{
		this.context = context;
		mListener = lListener;
		this.renderer = renderer;
		this.glOffscreenSurface = glOffscreenSurface;
	}
	
	@Override
	public void run()
	{
		Looper.prepare();			// prepare looper on current thread
		handler = new Handler();	// handler automatically binds to the looper
		Looper.loop();				// get the party started
	}

	public synchronized void stopThread()
	{
		handler.post(new Runnable()
		{
			public void run()
			{
				Looper.myLooper().quit();
			}
		});
	}
	
	private void queueFront(final File cacheFile, final ViewHolder holder, final int position)
	{
		handler.postAtFrontOfQueue(new Runnable()
		{
			public void run()
			{
				if(holder.index != position) return;
				
				Bitmap thumbnail = BitmapFactory.decodeFile(cacheFile.getPath());
				if(mListener != null) mListener.onImageLoaded(holder, thumbnail, position);
			}
		});
	}
	
	private void queueBack(final File cacheFile, final ViewHolder holder, final int position)
	{
		handler.post(new Runnable()
		{
			public void run()
			{
				if(holder.index != position)
		 		{
		 			// If the on-screen view index doesn't match the thumbnail index, that means
					// it went off-screen and got recycled before the this event ever got a chance
					// to fire. Just forget about it for now, we'll load it next time it scrolls by
					// which keeps the UI more responsive and relevant.
		 			return;
		 		}
				
				try
				{
		 			// reacquire an EGL context for every pass in this thread
		 			// (setRenderer might be a bit heavy for this purpose, look into doing less if possible)
		 			if(!glOffscreenSurface.checkCurrentThread())
		 			{
		 				glOffscreenSurface.setRenderer(renderer);
		 			}
		 			
		 			renderer.loadBattleBackground(position);
		 			
		 			Bitmap thumbnail = glOffscreenSurface.getBitmap();
		 			
		 			cacheFile.getParentFile().mkdirs(); // safely does nothing if path exists
		 			
						FileOutputStream fileOutputStream = new FileOutputStream(cacheFile);
						thumbnail.compress(CompressFormat.PNG, 80, fileOutputStream); // quality is irrelevant for PNGs
						fileOutputStream.close();
		 			
						// thumbnail cached correctly; poke the UI thread right in its callback
						
						if(mListener != null) mListener.onImageLoaded(holder, thumbnail, position);
				}
				catch (FileNotFoundException e)
				{
		 			Log.i(TAG, "coult not write thumbnail to disk cache");
						e.printStackTrace();
		 		}
				catch(Exception e)
				{
		 			Log.e(TAG, "Something exploded: \"" + e.getMessage() + "\"");
		 			e.printStackTrace();
				}
			}
		});
	}
	
	public synchronized void queueImageLoad(final int position, final ViewHolder holder)
	{
		String cacheFileName = String.valueOf(position); //String.format("%03d", index);
 		File cacheDir = new File(context.getCacheDir(), "thumbnails");
 		File cacheFile = new File(cacheDir, cacheFileName);
 		
 		try
		{
	 		if(cacheFile.exists())
	 		{
	 			queueFront(cacheFile, holder, position);
	 		}
	 		else
	 		{
	 			queueBack(cacheFile, holder, position);
	 		}
		}
 		catch(Exception e)
		{
			Log.w(TAG, "Failed to post an ImageLoader event");
		}
		
	}
}

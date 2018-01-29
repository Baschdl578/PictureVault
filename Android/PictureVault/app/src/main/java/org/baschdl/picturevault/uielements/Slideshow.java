package org.baschdl.picturevault.uielements;

import android.animation.Animator;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

import org.baschdl.picturevault.R;

import java.io.File;
import java.util.Random;

/**
 * Created by baschdl on 23.08.17.
 */

public class Slideshow extends AppCompatImageView {
    RefreshHandler switcher;
    File[] ids;
    boolean isSet = false;
    int oldId;
    boolean stop;



    public Slideshow(Context context) {
        super(context);
        oldId = -1;
        stop = false;
    }

    public Slideshow(Context context, AttributeSet attrs) {
        super(context, attrs);
        oldId = -1;
        stop = false;
    }

    public Slideshow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        oldId = -1;
        stop = false;
    }

    synchronized File[] getIds() {
        return ids;
    }

    synchronized void setIds(File[] ids) {
        this.ids = ids;
        if (Build.VERSION.SDK_INT > 22) {
            this.setImageDrawable(new ColorDrawable(getContext().getColor(R.color.transparent)));
        } else {
            this.setImageDrawable(new ColorDrawable(getContext().getResources().getColor(R.color.transparent)));
        }
        if (!isSet && !stop) switchPic();
        isSet = true;
    }

    public synchronized void addId(File id) {
        if (ids != null && ids.length > 0) {
            File[] out = new File[ids.length + 1];
            System.arraycopy(ids, 0, out, 0, ids.length);
            out[ids.length] = id;
            setIds(out);
        } else {
            setIds(new File[]{id});
        }
    }

    private synchronized void switchPic() {
        int tmpId = oldId;
        final File f = choosePic();
        if (tmpId == oldId) return;
        if (f == null || !f.exists() || !f.isFile()) return;
        this.animate().alpha(0f).setDuration(500).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {}
            @Override
            public void onAnimationEnd(Animator animator) {
                Slideshow.this.setImageURI(Uri.fromFile(f));
                isSet = true;
                Slideshow.this.animate().alpha(1f).setDuration(500).setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {}

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        if (switcher == null) {
                            switcher = new RefreshHandler();
                        }
                        switcher.sleep(new Random().nextInt(5000) + 2000);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {}
                    @Override
                    public void onAnimationRepeat(Animator animator) {}
                }).start();
            }
            @Override
            public void onAnimationCancel(Animator animator) {}
            @Override
            public void onAnimationRepeat(Animator animator) {}
        }).start();
    }

    private synchronized File choosePic() {
        int newId = oldId + 1;
        if (getIds() != null) {
            if (newId == getIds().length) newId = 0;
            oldId = newId;
            return getIds()[newId];
        }
        return null;
    }

    public synchronized void stop() {
        this.stop = true;
        if (this.switcher != null)
            this.switcher.removeMessages(0);
    }

    public synchronized void start() {
        this.stop = false;
        if (this.switcher == null) {
            this.switcher = new RefreshHandler();
        }
        this.switcher.sleep(new Random().nextInt(5000) + 2000);
    }


    class RefreshHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (Slideshow.this.stop) return;
            Slideshow.this.switchPic();
        }
        public void sleep(long delayMillis){
            this.removeMessages(0);
            sendMessageDelayed(obtainMessage(0), delayMillis);
        }
    }
}

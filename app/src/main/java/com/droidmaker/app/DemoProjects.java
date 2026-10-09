package com.droidmaker.app;

import android.content.Context;
import android.graphics.Bitmap;

import com.droidmaker.core.demo.Demos;
import com.droidmaker.core.demo.Img;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Map;

/** Writes the bundled demo projects (built in core) to the project folder. */
final class DemoProjects {
    private DemoProjects() {}

    static void createAll(Context c, ProjectStore store) throws Exception {
        write(store, Demos.platformer());
        write(store, Demos.raycast());
        write(store, Demos.world3d());
    }

    private static void write(ProjectStore store, Demos.Demo d) throws Exception {
        File dir = store.uniqueDir(d.project.name);
        dir.mkdirs();
        d.project.dir = dir;
        for (Map.Entry<String, Img> e : d.images.entrySet()) {
            Img im = e.getValue();
            Bitmap b = Bitmap.createBitmap(im.px, im.w, im.h, Bitmap.Config.ARGB_8888);
            ImageIO.savePng(b, new File(dir, e.getKey()));
            b.recycle();
        }
        for (Map.Entry<String, byte[]> e : d.files.entrySet()) {
            File f = new File(dir, e.getKey());
            f.getParentFile().mkdirs();
            try (OutputStream o = new FileOutputStream(f)) { o.write(e.getValue()); }
        }
        d.project.save();
    }
}

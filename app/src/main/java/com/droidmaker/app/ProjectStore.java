package com.droidmaker.app;

import com.droidmaker.core.json.Json;
import com.droidmaker.core.project.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Projects on disk: each project is a folder with project.json plus img/ and snd/. */
public final class ProjectStore {
    public final File root;

    public static final class Entry {
        public final File dir;
        public final String name;
        public final long modified;
        public final int objects, rooms;

        Entry(File dir, String name, long modified, int objects, int rooms) {
            this.dir = dir; this.name = name; this.modified = modified; this.objects = objects; this.rooms = rooms;
        }
    }

    ProjectStore(File root) {
        this.root = root;
        root.mkdirs();
    }

    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        File[] dirs = root.listFiles();
        if (dirs == null) return out;
        for (File d : dirs) {
            File f = new File(d, Project.PROJECT_FILE);
            if (!f.exists()) continue;
            String name = d.getName();
            int objs = 0, rooms = 0;
            try {
                Map<String, Object> m = Json.obj(Json.parse(Project.readText(f)));
                name = Json.str(m, "name", name);
                for (Object o : Json.arr(m.get("objects"))) if (o != null) objs++;
                for (Object o : Json.arr(m.get("rooms"))) if (o != null) rooms++;
            } catch (Exception ignored) {
                // still list damaged projects so they can be deleted
            }
            out.add(new Entry(d, name, f.lastModified(), objs, rooms));
        }
        out.sort((a, b) -> Long.compare(b.modified, a.modified));
        return out;
    }

    static String safe(String name) {
        String s = name.replaceAll("[^A-Za-z0-9 _-]", "").trim().replace(' ', '_');
        return s.isEmpty() ? "project" : s;
    }

    public File uniqueDir(String name) {
        String base = safe(name);
        File d = new File(root, base);
        int i = 2;
        while (d.exists()) d = new File(root, base + "_" + i++);
        return d;
    }

    public Project create(String name) throws IOException {
        Project p = new Project();
        p.name = name;
        p.dir = uniqueDir(name);
        p.dir.mkdirs();
        new File(p.dir, "img").mkdirs();
        new File(p.dir, "snd").mkdirs();
        p.newRoom();
        p.save();
        return p;
    }

    public void delete(File dir) { deleteRec(dir); }

    static void deleteRec(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }

    public File duplicate(File dir, String newName) throws IOException {
        File d = uniqueDir(newName);
        copyRec(dir, d);
        Project p = Project.load(d);
        p.name = newName;
        p.save();
        return d;
    }

    public void rename(File dir, String newName) throws IOException {
        Project p = Project.load(dir);
        p.name = newName;
        p.save();
    }

    static void copyRec(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyRec(k, new File(dst, k.getName()));
        } else {
            try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }

    /** Writes the project folder as a .zip (".dmk") archive. */
    public void export(File dir, OutputStream os) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(os)) {
            addZip(zip, dir, "");
        }
    }

    private void addZip(ZipOutputStream zip, File f, String path) throws IOException {
        File[] kids = f.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            String p = path + k.getName();
            if (k.isDirectory()) addZip(zip, k, p + "/");
            else {
                zip.putNextEntry(new ZipEntry(p));
                try (InputStream in = new FileInputStream(k)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
                }
                zip.closeEntry();
            }
        }
    }

    /** Imports a project archive; returns the new project folder. */
    public File importZip(InputStream is) throws IOException {
        File tmp = uniqueDir("imported");
        tmp.mkdirs();
        String canon = tmp.getCanonicalPath();
        try (ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = zip.getNextEntry()) != null) {
                File out = new File(tmp, e.getName());
                if (!out.getCanonicalPath().startsWith(canon + File.separator)) continue; // zip slip guard
                if (e.isDirectory()) { out.mkdirs(); continue; }
                out.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
        }
        // allow archives that contain a single top-level folder
        if (!new File(tmp, Project.PROJECT_FILE).exists()) {
            File[] kids = tmp.listFiles();
            if (kids != null && kids.length == 1 && new File(kids[0], Project.PROJECT_FILE).exists()) {
                File inner = kids[0];
                File fixed = uniqueDir(inner.getName());
                inner.renameTo(fixed);
                deleteRec(tmp);
                tmp = fixed;
            }
        }
        if (!new File(tmp, Project.PROJECT_FILE).exists()) {
            deleteRec(tmp);
            throw new IOException("This file is not a DroidMaker project");
        }
        Project p = Project.load(tmp);
        File want = uniqueDir(p.name);
        if (!want.getName().equals(tmp.getName()) && tmp.renameTo(want)) tmp = want;
        return tmp;
    }
}

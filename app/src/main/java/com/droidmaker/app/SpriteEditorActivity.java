package com.droidmaker.app;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.droidmaker.core.project.Background;
import com.droidmaker.core.project.ResType;
import com.droidmaker.core.project.Sprite;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Pixel editor for sprites (animated, with origin and collision mask) and backgrounds. */
public final class SpriteEditorActivity extends BaseActivity implements PixelEditorView.Listener {
    private static final int REQ_IMPORT = 1, REQ_STRIP = 2;
    private boolean isBg;
    private Sprite sprite;
    private Background bg;
    private final List<Bitmap> frames = new ArrayList<>();
    private int frame;
    private int w, h;
    private boolean dirty;
    private PixelEditorView canvas;
    private LinearLayout toolRow, frameRow, bar;
    private View colorSwatch;
    private TextView brushChip;
    private boolean onionSkin;
    private final List<List<Bitmap>> undoStack = new ArrayList<>(), redoStack = new ArrayList<>();
    private final List<int[]> undoSize = new ArrayList<>(), redoSize = new ArrayList<>();

    @Override
    protected void build(Bundle state) {
        isBg = ResType.BACKGROUND.name().equals(getIntent().getStringExtra("type"));
        int id = getIntent().getIntExtra("id", -1);
        if (isBg) bg = p.background(id); else sprite = p.sprite(id);
        if (bg == null && sprite == null) { finish(); return; }

        if (isBg) {
            w = bg.width; h = bg.height;
            frames.add(ImageIO.loadMutable(p.file(bg.file), w, h));
        } else {
            w = sprite.width; h = sprite.height;
            for (String f : sprite.frames) frames.add(ImageIO.loadMutable(p.file(f), w, h));
            if (frames.isEmpty()) { frames.add(blank()); dirty = true; }
        }

        LinearLayout root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);
        bar = Ui.toolbar(this, name(), true);
        bar.findViewWithTag("title").setOnClickListener(v -> rename());
        bar.addView(Ui.icon(this, "↶", v -> undo()));
        bar.addView(Ui.icon(this, "↷", v -> redo()));
        bar.addView(Ui.icon(this, "⋮", v -> menu()));
        root.addView(bar);

        canvas = new PixelEditorView(this);
        canvas.setListener(this);
        root.addView(canvas, Ui.lpw(Ui.MATCH, 0, 1));

        toolRow = Ui.row(this);
        toolRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        HorizontalScrollView ts = Ui.hscroll(this, toolRow);
        ts.setBackgroundColor(Ui.PANEL);
        root.addView(ts);

        if (!isBg) {
            frameRow = Ui.row(this);
            frameRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            HorizontalScrollView fs = Ui.hscroll(this, frameRow);
            fs.setBackgroundColor(Ui.PANEL2);
            root.addView(fs);
        }
        setContentView(root);
        canvas.setColor(lastColor);
        buildTools();
        showFrame(0);
    }

    private static int lastColor = 0xFF000000;

    private String name() { return isBg ? bg.name : sprite.name; }

    private Bitmap blank() { return Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888); }

    // ================================================================= tools
    private PixelEditorView.Tool[] TOOLS;
    private final List<TextView> toolButtons = new ArrayList<>();

    private void buildTools() {
        toolRow.removeAllViews();
        toolButtons.clear();
        colorSwatch = Ui.swatch(this, canvas.color(), v -> Dialogs.color(this, canvas.color(), true, c -> setColor(c)));
        LinearLayout.LayoutParams sp = Ui.lp(Ui.dp(this, 40), Ui.dp(this, 40));
        sp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        toolRow.addView(colorSwatch, sp);
        TOOLS = isBg || true ? PixelEditorView.Tool.values() : null;
        String[] glyphs = {"✏", "⌫", "╱", "▭", "■", "◯", "●", "🪣", "💧", "✥", "⊕"};
        for (int i = 0; i < TOOLS.length; i++) {
            PixelEditorView.Tool t = TOOLS[i];
            if (t == PixelEditorView.Tool.ORIGIN && isBg) continue;
            TextView b = Ui.icon(this, glyphs[i], v -> selectTool(t));
            b.setTag(t);
            toolButtons.add(b);
            toolRow.addView(b);
        }
        brushChip = Ui.chip(this, canvas.brush() + "px", false, v -> {
            String[] sizes = {"1px", "2px", "3px", "4px", "6px", "8px", "12px", "16px"};
            Dialogs.choose(this, "Brush size", sizes, k -> {
                canvas.setBrush(Integer.parseInt(sizes[k].replace("px", "")));
                brushChip.setText(sizes[k]);
            });
        });
        toolRow.addView(brushChip);
        TextView mirror = Ui.chip(this, "Mirror", false, null);
        mirror.setOnClickListener(v -> { canvas.setMirror(!canvas.mirror()); Ui.setChip(this, mirror, canvas.mirror()); });
        toolRow.addView(mirror);
        TextView grid = Ui.chip(this, "Grid", true, null);
        grid.setOnClickListener(v -> { canvas.setGrid(!canvas.grid()); Ui.setChip(this, grid, canvas.grid()); });
        toolRow.addView(grid);
        toolRow.addView(Ui.chip(this, "Fit", false, v -> canvas.fit()));
        selectTool(PixelEditorView.Tool.PENCIL);
    }

    private void selectTool(PixelEditorView.Tool t) {
        canvas.setTool(t);
        for (TextView b : toolButtons) b.setBackground(b.getTag() == t ? Ui.round(Ui.ACCENT, Ui.dp(this, 8)) : null);
        if (!isBg) canvas.setOrigin(sprite.originX, sprite.originY);
        if (t == PixelEditorView.Tool.ORIGIN) Dialogs.toast(this, "Tap where the origin should be");
    }

    private void setColor(int c) {
        canvas.setColor(c);
        lastColor = c;
        colorSwatch.setBackground(Ui.roundStroke(c, 0xFFFFFFFF, Ui.dp(this, 2), Ui.dp(this, 8)));
    }

    // ================================================================= frames
    private void showFrame(int i) {
        frame = Math.max(0, Math.min(frames.size() - 1, i));
        canvas.setImage(frames.get(frame));
        canvas.setOnion(onionSkin && frame > 0 ? frames.get(frame - 1) : null);
        if (!isBg) {
            canvas.setOrigin(sprite.originX, sprite.originY);
            canvas.setBBox(sprite.maskMode == Sprite.MASK_MANUAL ? new int[]{sprite.bboxLeft, sprite.bboxTop, sprite.bboxRight, sprite.bboxBottom} : null);
        }
        refreshFrames();
    }

    private void refreshFrames() {
        if (frameRow == null) return;
        frameRow.removeAllViews();
        int s = Ui.dp(this, 52);
        for (int i = 0; i < frames.size(); i++) {
            int k = i;
            ImageView iv = new ImageView(this);
            BitmapDrawable bd = new BitmapDrawable(getResources(), frames.get(i));
            bd.setFilterBitmap(false);
            iv.setImageDrawable(bd);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(4, 4, 4, 4);
            iv.setBackground(Ui.roundStroke(Ui.PANEL3, i == frame ? Ui.ACCENT : Ui.LINE, Ui.dp(this, i == frame ? 3 : 1), Ui.dp(this, 6)));
            iv.setOnClickListener(v -> showFrame(k));
            iv.setOnLongClickListener(v -> { frameMenu(k); return true; });
            LinearLayout.LayoutParams lp = Ui.lp(s, s);
            lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            frameRow.addView(iv, lp);
        }
        TextView add = Ui.icon(this, "+", v -> addFrame(false));
        add.setTextColor(Ui.ACCENT);
        frameRow.addView(add);
        frameRow.addView(Ui.icon(this, "⧉", v -> addFrame(true)));
        frameRow.addView(Ui.icon(this, "▶", v -> preview()));
        TextView onion = Ui.chip(this, "Onion", onionSkin, null);
        onion.setOnClickListener(v -> { onionSkin = !onionSkin; showFrame(frame); });
        frameRow.addView(onion);
        TextView info = Ui.text(this, "  " + (frame + 1) + "/" + frames.size() + "  " + w + "×" + h, 13, Ui.DIM);
        frameRow.addView(info);
    }

    private void addFrame(boolean copy) {
        pushUndo();
        Bitmap b = copy ? frames.get(frame).copy(Bitmap.Config.ARGB_8888, true) : blank();
        frames.add(frame + 1, b);
        dirty = true;
        showFrame(frame + 1);
    }

    private void frameMenu(int k) {
        Dialogs.choose(this, "Frame " + (k + 1), new String[]{"Duplicate", "Move left", "Move right", "Delete"}, w2 -> {
            pushUndo();
            switch (w2) {
                case 0: frames.add(k + 1, frames.get(k).copy(Bitmap.Config.ARGB_8888, true)); showFrame(k + 1); break;
                case 1: if (k > 0) { java.util.Collections.swap(frames, k, k - 1); showFrame(k - 1); } break;
                case 2: if (k < frames.size() - 1) { java.util.Collections.swap(frames, k, k + 1); showFrame(k + 1); } break;
                case 3:
                    if (frames.size() <= 1) { Dialogs.toast(this, "A sprite needs at least one frame"); return; }
                    frames.remove(k);
                    showFrame(Math.min(k, frames.size() - 1));
                    break;
            }
            dirty = true;
        });
    }

    private void preview() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(0xFF2A2F3A);
        int size = Ui.dp(this, 260);
        Handler hnd = new Handler(Looper.getMainLooper());
        double[] pos = {0};
        Runnable[] tick = new Runnable[1];
        tick[0] = () -> {
            int f = ((int) Math.floor(pos[0])) % frames.size();
            BitmapDrawable bd = new BitmapDrawable(getResources(), frames.get(f));
            bd.setFilterBitmap(false);
            iv.setImageDrawable(bd);
            pos[0] += Math.max(0.01, sprite.previewSpeed);
            hnd.postDelayed(tick[0], 16);
        };
        AlertDialog d = Dialogs.builder(this).setTitle("Animation preview").setView(iv)
                .setPositiveButton("Close", null)
                .setNeutralButton("Speed…", null).create();
        d.setOnDismissListener(x -> hnd.removeCallbacks(tick[0]));
        d.show();
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> Dialogs.input(this, "Frames per step (0.25 = 15 fps at 60 steps/s)",
                Dialogs.fmt(sprite.previewSpeed), "", true, s -> {
                    try { sprite.previewSpeed = Math.max(0.01, Double.parseDouble(s)); } catch (Exception ignored) { /* ignore */ }
                }));
        iv.setLayoutParams(new android.widget.FrameLayout.LayoutParams(size, size));
        hnd.post(tick[0]);
    }

    // ================================================================= undo
    private List<Bitmap> snapshotFrames() {
        List<Bitmap> l = new ArrayList<>();
        for (Bitmap b : frames) l.add(b.copy(Bitmap.Config.ARGB_8888, true));
        return l;
    }

    private void pushUndo() {
        undoStack.add(snapshotFrames());
        undoSize.add(new int[]{w, h, frame});
        redoStack.clear();
        redoSize.clear();
        trim();
    }

    private void trim() {
        long bytes = 0;
        for (List<Bitmap> l : undoStack) for (Bitmap b : l) bytes += b.getByteCount();
        while (undoStack.size() > 40 || (bytes > 48L * 1024 * 1024 && undoStack.size() > 1)) {
            for (Bitmap b : undoStack.get(0)) bytes -= b.getByteCount();
            undoStack.remove(0);
            undoSize.remove(0);
        }
    }

    @Override
    public void beforeEdit(Bitmap before) {
        List<Bitmap> l = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) l.add(i == frame ? before.copy(Bitmap.Config.ARGB_8888, true) : frames.get(i).copy(Bitmap.Config.ARGB_8888, true));
        undoStack.add(l);
        undoSize.add(new int[]{w, h, frame});
        redoStack.clear();
        redoSize.clear();
        trim();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.add(snapshotFrames());
        redoSize.add(new int[]{w, h, frame});
        restore(undoStack.remove(undoStack.size() - 1), undoSize.remove(undoSize.size() - 1));
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.add(snapshotFrames());
        undoSize.add(new int[]{w, h, frame});
        restore(redoStack.remove(redoStack.size() - 1), redoSize.remove(redoSize.size() - 1));
    }

    private void restore(List<Bitmap> l, int[] size) {
        frames.clear();
        frames.addAll(l);
        w = size[0];
        h = size[1];
        dirty = true;
        showFrame(size[2]);
    }

    @Override public void edited() { dirty = true; refreshFrames(); }

    @Override public void picked(int c) {
        setColor(c);
        selectTool(PixelEditorView.Tool.PENCIL);
    }

    @Override public void origin(int x, int y) {
        if (isBg) return;
        sprite.originX = x;
        sprite.originY = y;
        canvas.setOrigin(x, y);
        dirty = true;
    }

    // ================================================================= menu
    private void rename() {
        Dialogs.name(this, p, "Rename", name(), isBg ? bg : sprite, s -> {
            p.rename(isBg ? bg : sprite, s);
            Ui.setToolbarTitle(bar, s);
        });
    }

    private void menu() {
        List<String> items = new ArrayList<>();
        java.util.Collections.addAll(items, "Import image(s) from gallery", "Import animation strip", "Resize canvas", "Scale image",
                "Flip horizontally", "Flip vertically", "Rotate 90°", "Clear frame", "Replace color");
        items.add(isBg ? "Background properties" : "Origin & collision mask");
        Dialogs.choose(this, name(), items.toArray(new String[0]), k -> {
            switch (items.get(k)) {
                case "Import image(s) from gallery": pick(REQ_IMPORT); break;
                case "Import animation strip": pick(REQ_STRIP); break;
                case "Resize canvas": resize(false); break;
                case "Scale image": resize(true); break;
                case "Flip horizontally": transform(-1, 1, 0); break;
                case "Flip vertically": transform(1, -1, 0); break;
                case "Rotate 90°": transform(1, 1, 90); break;
                case "Clear frame": pushUndo(); frames.get(frame).eraseColor(0); dirty = true; showFrame(frame); break;
                case "Replace color": replaceColor(); break;
                default: if (isBg) bgProperties(); else spriteProperties();
            }
        });
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (req == REQ_IMPORT && !isBg) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(i, "Choose image"), req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData cd = data.getClipData();
        if (cd != null) for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
        else if (data.getData() != null) uris.add(data.getData());
        List<Bitmap> imgs = new ArrayList<>();
        for (Uri u : uris) {
            try (InputStream in = getContentResolver().openInputStream(u)) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inScaled = false;
                Bitmap b = BitmapFactory.decodeStream(in, null, o);
                if (b != null) {
                    if (b.getWidth() > 2048 || b.getHeight() > 2048) {
                        float f = 2048f / Math.max(b.getWidth(), b.getHeight());
                        b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * f), Math.round(b.getHeight() * f), true);
                    }
                    imgs.add(b.copy(Bitmap.Config.ARGB_8888, true));
                }
            } catch (Exception e) {
                Dialogs.toast(this, "Could not read image");
            }
        }
        if (imgs.isEmpty()) return;
        if (req == REQ_STRIP) importStrip(imgs.get(0));
        else importImages(imgs);
    }

    private boolean onlyBlank() {
        if (frames.size() != 1) return false;
        Bitmap b = frames.get(0);
        int[] px = new int[b.getWidth() * b.getHeight()];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        for (int c : px) if ((c >>> 24) != 0) return false;
        return true;
    }

    private void importImages(List<Bitmap> imgs) {
        Bitmap first = imgs.get(0);
        boolean sameSize = first.getWidth() == w && first.getHeight() == h;
        Runnable useImageSize = () -> {
            pushUndo();
            boolean replace = onlyBlank() || isBg;
            if (replace) frames.clear();
            else for (int i = 0; i < frames.size(); i++) frames.set(i, resizeCanvas(frames.get(i), first.getWidth(), first.getHeight(), false));
            w = first.getWidth();
            h = first.getHeight();
            for (Bitmap b : imgs) frames.add(resizeCanvas(b, w, h, true));
            finishImport(replace);
        };
        Runnable keepSize = () -> {
            pushUndo();
            boolean replace = onlyBlank() || isBg;
            if (replace) frames.clear();
            for (Bitmap b : imgs) frames.add(resizeCanvas(b, w, h, true));
            finishImport(replace);
        };
        if (sameSize) { keepSize.run(); return; }
        if (onlyBlank() || isBg) { useImageSize.run(); return; }
        Dialogs.builder(this).setTitle("Image size differs")
                .setMessage("The image is " + first.getWidth() + "×" + first.getHeight() + " but the sprite is " + w + "×" + h + ".")
                .setPositiveButton("Use image size", (d, x) -> useImageSize.run())
                .setNegativeButton("Scale to sprite", (d, x) -> keepSize.run())
                .show();
    }

    private void finishImport(boolean replaced) {
        dirty = true;
        if (!isBg && replaced) { sprite.originX = Math.min(sprite.originX, w); sprite.originY = Math.min(sprite.originY, h); }
        showFrame(replaced ? 0 : frames.size() - 1);
        canvas.fit();
    }

    private void importStrip(Bitmap img) {
        LinearLayout f = Ui.col(this);
        EditText fw = Dialogs.numberField(this, Math.min(img.getWidth(), img.getHeight()));
        EditText fh = Dialogs.numberField(this, img.getHeight());
        EditText count = Dialogs.numberField(this, Math.max(1, img.getWidth() / Math.max(1, img.getHeight())));
        EditText cols = Dialogs.numberField(this, Math.max(1, img.getWidth() / Math.max(1, img.getHeight())));
        f.addView(Ui.labeled(this, "Frame width", fw));
        f.addView(Ui.labeled(this, "Frame height", fh));
        f.addView(Ui.labeled(this, "Number of frames", count));
        f.addView(Ui.labeled(this, "Frames per row", cols));
        TextView note = Ui.text(this, "Image is " + img.getWidth() + "×" + img.getHeight() + ". Frames are read left to right, top to bottom.", 13, Ui.DIM);
        note.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 8));
        f.addView(note);
        Dialogs.builder(this).setTitle("Import strip").setView(Ui.scroll(this, f))
                .setPositiveButton("Import", (d, x) -> {
                    int cw = Math.max(1, Dialogs.inum(fw, 32)), ch = Math.max(1, Dialogs.inum(fh, 32));
                    int n = Math.max(1, Dialogs.inum(count, 1)), c = Math.max(1, Dialogs.inum(cols, n));
                    pushUndo();
                    frames.clear();
                    w = cw;
                    h = ch;
                    for (int i = 0; i < n; i++) {
                        int sx = (i % c) * cw, sy = (i / c) * ch;
                        Bitmap b = blank();
                        new Canvas(b).drawBitmap(img, -sx, -sy, null);
                        frames.add(b);
                    }
                    finishImport(true);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private Bitmap resizeCanvas(Bitmap src, int nw, int nh, boolean scale) {
        Bitmap out = Bitmap.createBitmap(Math.max(1, nw), Math.max(1, nh), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (scale && (src.getWidth() != nw || src.getHeight() != nh)) {
            Paint pt = new Paint();
            pt.setFilterBitmap(false);
            Matrix m = new Matrix();
            m.setScale(nw / (float) src.getWidth(), nh / (float) src.getHeight());
            c.drawBitmap(src, m, pt);
        } else c.drawBitmap(src, 0, 0, null);
        return out;
    }

    private void resize(boolean scale) {
        LinearLayout f = Ui.col(this);
        EditText ew = Dialogs.numberField(this, w), eh = Dialogs.numberField(this, h);
        f.addView(Ui.labeled(this, "Width", ew));
        f.addView(Ui.labeled(this, "Height", eh));
        Dialogs.builder(this).setTitle(scale ? "Scale image" : "Resize canvas").setView(f)
                .setPositiveButton("OK", (d, x) -> {
                    int nw = Math.max(1, Math.min(2048, Dialogs.inum(ew, w))), nh = Math.max(1, Math.min(2048, Dialogs.inum(eh, h)));
                    pushUndo();
                    for (int i = 0; i < frames.size(); i++) frames.set(i, resizeCanvas(frames.get(i), nw, nh, scale));
                    if (!isBg && scale) { sprite.originX = sprite.originX * nw / Math.max(1, w); sprite.originY = sprite.originY * nh / Math.max(1, h); }
                    w = nw;
                    h = nh;
                    dirty = true;
                    showFrame(frame);
                    canvas.fit();
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void transform(int sx, int sy, int rot) {
        pushUndo();
        Bitmap src = frames.get(frame);
        Matrix m = new Matrix();
        if (rot != 0) m.setRotate(rot);
        else m.setScale(sx, sy);
        Bitmap t = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, false);
        frames.set(frame, resizeCanvas(t, w, h, false));
        dirty = true;
        showFrame(frame);
    }

    private void replaceColor() {
        Dialogs.toast(this, "Pick the color to replace, then the new color");
        Dialogs.color(this, canvas.color(), true, from -> Dialogs.color(this, from, true, to -> {
            pushUndo();
            for (Bitmap b : frames) {
                int[] px = new int[b.getWidth() * b.getHeight()];
                b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
                for (int i = 0; i < px.length; i++) if (px[i] == from) px[i] = to;
                b.setPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
            }
            dirty = true;
            showFrame(frame);
        }));
    }

    private void spriteProperties() {
        LinearLayout f = Ui.col(this);
        EditText ox = Dialogs.numberField(this, sprite.originX), oy = Dialogs.numberField(this, sprite.originY);
        f.addView(Ui.section(this, "Origin"));
        f.addView(Ui.labeled(this, "Origin X", ox));
        f.addView(Ui.labeled(this, "Origin Y", oy));
        LinearLayout quick = Ui.row(this);
        quick.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        quick.addView(Ui.chip(this, "Center", false, v -> { ox.setText(String.valueOf(w / 2)); oy.setText(String.valueOf(h / 2)); }));
        quick.addView(Ui.chip(this, "Top-left", false, v -> { ox.setText("0"); oy.setText("0"); }));
        quick.addView(Ui.chip(this, "Bottom-center", false, v -> { ox.setText(String.valueOf(w / 2)); oy.setText(String.valueOf(h)); }));
        f.addView(quick);
        f.addView(Ui.section(this, "Collision mask"));
        int[] mode = {sprite.maskMode};
        String[] modes = {"Automatic (tight box)", "Full image", "Manual box"};
        TextView mb = Ui.valueButton(this, modes[mode[0]], null);
        mb.setOnClickListener(v -> Dialogs.choose(this, "Mask", modes, k -> { mode[0] = k; Ui.setValue(mb, modes[k]); }));
        f.addView(Ui.labeled(this, "Mask", mb));
        EditText bl = Dialogs.numberField(this, sprite.bboxLeft), bt = Dialogs.numberField(this, sprite.bboxTop);
        EditText br = Dialogs.numberField(this, sprite.bboxRight), bb = Dialogs.numberField(this, sprite.bboxBottom);
        f.addView(Ui.labeled(this, "Left (manual)", bl));
        f.addView(Ui.labeled(this, "Top (manual)", bt));
        f.addView(Ui.labeled(this, "Right (manual)", br));
        f.addView(Ui.labeled(this, "Bottom (manual)", bb));
        Switch circle = Ui.toggle(this, sprite.circleMask, null);
        f.addView(Ui.labeled(this, "Round (circle) collisions", circle));
        Dialogs.builder(this).setTitle("Sprite properties").setView(Ui.scroll(this, f))
                .setPositiveButton("OK", (d, x) -> {
                    sprite.originX = Dialogs.inum(ox, sprite.originX);
                    sprite.originY = Dialogs.inum(oy, sprite.originY);
                    sprite.maskMode = mode[0];
                    sprite.bboxLeft = Dialogs.inum(bl, 0);
                    sprite.bboxTop = Dialogs.inum(bt, 0);
                    sprite.bboxRight = Dialogs.inum(br, w - 1);
                    sprite.bboxBottom = Dialogs.inum(bb, h - 1);
                    sprite.circleMask = circle.isChecked();
                    dirty = true;
                    showFrame(frame);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void bgProperties() {
        LinearLayout f = Ui.col(this);
        Switch ts = Ui.toggle(this, bg.tileset, null);
        EditText tw = Dialogs.numberField(this, bg.tileWidth), th = Dialogs.numberField(this, bg.tileHeight);
        f.addView(Ui.labeled(this, "Use as tileset", ts));
        f.addView(Ui.labeled(this, "Tile width", tw));
        f.addView(Ui.labeled(this, "Tile height", th));
        Dialogs.builder(this).setTitle("Background properties").setView(f)
                .setPositiveButton("OK", (d, x) -> {
                    bg.tileset = ts.isChecked();
                    bg.tileWidth = Math.max(1, Dialogs.inum(tw, 16));
                    bg.tileHeight = Math.max(1, Dialogs.inum(th, 16));
                    App.save();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ================================================================= save
    private void saveImages() {
        if (!dirty) return;
        try {
            if (isBg) {
                String path = ImageIO.newImagePath("bg" + bg.id);
                ImageIO.savePng(frames.get(0), p.file(path));
                bg.file = path;
                bg.width = w;
                bg.height = h;
                bg.revision++;
            } else {
                List<String> paths = new ArrayList<>();
                for (Bitmap b : frames) {
                    String path = ImageIO.newImagePath("spr" + sprite.id);
                    ImageIO.savePng(b, p.file(path));
                    paths.add(path);
                }
                sprite.frames = paths;
                sprite.width = w;
                sprite.height = h;
                if (sprite.maskMode == Sprite.MASK_AUTO) {
                    int[] bb = ImageIO.autoBBox(frames, w, h);
                    sprite.bboxLeft = bb[0]; sprite.bboxTop = bb[1]; sprite.bboxRight = bb[2]; sprite.bboxBottom = bb[3];
                } else if (sprite.maskMode == Sprite.MASK_FULL) {
                    sprite.bboxLeft = 0; sprite.bboxTop = 0; sprite.bboxRight = w - 1; sprite.bboxBottom = h - 1;
                }
                sprite.revision++;
            }
            dirty = false;
            App.save();
            ImageIO.cleanUnused(p);
        } catch (Exception e) {
            Dialogs.toast(this, "Saving image failed: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveImages();
        App.save();
    }
}

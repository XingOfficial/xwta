package cn.xing.wtalite;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Intent;
import android.net.Uri;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivityNative extends Activity {
    static final int T_TITLE = 0, T_TEXT = 1, T_IMAGE = 2, T_DIVIDER = 3, T_EDIT = 4, T_BUTTON = 5;

    static class Block {
        int type; CharSequence text; float size; boolean bold;
        String url; String name; String hint; boolean pw; String label;
        String formAction; String formMethod;
    }

    private final ArrayList<Block> blocks = new ArrayList<>();
    private final HashMap<String, String> formValues = new HashMap<>();
    private final ArrayList<String> history = new ArrayList<>();
    private RecyclerView rv;
    private String currentUrl;
    private int imgCount;
    private RecyclerView.Adapter ad;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String trace = sw.toString();
                try {
                    java.io.FileWriter w = new java.io.FileWriter(
                            new java.io.File(getExternalFilesDir(null), "crash.txt"));
                    w.write(trace); w.close();
                } catch (Exception ignored) {}
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,
                            "crash_" + System.currentTimeMillis() + ".txt");
                    cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
                    android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri != null) {
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        if (os != null) { os.write(trace.getBytes()); os.close(); }
                    }
                }
            } catch (Exception ignored) {}
            if (prev != null) prev.uncaughtException(t, e);
        });
        super.onCreate(savedInstanceState);
        int id = getResources().getIdentifier("xwta_url", "string", getPackageName());
        String url = id != 0 ? getString(id) : "https://example.com";
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setBackgroundColor(0xFFFFFFFF);
        setContentView(rv);
        load(url);
    }

    @Override
    public void onBackPressed() {
        if (history.size() > 1) {
            history.remove(history.size() - 1);
            load(history.get(history.size() - 1), false);
        } else {
            super.onBackPressed();
        }
    }

    private void load(String url) { load(url, true); }

    private void load(final String url, boolean push) {
        if (push && currentUrl != null && !currentUrl.equals(url)) history.add(currentUrl);
        currentUrl = url;
        imgCount = 0;
        blocks.clear();
        formValues.clear();
        ad = null;
        showStatus("加载中… " + url);
        new Thread(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .timeout(20000).followRedirects(true).get();
                doc.select("script,style,noscript,iframe,meta,link,head").remove();
                Element root = doc.selectFirst("article");
                if (root == null) root = doc.selectFirst("[role=main]");
                if (root == null) root = doc.selectFirst("#content");
                if (root == null) root = doc.body();
                int textLen = root != null ? root.text().trim().length() : 0;
                if (textLen < 60) {
                    runOnUiThread(this::fallbackWebView);
                    return;
                }
                renderDoc(doc, url);
            } catch (Exception e) {
                String msg = "加载失败: " + e;
                runOnUiThread(() -> {
                    blocks.clear();
                    Block b = new Block(); b.type = T_TEXT; b.text = msg + "\n\n点击重试"; b.size = 16;
                    blocks.add(b);
                    ad = new Adapter();
                    rv.setAdapter(ad);
                });
            }
        }).start();
    }

    private void showStatus(String s) {
        Block b = new Block(); b.type = T_TEXT; b.text = s; b.size = 14;
        blocks.add(b);
        ad = new Adapter();
        rv.setAdapter(ad);
    }

    /** 解析渲染文档（GET/POST共用） */
    private void renderDoc(Document doc, String base) {
        doc.select("script,style,noscript,iframe,meta,link,head").remove();
        Element root = doc.selectFirst("article");
        if (root == null) root = doc.selectFirst("[role=main]");
        if (root == null) root = doc.selectFirst("#content");
        if (root == null) root = doc.body();
        String title = doc.title();
        int textLen = root != null ? root.text().trim().length() : 0;
        if (textLen < 60) { runOnUiThread(this::fallbackWebView); return; }
        final Element r = root;
        final String fTitle = title;
        runOnUiThread(() -> {
            blocks.clear();
            if (!fTitle.isEmpty()) {
                Block tb = new Block(); tb.type = T_TITLE; tb.text = fTitle;
                blocks.add(tb);
            }
            collect(r, base);
            ad = new Adapter();
            rv.setAdapter(ad);
        });
    }

    /** POST表单提交：Jsoup执行POST，响应页应用内渲染 */
    private void submitPost(final String action) {
        ToastShort("提交中…");
        new Thread(() -> {
            try {
                org.jsoup.Connection conn = Jsoup.connect(action)
                        .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        .timeout(20000).followRedirects(true);
                for (HashMap.Entry<String, String> en : formValues.entrySet()) {
                    conn.data(en.getKey(), en.getValue());
                }
                org.jsoup.Connection.Response resp = conn.method(
                        org.jsoup.Connection.Method.POST).execute();
                if (currentUrl != null) history.add(currentUrl);
                currentUrl = action;
                Document doc = Jsoup.parse(resp.body(), resp.url().toString());
                renderDoc(doc, resp.url().toString());
            } catch (Exception e) {
                String msg = "提交失败: " + e;
                runOnUiThread(() -> ToastShort(msg));
            }
        }).start();
    }

    private void fallbackWebView() {
        android.webkit.WebView wv = new android.webkit.WebView(this);
        android.webkit.WebSettings st = wv.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        wv.setWebViewClient(new android.webkit.WebViewClient());
        wv.setDownloadListener((u, ua, cd, mime, len) -> downloadFile(u));
        wv.setBackgroundColor(0xFFFFFFFF);
        setContentView(wv);
        wv.loadUrl(currentUrl);
    }

    private void collect(Element el, String base) {
        StringBuilder pending = new StringBuilder();
        for (Node n : el.childNodes()) {
            if (n instanceof TextNode) {
                String t = ((TextNode) n).text();
                if (!t.trim().isEmpty()) pending.append(t.trim()).append('\n');
            } else if (n instanceof Element) {
                flushText(pending, base);
                collectElement((Element) n, base);
            }
        }
        flushText(pending, base);
    }

    private void flushText(StringBuilder pending, String base) {
        if (pending.length() > 0) {
            Block b = new Block(); b.type = T_TEXT; b.size = 16;
            b.text = span(pending.toString());
            blocks.add(b);
            pending.setLength(0);
        }
    }

    private SpannableStringBuilder span(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder(s);
        return sb;
    }

    private void collectElement(Element c, String base) {
        String tag = c.tagName().toLowerCase();
        switch (tag) {
            case "script": case "style": case "noscript": case "head":
            case "meta": case "link": case "title": case "template":
                return;
            case "img": addImageBlock(c, base); return;
            case "hr": { Block b = new Block(); b.type = T_DIVIDER; blocks.add(b); return; }
            case "h1": addTextElement(c, base, 26, true); return;
            case "h2": addTextElement(c, base, 22, true); return;
            case "h3": addTextElement(c, base, 19, true); return;
            case "h4": case "h5": case "h6": addTextElement(c, base, 17, true); return;
            case "blockquote": {
                Block b = new Block(); b.type = T_TEXT; b.size = 16;
                b.text = buildSpanned(new SpannableStringBuilder(), c, base, 0);
                b.name = "indent";
                blocks.add(b); return;
            }
            case "pre": {
                Block b = new Block(); b.type = T_TEXT; b.size = 13;
                b.text = c.text(); b.name = "mono";
                blocks.add(b); return;
            }
            case "ul": case "ol": {
                int n = 1;
                for (Element li : c.children()) {
                    if (!li.tagName().toLowerCase().equals("li")) { collect(li, base); continue; }
                    String bullet = tag.equals("ol") ? (n++ + ". ") : "• ";
                    Block b = new Block(); b.type = T_TEXT; b.size = 16;
                    b.text = new SpannableStringBuilder().append(bullet)
                            .append(buildSpanned(new SpannableStringBuilder(), li, base, 0));
                    blocks.add(b);
                }
                return;
            }
            case "table": {
                for (Element tr : c.select("tr")) {
                    StringBuilder row = new StringBuilder();
                    for (Element cell : tr.children()) row.append(cell.text()).append("  |  ");
                    Block b = new Block(); b.type = T_TEXT; b.size = 14; b.text = row.toString();
                    blocks.add(b);
                }
                return;
            }
            case "form": {
                for (Element fc : c.select("input,button,select,textarea")) collectFormControl(fc, c, base);
                return;
            }
            case "input": case "button": case "textarea":
                collectFormControl(c, c.closest("form"), base); return;
            case "a": {
                if (!c.select("img").isEmpty()) { addImageBlock(c, base); return; }
                // 块级裸链接：保留href，渲染为可点击文本（应用内续读）
                addTextElement(c, base, 16, false); return;
            }
            default: collect(c, base);
        }
    }

    private void collectFormControl(Element fc, Element form, String base) {
        String type = fc.attr("type").toLowerCase();
        String tag = fc.tagName().toLowerCase();
        if (type.equals("hidden")) {
            String nm = fc.attr("name");
            if (!nm.isEmpty()) formValues.put(nm, fc.attr("value"));
            return;
        }
        if (tag.equals("input") && (type.equals("text") || type.equals("search")
                || type.equals("email") || type.equals("password") || type.isEmpty())) {
            Block b = new Block(); b.type = T_EDIT;
            b.name = fc.attr("name");
            b.hint = fc.attr("placeholder").isEmpty() ? fc.attr("name") : fc.attr("placeholder");
            b.pw = type.equals("password");
            blocks.add(b);
        } else if (tag.equals("button") || (tag.equals("input") && (type.equals("submit") || type.equals("button")))) {
            Block b = new Block(); b.type = T_BUTTON;
            b.label = !fc.text().isEmpty() ? fc.text() : fc.attr("value");
            if (b.label.isEmpty()) b.label = "提交";
            b.formAction = form != null && !form.attr("action").isEmpty()
                    ? form.absUrl("action") : currentUrl;
            b.formMethod = form != null ? form.attr("method").toLowerCase() : "";
            blocks.add(b);
        } else if (tag.equals("textarea")) {
            Block b = new Block(); b.type = T_EDIT;
            b.name = fc.attr("name"); b.hint = fc.attr("name"); b.pw = false;
            blocks.add(b);
        }
    }

    private void addTextElement(Element c, String base, float size, boolean bold) {
        Block b = new Block(); b.type = T_TEXT; b.size = size; b.bold = bold;
        b.text = buildSpanned(new SpannableStringBuilder(), c, base, bold ? Typeface.BOLD : 0);
        blocks.add(b);
    }

    private void addImageBlock(Element c, String base) {
        if (imgCount >= 15) return;
        String src = c.absUrl("src");
        if (src.isEmpty()) src = c.attr("data-src");
        if (src.isEmpty() || src.startsWith("data:")) return;
        imgCount++;
        Block b = new Block(); b.type = T_IMAGE; b.url = src;
        Element a = c.closest("a");
        b.formAction = a != null ? a.absUrl("href") : "";
        blocks.add(b);
    }

    private SpannableStringBuilder buildSpanned(SpannableStringBuilder sb, Element el, String base, int baseStyle) {
        appendInline(sb, el, base, baseStyle);
        return sb;
    }

    private void appendInline(SpannableStringBuilder sb, Node n, String base, int baseStyle) {
        if (n instanceof TextNode) {
            String t = ((TextNode) n).text();
            if (!t.trim().isEmpty() && sb.length() > 0
                    && !Character.isWhitespace(sb.charAt(sb.length() - 1))) sb.append(' ');
            sb.append(t);
            return;
        }
        if (!(n instanceof Element)) return;
        Element e = (Element) n;
        String tag = e.tagName().toLowerCase();
        if (tag.equals("br")) { sb.append('\n'); return; }
        if (tag.equals("img")) { sb.append("[图片]"); return; }
        if (tag.equals("script") || tag.equals("style")) return;

        int style = baseStyle;
        if (tag.equals("strong") || tag.equals("b") || tag.equals("th")) style |= Typeface.BOLD;
        if (tag.equals("em") || tag.equals("i")) style |= Typeface.ITALIC;
        boolean underline = tag.equals("u") || tag.equals("ins");

        int start = sb.length();
        for (Node c : e.childNodes()) appendInline(sb, c, base, style);
        int end = sb.length();
        if (end > start) {
            if (style != 0) sb.setSpan(new StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (underline) sb.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (tag.equals("a")) {
            String href = e.absUrl("href");
            if (!href.isEmpty() && end > start) {
                final String target = href;
                sb.setSpan(new ClickableSpan() {
                    @Override public void onClick(View v) {
                        if (isDownloadUrl(target)) downloadFile(target);
                        else load(target);
                    }
                    @Override public void updateDrawState(android.text.TextPaint ds) {
                        ds.setColor(0xFF1A73E8);
                        ds.setUnderlineText(true);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    // ---------- 文件下载 ----------
    private static final String DL_EXT = ".apk .zip .rar .7z .pdf .doc .docx .xls .xlsx .ppt .pptx .mp3 .mp4 .txt .epub .mobi .ttf .jpg .png";

    private boolean isDownloadUrl(String u) {
        String low = u.toLowerCase();
        int q = low.indexOf('?');
        if (q > 0) low = low.substring(0, q);
        for (String ext : DL_EXT.split(" ")) {
            if (low.endsWith(ext)) return true;
        }
        return false;
    }

    private void downloadFile(String u) {
        ToastShort("开始下载…");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);
                int code = c.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);
                InputStream in = c.getInputStream();
                String name = u.substring(u.lastIndexOf('/') + 1);
                int q = name.indexOf('?');
                if (q > 0) name = name.substring(0, q);
                if (name.isEmpty()) name = "download_" + System.currentTimeMillis();
                byte[] data = readAll(in);
                in.close();
                final String saved;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                    cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,
                            c.getContentType() != null ? c.getContentType() : "application/octet-stream");
                    android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("无法创建下载条目");
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    os.write(data); os.close();
                    saved = "下载目录/" + name;
                } else {
                    java.io.File d = new java.io.File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DOWNLOADS), "XWTA");
                    d.mkdirs();
                    java.io.File f = new java.io.File(d, name);
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
                    fo.write(data); fo.close();
                    saved = f.getAbsolutePath();
                }
                runOnUiThread(() -> ToastShort("下载完成: " + saved));
            } catch (Exception e) {
                runOnUiThread(() -> ToastShort("下载失败: " + e));
            }
        }).start();
    }

    private byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    private class Adapter extends RecyclerView.Adapter<VH> {
        @Override public VH onCreateViewHolder(ViewGroup p, int vt) {
            switch (vt) {
                case T_TITLE: case T_TEXT: {
                    TextView tv = new TextView(MainActivityNative.this);
                    tv.setPadding(0, 6, 0, 6);
                    tv.setTextIsSelectable(true);
                    return new VH(tv);
                }
                case T_IMAGE: {
                    ImageView iv = new ImageView(MainActivityNative.this);
                    iv.setAdjustViewBounds(true);
                    iv.setPadding(0, 8, 0, 8);
                    return new VH(iv);
                }
                case T_DIVIDER: {
                    View v = new View(MainActivityNative.this);
                    v.setBackgroundColor(0xFFDDDDDD);
                    v.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 2));
                    return new VH(v);
                }
                case T_EDIT: {
                    EditText et = new EditText(MainActivityNative.this);
                    return new VH(et);
                }
                default: {
                    android.widget.Button btn = new android.widget.Button(MainActivityNative.this);
                    return new VH(btn);
                }
            }
        }
        @Override public int getItemViewType(int pos) { return blocks.get(pos).type; }
        @Override public int getItemCount() { return blocks.size(); }
        @Override public void onBindViewHolder(VH h, int pos) {
            Block b = blocks.get(pos);
            switch (b.type) {
                case T_TITLE: {
                    TextView tv = (TextView) h.itemView;
                    tv.setText(b.text, TextView.BufferType.SPANNABLE);
                    tv.setMovementMethod(LinkMovementMethod.getInstance());
                    tv.setTextSize(22);
                    tv.setTypeface(null, Typeface.BOLD);
                    break;
                }
                case T_TEXT: {
                    TextView tv = (TextView) h.itemView;
                    tv.setText(b.text, TextView.BufferType.SPANNABLE);
                    tv.setMovementMethod(LinkMovementMethod.getInstance());
                    tv.setTextSize(b.size);
                    boolean mono = b.name != null && b.name.equals("mono");
                    boolean indent = b.name != null && b.name.equals("indent");
                    tv.setTypeface(mono ? Typeface.MONOSPACE
                            : (b.bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT));
                    tv.setTextColor(mono ? 0xFF333333 : 0xFF000000);
                    tv.setBackgroundColor(mono ? 0xFFF5F5F5 : 0x00000000);
                    tv.setPadding(indent ? 32 : (mono ? 16 : 0), 6, mono ? 16 : 0, 6);
                    break;
                }
                case T_IMAGE: {
                    ImageView iv = (ImageView) h.itemView;
                    iv.setTag(pos);
                    iv.setImageResource(android.R.color.transparent);
                    final int p = pos;
                    new Thread(() -> {
                        try {
                            HttpURLConnection c = (HttpURLConnection) new URL(b.url).openConnection();
                            c.setConnectTimeout(15000);
                            c.setReadTimeout(20000);
                            Bitmap bmp = BitmapFactory.decodeStream(c.getInputStream());
                            runOnUiThread(() -> {
                                if (Integer.valueOf(p).equals(h.itemView.getTag())) {
                                    int maxW = rv.getWidth() - 40;
                                    double scale = Math.min(1.0, maxW / (double) bmp.getWidth());
                                    iv.setImageBitmap(Bitmap.createScaledBitmap(bmp,
                                            Math.max(1, (int) (bmp.getWidth() * scale)),
                                            Math.max(1, (int) (bmp.getHeight() * scale)), true));
                                    if (!b.formAction.isEmpty()) {
                                        iv.setClickable(true);
                                        iv.setOnClickListener(v -> {
                                            if (isDownloadUrl(b.formAction)) downloadFile(b.formAction);
                                            else load(b.formAction);
                                        });
                                    }
                                }
                            });
                        } catch (Exception ignored) {}
                    }).start();
                    break;
                }
                case T_EDIT: {
                    EditText et = (EditText) h.itemView;
                    et.setHint(b.pw ? b.hint + "（密码）" : b.hint);
                    et.setInputType(b.pw
                            ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                            : android.text.InputType.TYPE_CLASS_TEXT);
                    et.addTextChangedListener(new android.text.TextWatcher() {
                        public void beforeTextChanged(CharSequence s, int a, int b2, int c2) {}
                        public void onTextChanged(CharSequence s, int a, int b2, int c2) {}
                        public void afterTextChanged(android.text.Editable s) {
                            if (b.name != null) formValues.put(b.name, s.toString());
                        }
                    });
                    break;
                }
                case T_BUTTON: {
                    android.widget.Button btn = (android.widget.Button) h.itemView;
                    btn.setText(b.label);
                    btn.setOnClickListener(v -> {
                        StringBuilder q = new StringBuilder();
                        try {
                            for (HashMap.Entry<String, String> en : formValues.entrySet()) {
                                if (q.length() > 0) q.append('&');
                                q.append(URLEncoder.encode(en.getKey(), "UTF-8")).append('=')
                                 .append(URLEncoder.encode(en.getValue(), "UTF-8"));
                            }
                        } catch (Exception ignored) {}
                        String action = b.formAction;
                        if (action.contains("?")) action += "&" + q;
                        else action += "?" + q;
                        if (!b.formMethod.equals("post")) load(action);
                        else submitPost(action);
                    });
                    break;
                }
            }
        }
    }

    private void ToastShort(String m) {
        runOnUiThread(() -> android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show());
    }

    private static class VH extends RecyclerView.ViewHolder {
        VH(View v) { super(v); }
    }
}

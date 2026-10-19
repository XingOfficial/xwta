package cn.xing.wtalite;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

public class MainActivity extends Activity {
    private WebView wv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        int id = getResources().getIdentifier("xwta_url", "string", getPackageName());
        String url = id != 0 ? getString(id) : "https://example.com";
        id = getResources().getIdentifier("xwta_js", "string", getPackageName());
        boolean js = id != 0 && Boolean.parseBoolean(getString(id));
        s.setJavaScriptEnabled(js);
        s.setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        wv.setBackgroundColor(0xFFFFFFFF);
        setContentView(wv);
        wv.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }
}

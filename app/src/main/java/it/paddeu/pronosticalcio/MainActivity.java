package it.paddeu.pronosticalcio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.HttpAuthHandler;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Pronosticalcio 2 come app: il sito del worker dentro una WebView a schermo intero.
 * - password del sito chiesta una volta sola e ricordata
 * - i link esterni si aprono nel browser
 * - "Copia INSTA" (file .txt) apre la condivisione di Android
 * - all'avvio controlla su GitHub se c'è una versione nuova dell'app
 */
public class MainActivity extends Activity {

    private static final String PREFS = "accesso";
    private WebView web;
    private ProgressBar barra;
    private long inPausaDa = 0;
    private int tentativiAuth = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        barra = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        barra.setMax(100);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        root.addView(barra, lp);
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " Pronosticalcio2App/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Ponte(), "PronoApp");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                barra.setProgress(p);
                barra.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (Uri.parse(BuildConfig.SITO).getHost().equals(u.getHost())) return false;
                apriFuori(u);
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap f) { tentativiAuth = 0; }
            @Override public void onPageFinished(WebView v, String url) {
                CookieManager.getInstance().flush();
                v.evaluateJavascript(JS_DOWNLOAD, null);
                v.evaluateJavascript(JS_SOLO_PARTITE, null);
            }
            @Override public void onReceivedHttpAuthRequest(WebView v, HttpAuthHandler h, String host, String realm) {
                SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
                String pw = p.getString("pw", null);
                if (pw != null && tentativiAuth++ < 2) { h.proceed("app", pw); return; }
                chiediPassword(h);
            }
        });
        web.setDownloadListener((url, ua, cd, mime, len) -> {
            if (url.startsWith("blob:") || url.startsWith("data:")) web.evaluateJavascript(JS_DOWNLOAD, null);
            else apriFuori(Uri.parse(url));
        });

        if (b != null) web.restoreState(b); else web.loadUrl(BuildConfig.SITO);
        controllaAggiornamento();
    }

    // i download del sito (Copia INSTA) sono file creati nella pagina: li si intercetta e si passano ad Android
    private static final String JS_DOWNLOAD =
        "(function(){if(window.__pa)return;window.__pa=1;" +
        "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[download]');" +
        "if(!a||!a.href||!(a.href.indexOf('blob:')===0||a.href.indexOf('data:')===0))return;e.preventDefault();" +
        "fetch(a.href).then(function(r){return r.text()}).then(function(t){PronoApp.condividi(a.getAttribute('download')||'pronosticalcio.txt',t)});},true);" +
        "var c=HTMLAnchorElement.prototype.click;HTMLAnchorElement.prototype.click=function(){" +
        "if(this.hasAttribute('download')&&this.href&&(this.href.indexOf('blob:')===0||this.href.indexOf('data:')===0)){var a=this;" +
        "fetch(a.href).then(function(r){return r.text()}).then(function(t){PronoApp.condividi(a.getAttribute('download')||'pronosticalcio.txt',t)});return;}" +
        "return c.apply(this,arguments)};" +
        "if(navigator.clipboard){navigator.clipboard.writeText=function(t){PronoApp.copia(t);return Promise.resolve();};}" +
        "})();";

    // nell'app serve solo la schermata Partite (e la scheda della singola partita): via il menu, e Osservate, Regole, Dati tornano a Partite
    private static final String JS_SOLO_PARTITE =
        "(function(){if(window.__sp)return;window.__sp=1;" +
        "var st=document.createElement('style');st.textContent='nav.menu{display:none!important}';document.head.appendChild(st);" +
        "function vai(){var h=location.hash||'';if(/^#\\/(osservate|regole|dati)/.test(h))location.hash='#/';}" +
        "window.addEventListener('hashchange',vai);vai();" +
        "})();";

    private class Ponte {
        @JavascriptInterface public void condividi(String nome, String testo) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, nome);
                i.putExtra(Intent.EXTRA_TEXT, testo);
                startActivity(Intent.createChooser(i, nome));
            });
        }
        @JavascriptInterface public void copia(String testo) {
            runOnUiThread(() -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("Pronosticalcio", testo));
                Toast.makeText(MainActivity.this, "Copiato", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void chiediPassword(HttpAuthHandler h) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setHint("Password del sito");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
            .setTitle("Pronosticalcio 2")
            .setMessage(tentativiAuth > 2 ? "Password sbagliata, riprova." : "Inserisci la password del sito (si chiede una volta sola).")
            .setView(box)
            .setCancelable(false)
            .setPositiveButton("Entra", (d, w) -> {
                String pw = e.getText().toString();
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("pw", pw).apply();
                tentativiAuth = 1;
                h.proceed("app", pw);
            })
            .setNegativeButton("Esci", (d, w) -> { h.cancel(); finish(); })
            .show();
    }

    private void apriFuori(Uri u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); }
        catch (ActivityNotFoundException ex) { Toast.makeText(this, "Nessuna app per aprire il link", Toast.LENGTH_SHORT).show(); }
    }

    private void controllaAggiornamento() {
        new Thread(() -> {
            if (!Updater.check(this, false)) return;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                new AlertDialog.Builder(this)
                    .setTitle("Aggiornamento disponibile")
                    .setMessage("C'è la versione " + Updater.latestName(this) + " dell'app. Installarla adesso?")
                    .setPositiveButton("Aggiorna", (d, w) -> aggiorna())
                    .setNegativeButton("Dopo", null)
                    .show();
            });
        }).start();
    }

    private void aggiorna() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Consenti a Pronosticalcio di installare app, poi torna indietro e premi di nuovo Aggiorna", Toast.LENGTH_LONG).show();
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Toast.makeText(this, "Scarico l'aggiornamento…", Toast.LENGTH_SHORT).show();
        new Thread(() -> Updater.downloadAndInstall(this, new Updater.Progress() {
            @Override public void onProgress(int percent) { }
            @Override public void onError(String m) { runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show()); }
        })).start();
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onPause() { super.onPause(); inPausaDa = System.currentTimeMillis(); CookieManager.getInstance().flush(); web.onPause(); }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        // tornando nell'app dopo più di 10 minuti si ricarica, così le quote sono fresche
        if (inPausaDa > 0 && System.currentTimeMillis() - inPausaDa > 10 * 60 * 1000) web.reload();
    }

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

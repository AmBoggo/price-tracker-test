package com.zaratest;

import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * App de teste: verifica se a WebView do Android consegue acessar a Zara
 * e extrair o preço do produto. Mostra tudo na tela para validação visual.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView tvStatus;
    private TextView tvLog;

    // URLs de teste da Zara
    private static final String[] TEST_URLS = {
        "https://www.zara.com/br/pt/jaqueta-acetinada-com-bolsos-p01255904.html?v1=546167613&v2=2510426",
    };

    // JavaScript ultra-simples com try-catch
    private static final String EXTRACT_JS = "" +
        "(function(){" +
        "  try{" +
        "    var ps=[];" +
        "    var all=document.querySelectorAll('[class*=pric],[class*=Pric],.price,span');" +
        "    for(var i=0;i<Math.min(all.length,50);i++){" +
        "      var t=all[i].innerText||'';" +
        "      if(t.match(/R\\$|USD|EUR|\\d+,\\d{2}/)) ps.push(t.trim());" +
        "    }" +
        "    var r={" +
        "      prices:ps.slice(0,5)," +
        "      pricesCount:ps.length," +
        "      title:document.title||'sem title'," +
        "      h1:(document.querySelector('h1')||{}).innerText||'sem h1'," +
        "      url:window.location.href||'?'," +
        "      bodyLen:document.body?document.body.innerHTML.length:0," +
        "      allText:document.body?document.body.innerText.substring(0,500):''" +
        "    };" +
        "    return JSON.stringify(r);" +
        "  }catch(e){ return 'ERROR:'+e.message; }" +
        "})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        webView = findViewById(R.id.webview);

        setupWebView();
        log("=== Zara WebView Test ===");
        log("URL: " + TEST_URLS[0]);
        webView.loadUrl(TEST_URLS[0]);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        );
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

                webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                log("✓ Página carregada: " + url);
                tvStatus.setText("Extraindo dados...");
                
                // Aguarda a página terminar de carregar JS, depois extrai
                view.postDelayed(() -> doExtract(view), 5000);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                log("ERRO [" + errorCode + "]: " + description);
                tvStatus.setText("❌ Erro: " + errorCode);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                tvStatus.setText("Carregando: " + newProgress + "%");
            }
        });
    }

    private void doExtract(WebView view) {
        view.evaluateJavascript(EXTRACT_JS, value -> {
            log("JS raw value: " + (value == null ? "NULL" : value.substring(0, Math.min(100, value.length()))));
            
            if (value == null || "null".equals(value) || value.length() < 3) {
                log("⚠️ Resultado vazio. Tentando novamente em 3s...");
                view.postDelayed(() -> doExtract(view), 3000);
                return;
            }
            
            // Remove aspas externas
            String json = value;
            if (json.startsWith("\"") && json.endsWith("\"")) {
                json = json.substring(1, json.length() - 1);
                json = json.replace("\\\"", "\"").replace("\\\\", "\\");
            }
            
            log("=== RESULTADO ===");
            log(json);
            
            if (json.contains("\"prices\":[]")) {
                tvStatus.setText("⚠️ Sem preço encontrado");
            } else if (json.contains("ERROR:")) {
                tvStatus.setText("❌ Erro JS");
            } else {
                tvStatus.setText("✅ Dados extraídos!");
            }
        });
    }

    private void log(String msg) {
        runOnUiThread(() -> tvLog.append(msg + "\n"));
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
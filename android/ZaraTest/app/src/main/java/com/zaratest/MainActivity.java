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
        "https://www.zara.com/br/pt/camiseta-basica-l2562950.html",
    };

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

        // JavaScript enxuto que retorna JSON direto
        final String EXTRACT_JS = "" +
            "JSON.stringify({" +
            "  title: document.title || ''," +
            "  url: window.location.href || ''," +
            "  bodyLen: document.body ? document.body.innerHTML.length : 0," +
            "  h1: (function(){var h=document.querySelector('h1');return h?h.innerText:'sem h1';})()," +
            "  prices: (function(){" +
            "    var ps=[];" +
            "    var sel=['.price__amount','[data-qa=price]','.price','.product-price','.product-price__value','[class*=pric]'];" +
            "    for(var i=0;i<sel.length;i++){" +
            "      var els=document.querySelectorAll(sel[i]);" +
            "      for(var j=0;j<els.length&&ps.length<10;j++){" +
            "        var t=els[j].innerText.trim();" +
            "        if(t&&t.match(/\\d/)) ps.push(t);" +
            "      }" +
            "    }" +
            "    return ps;" +
            "  })()," +
            "  ogImage: (function(){var m=document.querySelector('meta[property=og:image]');return m?m.getAttribute('content'):'';})()," +
            "  jsonld: (function(){var s=document.querySelectorAll('script[type=\"application/ld+json\"]');return s.length>0?s[0].innerText.substring(0,200):'nenhum';})()," +
            "  snippet: document.body ? document.body.innerText.substring(0,500) : 'sem body'," +
            "  accessDenied: document.title === 'Access Denied'" +
            "})";

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                log("✓ Página carregada: " + url);
                tvStatus.setText("Extraindo dados...");
                
                // Aguarda a página terminar de carregar JS, depois extrai
                view.postDelayed(() -> {
                    view.evaluateJavascript(EXTRACT_JS, value -> {
                        // value volta como string JSON entre aspas
                        if (value != null && !"null".equals(value) && value.length() > 2) {
                            // Remove as aspas externas (evaluateJavascript retorna "json_string")
                            String json = value;
                            if (json.startsWith("\"") && json.endsWith("\"")) {
                                json = json.substring(1, json.length() - 1);
                                json = json.replace("\\\"", "\"").replace("\\\\", "\\");
                            }
                            log("=== RESULTADO ===");
                            log(json);
                            
                            // Verifica se foi bloqueado
                            if (json.contains("\"accessDenied\":true")) {
                                tvStatus.setText("❌ ACCESS DENIED");
                                log("⚠️ Akamai bloqueou a WebView");
                            } else if (json.contains("\"prices\":[]")) {
                                tvStatus.setText("⚠️ Página carregou mas sem preço");
                            } else {
                                tvStatus.setText("✅ PREÇO ENCONTRADO!");
                            }
                        } else {
                            log("⚠️ evaluateJavascript retornou vazio/null");
                            tvStatus.setText("❌ Falha na extração");
                        }
                    });
                }, 5000); // Espera 5 segundos pra JS terminar de carregar
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
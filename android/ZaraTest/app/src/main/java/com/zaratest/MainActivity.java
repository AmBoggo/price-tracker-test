package com.zaratest;

import android.os.Bundle;
import android.webkit.JavascriptInterface;
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
 *
 * O que este app testa:
 * 1. A Zara carrega na WebView? (IP do celular vs IP datacenter)
 * 2. O JavaScript da página executa? (Akamai passa?)
 * 3. Conseguimos extrair preço/descrição/imagem?
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private TextView tvStatus;
    private TextView tvLog;

    // URL de produto de teste da Zara
    private static final String TEST_URL = "https://www.zara.com/br/pt/camiseta-basica-l2562950.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        webView = findViewById(R.id.webview);

        setupWebView();
        log("Carregando: " + TEST_URL);
        webView.loadUrl(TEST_URL);
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

        // Permite acesso a JavaScript
        webView.addJavascriptInterface(new JSBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                log("Página carregou: " + url);
                // Espera 3 segundos e tenta extrair dados
                view.postDelayed(() -> {
                    log("Extraindo dados...");
                    // Injeta JavaScript para extrair dados
                    view.evaluateJavascript(EXTRACT_JS, null);
                }, 3000);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                log("Redirect: " + request.getUrl());
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                log("ERRO: " + errorCode + " " + description);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                tvStatus.setText("Carregando: " + newProgress + "%");
                if (newProgress == 100) {
                    tvStatus.setText("Página carregada ✓");
                }
            }
        });
    }

    // JavaScript injetado para extrair dados da página
    private static final String EXTRACT_JS = "" +
        "function extractPrice() {" +
        "  var prices = [];" +
        "  // Procurar por seletores comuns de preço" +
        "  var selectors = ['.price__amount', '[data-qa=\"price\"]', '.price', " +
        "    '.product-price', '[class*=\"price\"]', '.price-current'];" +
        "  for (var i = 0; i < selectors.length; i++) {" +
        "    var els = document.querySelectorAll(selectors[i]);" +
        "    for (var j = 0; j < els.length && prices.length < 5; j++) {" +
        "      var txt = els[j].innerText.trim();" +
        "      if (txt && txt.match(/\\d/)) prices.push(selectors[i] + ': ' + txt);" +
        "    }" +
        "  }" +
        "  return prices.join(' | ') || 'NENHUM PREÇO ENCONTRADO';" +
        "}" +
        "var title = document.title || 'sem titulo';" +
        "var bodyLen = document.body ? document.body.innerHTML.length : 0;" +
        "var h1 = document.querySelector('h1') ? document.querySelector('h1').innerText : 'sem h1';" +
        "var priceInfo = extractPrice();" +
        "var jsonld = '';" +
        "var scripts = document.querySelectorAll('script[type=\"application/ld+json\"]');" +
        "for (var i = 0; i < scripts.length; i++) {" +
        "  jsonld += scripts[i].innerText.substring(0, 300);" +
        "}" +
        "Android.log('=== RESULTADO ===');" +
        "Android.log('Title: ' + title);" +
        "Android.log('Body size: ' + bodyLen);" +
        "Android.log('H1: ' + h1);" +
        "Android.log('Prices: ' + priceInfo);" +
        "Android.log('JSON-LD: ' + (jsonld || 'nenhum'));" +
        "Android.log('Body preview: ' + document.body.innerHTML.substring(0, 500));" +
        (window.location.href ? "" : "") +
        "";

    /** Classe exposta para o JavaScript da página enviar resultados de volta */
    public class JSBridge {
        @JavascriptInterface
        public void log(String message) {
            runOnUiThread(() -> {
                tvLog.append(message + "\n");
                // Auto-scroll pra baixo
                tvLog.post(() -> {
                    int scrollAmount = tvLog.getLayout().getLineTop(tvLog.getLineCount()) - tvLog.getHeight();
                    if (scrollAmount > 0)
                        tvLog.scrollTo(0, scrollAmount);
                    else
                        tvLog.scrollTo(0, 0);
                });
            });
        }
    }

    private void log(String msg) {
        runOnUiThread(() -> tvLog.append("[APP] " + msg + "\n"));
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
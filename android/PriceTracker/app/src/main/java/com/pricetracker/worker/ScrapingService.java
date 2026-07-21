package com.pricetracker.worker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationCompat;

import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.PrecoSubmit;
import com.pricetracker.model.Produto;
import com.pricetracker.ui.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import retrofit2.Response;

/**
 * Foreground Service que faz scraping via WebView em segundo plano.
 * Mostra notificação "Atualizando preços..." enquanto processa.
 */
public class ScrapingService extends Service {

    private static final String CHANNEL_ID = "scraping_channel";
    private static final int NOTIF_ID = 2001;

    private WebView webView;
    private NotificationManager nm;
    private List<Produto> produtos;
    private int index = 0;
    private int total = 0;
    private int sucessos = 0;

    private static final String EXTRACT_JS =
        "(function(){try{var ps=[],t=document.body?document.body.innerText:'';var m,rx=/R\\$/g;" +
        "while((m=rx.exec(t))!==null){var v=t.substring(m.index).match(/[\\d.,]+/);" +
        "if(v){var x=parseFloat(v[0].replace('.','').replace(',','.'));if(!isNaN(x))ps.push(x);}}" +
        "if(ps.length>1)ps.sort(function(a,b){return a-b});" +
        "var title=document.title||'';title=title.replace(/\\|.*/,'').trim();" +
        "var img='';var og=document.querySelector('meta[property=\"og:image\"]');" +
        "if(og)img=og.getAttribute('content');" +
        "if(!img){var metas=document.getElementsByTagName('meta');" +
        "for(var i=0;i<metas.length;i++){var n=metas[i].getAttribute('name')||'';" +
        "var p=metas[i].getAttribute('property')||'';" +
        "if((n==='twitter:image'||p==='twitter:image'||p==='og:image')&&!img)" +
        "{img=metas[i].getAttribute('content');}}}" +
        "if(!img){var imgs=document.querySelectorAll('img');for(var i=0;i<imgs.length;i++)" +
        "{var s=imgs[i].src||imgs[i].getAttribute('data-src')||'';" +
        "if(s&&s.indexOf('http')!=-1&&s.indexOf('data:')==-1){img=s;break;}}}" +
        "return JSON.stringify({prices:ps,title:title,image:img});" +
        "}catch(e){return JSON.stringify({error:e.message});}})()";

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        criarCanal();

        // WebView criada na thread principal
        new Handler(Looper.getMainLooper()).post(() -> {
            webView = new WebView(this);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setUserAgentString("Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.postDelayed(() -> {
                        view.evaluateJavascript(EXTRACT_JS, value -> processar(value));
                    }, 4000);
                }
            });
            // Inicia o processo
            iniciarScraping();
        });
    }

    private void criarCanal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "PriceTracker Scraping", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Atualização automática de preços");
            nm.createNotificationChannel(ch);
        }
    }

    private void iniciarScraping() {
        new Thread(() -> {
            try {
                Response<List<Produto>> r = RetrofitClient.getService().listarProdutos().execute();
                if (r.isSuccessful() && r.body() != null) {
                    produtos = r.body();
                    total = produtos.size();
                    if (total == 0) {
                        stopSelf();
                        return;
                    }
                    startForeground(NOTIF_ID, buildNotif("Iniciando..."));
                    processarProximo();
                } else {
                    stopSelf();
                }
            } catch (IOException e) {
                stopSelf();
            }
        }).start();
    }

    private void processarProximo() {
        if (index >= total) {
            // Terminou!
            atualizarNotif("✓ " + sucessos + "/" + total + " atualizados");
            new Handler(Looper.getMainLooper()).postDelayed(this::stopSelf, 3000);
            return;
        }

        Produto p = produtos.get(index);
        atualizarNotif("Verificando " + (index+1) + "/" + total + "...");
        new Handler(Looper.getMainLooper()).post(() -> webView.loadUrl(p.url));
    }

    private void processar(String value) {
        Produto p = produtos.get(index);
        index++;
        boolean ok = false;

        if (value != null && !value.equals("null")) {
            try {
                String json = value;
                if (json.startsWith("\"") && json.endsWith("\""))
                    json = json.substring(1, json.length()-1).replace("\\\"","\"").replace("\\\\","\\");
                JSONObject obj = new JSONObject(json);
                JSONArray prices = obj.optJSONArray("prices");
                double preco = 0;
                if (prices != null && prices.length() > 0) preco = prices.getDouble(0);
                String titulo = obj.optString("title","");
                String imagem = obj.optString("image","");

                if (preco > 0) {
                    Response<Produto> r = RetrofitClient.getService()
                        .registrarPreco(p.id, new PrecoSubmit(preco,
                            titulo.isEmpty()?null:titulo,
                            imagem.isEmpty()?null:imagem)).execute();
                    if (r.isSuccessful()) {
                        sucessos++;
                        ok = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (!ok) {
            atualizarNotif((index) + "/" + total + " (falha no item " + index + ")");
        }

        // Delay entre produtos pra não sobrecarregar
        new Handler(Looper.getMainLooper()).postDelayed(this::processarProximo, 2000);
    }

    private Notification buildNotif(String texto) {
        Intent i = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛒 PriceTracker")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void atualizarNotif(String texto) {
        nm.notify(NOTIF_ID, buildNotif(texto));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
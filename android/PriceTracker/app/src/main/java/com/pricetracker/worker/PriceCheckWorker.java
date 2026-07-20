package com.pricetracker.worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.WebView;
import android.webkit.WebSettings;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pricetracker.R;
import com.pricetracker.api.ApiService;
import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.PrecoSubmit;
import com.pricetracker.model.Produto;
import com.pricetracker.ui.MainActivity;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Worker que verifica preços de todos os produtos.
 * Usa WebView invisível para carregar cada produto e extrair preço via regex.
 * Notifica quando preço cai ou atinge meta.
 */
public class PriceCheckWorker extends Worker {

    private static final String CHANNEL_ID = "price_alerts";
    private static final String EXTRACT_JS = "" +
        "(function(){" +
        "  try{" +
        "    var ps=[];" +
        "    var fullText=document.body?document.body.innerText:'';" +
        "    var rx=/R\\$\\s*[\\d.,]+/g;" +
        "    var m;" +
        "    while((m=rx.exec(fullText))!==null) ps.push(m[0]);" +
        "    var title=document.title||'';" +
        "    var imgs=document.querySelectorAll('img');" +
        "    var img='';" +
        "    for(var i=0;i<imgs.length;i++){if(imgs[i].src&&imgs[i].width>50){img=imgs[i].src;break;}}" +
        "    return JSON.stringify({prices:ps,title:title,image:img});" +
        "  }catch(e){ return 'ERROR:'+e.message; }" +
        "})()";

    public PriceCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        criarCanalNotificacao();

        // Busca todos produtos do backend
        final List<Produto>[] produtosHolder = new List[]{null};
        final boolean[] fetchDone = {false};

        ApiService api = RetrofitClient.getService();
        api.listarProdutos().enqueue(new Callback<List<Produto>>() {
            @Override
            public void onResponse(Call<List<Produto>> call, Response<List<Produto>> response) {
                produtosHolder[0] = response.body();
                fetchDone[0] = true;
            }

            @Override
            public void onFailure(Call<List<Produto>> call, Throwable t) {
                fetchDone[0] = true;
            }
        });

        // Aguarda resposta (timeout 15s)
        aguardar(fetchDone, 15);

        if (produtosHolder[0] == null || produtosHolder[0].isEmpty()) {
            return Result.success();
        }

        // Verifica cada produto
        for (Produto p : produtosHolder[0]) {
            verificarProduto(p);
        }

        return Result.success();
    }

    private void verificarProduto(Produto produto) {
        if (produto.url == null || produto.url.isEmpty()) return;

        final CountDownLatch latch = new CountDownLatch(1);
        final Double[] precoEncontrado = {null};
        final String[] tituloEncontrado = {null};
        final String[] imagemEncontrada = {null};

        // Cria WebView na thread principal
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            WebView webView = new WebView(getApplicationContext());

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setUserAgentString(
                "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
            );

            webView.setWebViewClient(new android.webkit.WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    view.postDelayed(() -> {
                        view.evaluateJavascript(EXTRACT_JS, value -> {
                            if (value != null && value.startsWith("\"")) {
                                // Remove aspas externas
                                String json = value.substring(1, value.length() - 1)
                                        .replace("\\\"", "\"").replace("\\\\", "\\");

                                try {
                                    org.json.JSONObject obj = new org.json.JSONObject(json);
                                    org.json.JSONArray prices = obj.optJSONArray("prices");
                                    if (prices != null && prices.length() > 0) {
                                        String precoStr = prices.getString(0)
                                                .replace("R$", "").replace(".", "")
                                                .replace(",", ".").trim();
                                        precoEncontrado[0] = Double.parseDouble(precoStr);
                                    }
                                    tituloEncontrado[0] = obj.optString("title", null);
                                    imagemEncontrada[0] = obj.optString("image", null);
                                } catch (Exception e) {
                                    // parsing error
                                }
                            }
                            latch.countDown();
                            webView.destroy();
                        });
                    }, 5000);
                }
            });

            webView.loadUrl(produto.url);
        });

        // Aguarda WebView terminar (timeout 30s)
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return;
        }

        // Envia preço pro backend se encontrou
        if (precoEncontrado[0] != null) {
            enviarPreco(produto, precoEncontrado[0], tituloEncontrado[0], imagemEncontrada[0]);
        }
    }

    private void enviarPreco(Produto produto, double preco, String titulo, String imagem) {
        PrecoSubmit body = new PrecoSubmit(preco, titulo, imagem);
        RetrofitClient.getService()
                .registrarPreco(produto.id, body)
                .enqueue(new Callback<Produto>() {
                    @Override
                    public void onResponse(Call<Produto> call, Response<Produto> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Produto atualizado = response.body();

                            // Notifica se baixou
                            if (atualizado.baixou()) {
                                notificar("📉 Preço caiu!",
                                    (atualizado.titulo != null ? atualizado.titulo : "Produto") +
                                    " agora está " + atualizado.precoFormatado() +
                                    " (" + atualizado.variacaoFormatada() + ")");
                            }

                            // Notifica se atingiu meta
                            if (atualizado.atingiuMeta() && (produto.precoAtual == null
                                    || produto.precoAtual > produto.precoMeta)) {
                                notificar("🎯 Meta atingida!",
                                    (atualizado.titulo != null ? atualizado.titulo : "Produto") +
                                    " chegou ao seu preço meta: " + atualizado.metaFormatada());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Produto> call, Throwable t) {
                        // silent
                    }
                });
    }

    private void aguardar(boolean[] done, int timeoutSec) {
        int waited = 0;
        while (!done[0] && waited < timeoutSec) {
            try {
                Thread.sleep(1000);
                waited++;
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Alertas de Preço",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notificações quando o preço cai ou atinge a meta");
            NotificationManager manager = getApplicationContext()
                    .getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void notificar(String titulo, String texto) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            getApplicationContext(), 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
            getApplicationContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getApplicationContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            int notifId = (int) System.currentTimeMillis();
            manager.notify(notifId, builder.build());
        }
    }
}
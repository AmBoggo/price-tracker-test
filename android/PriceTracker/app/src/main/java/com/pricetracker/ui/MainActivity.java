package com.pricetracker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pricetracker.R;
import com.pricetracker.api.ApiService;
import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.PrecoSubmit;
import com.pricetracker.model.Produto;
import com.pricetracker.worker.PriceScheduler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProdutoAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvStatus;
    private WebView scrapWebView;
    private List<Produto> produtosAtuais = new ArrayList<>();
    private Produto scrapAtual;

    private static final String EXTRACT_JS = "" +
        "(function(){try{" +
        " var ps=[],t=document.body?document.body.innerText:'';" +
        " var m,rx=/R\\$/g;" +
        " while((m=rx.exec(t))!==null){" +
        "   var v=t.substring(m.index).match(/[\\d.,]+/);" +
        "   if(v){var x=parseFloat(v[0].replace('.','').replace(',','.'));if(!isNaN(x))ps.push(x);}" +
        " }" +
        " if(ps.length>1)ps.sort(function(a,b){return a-b});" +
        " var title=document.title||'';title=title.replace(/\\|.*/,'').trim();" +
        " var img=''; var debug=[];" +
        " var og=document.querySelector('meta[property=og:image]');" +
        " if(og){img=og.getAttribute('content'); debug.push('og:'+img.substring(0,50));}" +
        " if(!img){var tw=document.querySelector('meta[name=twitter:image],meta[property=twitter:image]');" +
        "   if(tw){img=tw.getAttribute('content'); debug.push('tw:'+img.substring(0,50));}}" +
        " if(!img){var sc=document.querySelector('meta[itemprop=image]');" +
        "   if(sc){img=sc.getAttribute('content'); debug.push('schema:'+img.substring(0,50));}}" +
        " var imgs=document.querySelectorAll('img,picture img,source');" +
        " debug.push('imgs:'+imgs.length);" +
        " if(!img&&imgs.length>0){" +
        "   for(var i=0;i<Math.min(imgs.length,20);i++){" +
        "     var s=imgs[i].src||imgs[i].getAttribute('data-src')||imgs[i].getAttribute('srcset')||'';" +
        "     if(i<5) debug.push('['+i+']:'+(s.substring(0,40)||'vazio')+' w'+imgs[i].naturalWidth);" +
        "     if(!img&&s&&s.indexOf('http')!=-1&&s.indexOf('data:')==-1&&s.indexOf('pixel')==-1&&s.indexOf('.svg')==-1){" +
        "       img=s; debug.push('PICK:'+i);" +
        "     }" +
        "   }" +
        " }" +
        " var dbg=debug.join('|');" +
        " return JSON.stringify({prices:ps,title:title,image:img,debug:dbg});" +
        "}catch(e){return JSON.stringify({error:e.message});}})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recycler = findViewById(R.id.recyclerProdutos);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);
        scrapWebView = findViewById(R.id.scrapWebView);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProdutoAdapter(new ArrayList<>(), this::onProdutoClick);
        recycler.setAdapter(adapter);

        // ⭐ WebViewClient fixo - configurado UMA VEZ
        WebSettings s = scrapWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        );
        scrapWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (scrapAtual == null) return;
                view.postDelayed(() -> {
                    view.evaluateJavascript(EXTRACT_JS, value -> processar(value));
                }, 4000);
            }
        });

        PriceScheduler.agendar(this);
        carregarProdutos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarProdutos();
    }

    private void processar(String value) {
        Produto p = scrapAtual;
        scrapAtual = null;
        if (p == null || value == null || value.equals("null")) {
            tvStatus.setVisibility(View.GONE);
            return;
        }
        try {
            String json = value;
            if (json.startsWith("\"") && json.endsWith("\""))
                json = json.substring(1, json.length()-1).replace("\\\"","\"").replace("\\\\","\\");
            JSONObject obj = new JSONObject(json);

            if (obj.has("error")) {
                tvStatus.setText("JS ERROR: " + obj.optString("error"));
                return;
            }

            JSONArray prices = obj.optJSONArray("prices");
            double preco = 0;
            if (prices != null && prices.length() > 0) preco = prices.getDouble(0);
            String titulo = obj.optString("title","");
            String imagem = obj.optString("image","");
            String debug = obj.optString("debug","");

            tvStatus.setText((preco>0?"✓":"?") + " " + titulo + " | img:" + (imagem.isEmpty()?"NONE":imagem.substring(0,Math.min(30,imagem.length()))) + " | " + debug);
            tvStatus.setVisibility(View.VISIBLE);

            if (preco > 0) {
                final double pf = preco;
                final String tf = titulo;
                final String imf = imagem;
                RetrofitClient.getService().registrarPreco(p.id,
                    new PrecoSubmit(pf, tf.isEmpty()?null:tf, imf.isEmpty()?null:imf))
                    .enqueue(new Callback<Produto>() {
                        @Override public void onResponse(Call<Produto> c, Response<Produto> r) {
                            carregarProdutos();
                            tvStatus.postDelayed(() -> tvStatus.setVisibility(View.GONE), 3000);
                        }
                        @Override public void onFailure(Call<Produto> c, Throwable t) {
                            tvStatus.setVisibility(View.GONE);
                        }
                    });
            }
        } catch (Exception e) {
            tvStatus.setText("Parse err: " + e.getMessage());
        }
    }

    public void onAddClick(View view) {
        startActivity(new Intent(this, AddProductActivity.class));
    }

    public void onRefreshClick(View view) {
        carregarProdutos();
    }

    private void carregarProdutos() {
        tvEmpty.setVisibility(View.GONE);
        RetrofitClient.getService().listarProdutos().enqueue(new Callback<List<Produto>>() {
            @Override public void onResponse(Call<List<Produto>> call, Response<List<Produto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    produtosAtuais = response.body();
                    adapter.atualizar(produtosAtuais);
                    tvEmpty.setVisibility(produtosAtuais.isEmpty() ? View.VISIBLE : View.GONE);
                    if (scrapAtual == null) {
                        for (Produto p : produtosAtuais) {
                            if (p.precoAtual == null) {
                                scrapAtual = p;
                                tvStatus.setText("Verificando " + p.url.substring(0,Math.min(30,p.url.length())) + "...");
                                tvStatus.setVisibility(View.VISIBLE);
                                scrapWebView.loadUrl(p.url);
                                break;
                            }
                        }
                    }
                }
            }
            @Override public void onFailure(Call<List<Produto>> call, Throwable t) {}
        });
    }

    private void onProdutoClick(Produto p, String acao) {
        if ("historico".equals(acao)) {
            Intent i = new Intent(this, HistoryActivity.class);
            i.putExtra("produto_id", p.id);
            i.putExtra("produto_titulo", p.titulo);
            startActivity(i);
        } else if ("deletar".equals(acao)) {
            RetrofitClient.getService().deletarProduto(p.id).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> call, Response<Void> response) {
                    Toast.makeText(MainActivity.this, "Removido", Toast.LENGTH_SHORT).show();
                    carregarProdutos();
                }
                @Override public void onFailure(Call<Void> call, Throwable t) {}
            });
        }
    }
}
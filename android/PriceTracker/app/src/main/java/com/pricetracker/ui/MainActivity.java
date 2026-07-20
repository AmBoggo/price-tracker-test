package com.pricetracker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

    private static final String EXTRACT_JS = "" +
        "(function(){try{" +
        " var ps=[];var fullText=document.body?document.body.innerText:'';" +
        " var rx=/R\\$\\s*[\\d.,]+/g;var m;" +
        " while((m=rx.exec(fullText))!==null){" +
        "   var v=m[0].replace('R$','').replace('.','').replace(',','.').trim();" +
        "   ps.push(parseFloat(v));" +
        " }" +
        " ps.sort(function(a,b){return a-b;});" + // menor preço = desconto
        " var title=document.title||'';" +
        " var titleClean=title.replace(/\\|.*/,'').trim();" +
        " var img=''; var debug=[];" +
        " // Tenta og:image" +
        " var og=document.querySelector('meta[property=og:image]');" +
        " if(og){img=og.getAttribute('content'); debug.push('og:'+img.substring(0,50));}" +
        " // Tenta twitter:image" +
        " if(!img){var tw=document.querySelector('meta[name=twitter:image],meta[property=twitter:image]');" +
        "   if(tw){img=tw.getAttribute('content'); debug.push('tw:'+img.substring(0,50));}}" +
        " // Tenta schema.org image" +
        " if(!img){var sc=document.querySelector('meta[itemprop=image]');" +
        "   if(sc){img=sc.getAttribute('content'); debug.push('schema:'+img.substring(0,50));}}" +
        " // Tenta todas as imgs" +
        " var imgs=document.querySelectorAll('img,picture img,source');" +
        " debug.push('imgs total:'+imgs.length);" +
        " if(!img&&imgs.length>0){" +
        "   for(var i=0;i<Math.min(imgs.length,20);i++){" +
        "     var s=imgs[i].src||imgs[i].getAttribute('data-src')||imgs[i].getAttribute('srcset')||'';" +
        "     if(i<5) debug.push('img['+i+']:'+(s.substring(0,60)||'vazio')+' w:'+imgs[i].naturalWidth);" +
        "     if(!img&&s&&s.indexOf('http')!=-1&&s.indexOf('data:')==-1&&s.indexOf('pixel')==-1&&s.indexOf('.svg')==-1){" +
        "       img=s; debug.push('selected:img['+i+']');" +
        "     }" +
        "   }" +
        " }" +
        " // Debug: mostra o que encontrou" +
        " var dbg=debug.join(' | ');" +
        " return JSON.stringify({prices:ps,title:titleClean,image:img,debug:dbg});" +
        "}catch(e){return 'ERROR:'+e.message;}})()";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recycler = findViewById(R.id.recyclerProdutos);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvStatus = findViewById(R.id.tvStatus);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProdutoAdapter(new ArrayList<>(), this::onProdutoClick);
        recycler.setAdapter(adapter);

        // WebView invisível para scraping
        scrapWebView = findViewById(R.id.scrapWebView);
        setupScrapWebView();

        // Agenda verificação 3x/dia
        PriceScheduler.agendar(this);

        carregarEVerificar();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarEVerificar();
    }

    private void setupScrapWebView() {
        WebSettings s = scrapWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(
            "Mozilla/5.0 (Linux; Android 14; SM-S908B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        );
    }

    public void onAddClick(View view) {
        startActivity(new Intent(this, AddProductActivity.class));
    }

    public void onRefreshClick(View view) {
        carregarEVerificar();
    }

    private void carregarEVerificar() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        ApiService api = RetrofitClient.getService();
        api.listarProdutos().enqueue(new Callback<List<Produto>>() {
            @Override
            public void onResponse(Call<List<Produto>> call, Response<List<Produto>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    produtosAtuais = response.body();
                    adapter.atualizar(produtosAtuais);
                    tvEmpty.setVisibility(produtosAtuais.isEmpty() ? View.VISIBLE : View.GONE);

                    // Verifica se tem produto sem preço
                    for (Produto p : produtosAtuais) {
                        if (p.precoAtual == null) {
                            verificarPreco(p);
                            break; // verifica um por vez
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Produto>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void verificarPreco(Produto produto) {
        if (produto.url == null || produto.url.isEmpty()) return;

        tvStatus.setText("Verificando: " + (produto.titulo != null ? produto.titulo : "produto..."));
        tvStatus.setVisibility(View.VISIBLE);

        scrapWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.postDelayed(() -> {
                    view.evaluateJavascript(EXTRACT_JS, value -> {
                        if (value != null && value.startsWith("\"")) {
                            String json = value.substring(1, value.length() - 1)
                                    .replace("\\\"", "\"").replace("\\\\", "\\");
                            try {
                                JSONObject obj = new JSONObject(json);
                                JSONArray prices = obj.optJSONArray("prices");
                                double preco = 0;
                                if (prices != null && prices.length() > 0) {
                                    // Menor preço = preço com desconto
                                    preco = prices.getDouble(0);
                                }
                                String titulo = obj.optString("title","");
                                                                String imagem = obj.optString("image","");
                                                                String debug = obj.optString("debug","");

                                                                if (preco > 0) {
                                                                    enviarPreco(produto, preco,
                                                                        titulo.isEmpty() ? null : titulo,
                                                                        imagem.isEmpty() ? null : imagem);
                                                                }
                                                                tvStatus.setText("OK " + titulo + " | img:" + (imagem.isEmpty()?"NONE":imagem.substring(0,30)) + " | " + debug.substring(0, Math.min(80, debug.length())));

                                // Verifica próximo produto
                                verificarProximo(produto);

                            } catch (Exception e) {
                                tvStatus.setText("Erro ao extrair: " + e.getMessage());
                                tvStatus.setVisibility(View.GONE);
                                verificarProximo(produto);
                            }
                        } else {
                            tvStatus.setVisibility(View.GONE);
                            verificarProximo(produto);
                        }
                    });
                }, 5000);
            }
        });

        scrapWebView.loadUrl(produto.url);
    }

    private void verificarProximo(Produto atual) {
        boolean encontrouAtual = false;
        for (Produto p : produtosAtuais) {
            if (!encontrouAtual && p.id == atual.id) {
                encontrouAtual = true;
                continue;
            }
            if (encontrouAtual && p.precoAtual == null) {
                new Handler(Looper.getMainLooper()).postDelayed(
                    () -> verificarPreco(p), 2000);
                return;
            }
        }
        tvStatus.setVisibility(View.GONE);
    }

    private void enviarPreco(Produto produto, double preco, String titulo, String imagem) {
        PrecoSubmit body = new PrecoSubmit(preco, titulo, imagem);
        RetrofitClient.getService()
            .registrarPreco(produto.id, body)
            .enqueue(new Callback<Produto>() {
                @Override
                public void onResponse(Call<Produto> call, Response<Produto> response) {
                    carregarProdutos();
                }

                @Override
                public void onFailure(Call<Produto> call, Throwable t) {}
            });
    }

    private void carregarProdutos() {
        RetrofitClient.getService().listarProdutos().enqueue(new Callback<List<Produto>>() {
            @Override
            public void onResponse(Call<List<Produto>> call, Response<List<Produto>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    produtosAtuais = response.body();
                    adapter.atualizar(produtosAtuais);
                    tvEmpty.setVisibility(produtosAtuais.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<List<Produto>> call, Throwable t) {}
        });
    }

    private void onProdutoClick(Produto produto, String acao) {
        if ("historico".equals(acao)) {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra("produto_id", produto.id);
            intent.putExtra("produto_titulo", produto.titulo);
            startActivity(intent);
        } else if ("deletar".equals(acao)) {
            deletarProduto(produto);
        }
    }

    private void deletarProduto(Produto produto) {
        RetrofitClient.getService().deletarProduto(produto.id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Removido", Toast.LENGTH_SHORT).show();
                    carregarEVerificar();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }
}
package com.pricetracker.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pricetracker.R;
import com.pricetracker.api.ApiService;
import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.Produto;
import com.pricetracker.worker.PriceScheduler;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProdutoAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recycler = findViewById(R.id.recyclerProdutos);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProdutoAdapter(new ArrayList<>(), this::onProdutoClick);
        recycler.setAdapter(adapter);

        // Agenda verificação automática 3x/dia
        PriceScheduler.agendar(this);

        carregarProdutos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarProdutos();
    }

    public void onAddClick(View view) {
        startActivity(new Intent(this, AddProductActivity.class));
    }

    private void carregarProdutos() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        ApiService api = RetrofitClient.getService();
        api.listarProdutos().enqueue(new Callback<List<Produto>>() {
            @Override
            public void onResponse(Call<List<Produto>> call, Response<List<Produto>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<Produto> produtos = response.body();
                    adapter.atualizar(produtos);
                    tvEmpty.setVisibility(produtos.isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    Toast.makeText(MainActivity.this, "Erro ao carregar", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Produto>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Sem conexão: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
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
        ApiService api = RetrofitClient.getService();
        api.deletarProduto(produto.id).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, "Removido", Toast.LENGTH_SHORT).show();
                    carregarProdutos();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Erro ao remover", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
package com.pricetracker.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.pricetracker.R;
import com.pricetracker.api.ApiService;
import com.pricetracker.api.RetrofitClient;
import com.pricetracker.model.HistoricoPreco;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        int produtoId = getIntent().getIntExtra("produto_id", -1);
        String titulo = getIntent().getStringExtra("produto_titulo");

        TextView tvTitulo = findViewById(R.id.tvTituloHistorico);
        tvTitulo.setText(titulo != null ? "Histórico: " + titulo : "Histórico de preços");

        recycler = findViewById(R.id.recyclerHistorico);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter(new ArrayList<>());
        recycler.setAdapter(adapter);

        if (produtoId > 0) {
            carregarHistorico(produtoId);
        }
    }

    private void carregarHistorico(int produtoId) {
        ApiService api = RetrofitClient.getService();
        api.getHistorico(produtoId).enqueue(new Callback<List<HistoricoPreco>>() {
            @Override
            public void onResponse(Call<List<HistoricoPreco>> call, Response<List<HistoricoPreco>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    adapter.atualizar(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<HistoricoPreco>> call, Throwable t) {
                // silent
            }
        });
    }

    public void onVoltarClick(View view) {
        finish();
    }

    // ─── Adapter ──

    static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<HistoricoPreco> historico;

        HistoryAdapter(List<HistoricoPreco> historico) {
            this.historico = historico;
        }

        void atualizar(List<HistoricoPreco> novos) {
            this.historico = novos;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_historico, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoricoPreco h = historico.get(position);
            holder.tvData.setText(h.dataFormatada());
            holder.tvPrecoHist.setText(h.precoFormatado());
        }

        @Override
        public int getItemCount() {
            return historico.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvData, tvPrecoHist;

            ViewHolder(View itemView) {
                super(itemView);
                tvData = itemView.findViewById(R.id.tvData);
                tvPrecoHist = itemView.findViewById(R.id.tvPrecoHist);
            }
        }
    }
}
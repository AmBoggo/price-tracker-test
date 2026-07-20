package com.pricetracker.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.pricetracker.R;
import com.pricetracker.model.Produto;

import java.util.List;

public class ProdutoAdapter extends RecyclerView.Adapter<ProdutoAdapter.ViewHolder> {

    private List<Produto> produtos;
    private OnProdutoActionListener listener;

    public interface OnProdutoActionListener {
        void onProdutoClick(Produto produto, String acao);
    }

    public ProdutoAdapter(List<Produto> produtos, OnProdutoActionListener listener) {
        this.produtos = produtos;
        this.listener = listener;
    }

    public void atualizar(List<Produto> novos) {
        this.produtos = novos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_produto, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Produto p = produtos.get(position);

        // Título
        holder.tvTitulo.setText(p.titulo != null ? p.titulo : "Carregando...");

        // Site badge
        holder.tvSite.setText(p.site != null ? p.site : "");

        // Preço
        holder.tvPreco.setText(p.precoFormatado());

        // Variação/indicador
        if (p.atingiuMeta()) {
            holder.tvIndicador.setText("🎯");
            holder.tvIndicador.setTextColor(Color.parseColor("#10B981"));
        } else if (p.baixou()) {
            holder.tvIndicador.setText(p.variacaoFormatada());
            holder.tvIndicador.setTextColor(Color.parseColor("#10B981")); // verde
        } else if (p.subiu()) {
            holder.tvIndicador.setText(p.variacaoFormatada());
            holder.tvIndicador.setTextColor(Color.parseColor("#EF4444")); // vermelho
        } else {
            holder.tvIndicador.setText("");
        }

        // Meta
        String metaTexto = p.precoMeta != null ? "Meta: " + p.metaFormatada() : "";
        if (p.atingiuMeta()) {
            metaTexto = "🎯 Meta atingida! " + p.metaFormatada();
            holder.tvMeta.setTextColor(Color.parseColor("#10B981"));
        } else {
            holder.tvMeta.setTextColor(Color.parseColor("#9CA3AF"));
        }
        holder.tvMeta.setText(metaTexto);

        // Imagem
        if (p.imagemUrl != null && !p.imagemUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(p.imagemUrl)
                    .centerCrop()
                    .into(holder.imgProduto);
        } else {
            holder.imgProduto.setBackgroundColor(Color.parseColor("#F3EFE8"));
        }

        // Botões
        holder.btnHistorico.setOnClickListener(v ->
                listener.onProdutoClick(p, "historico"));
        holder.btnDeletar.setOnClickListener(v ->
                listener.onProdutoClick(p, "deletar"));
    }

    @Override
    public int getItemCount() {
        return produtos.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgProduto;
        TextView tvTitulo, tvSite, tvPreco, tvMeta, tvIndicador, btnHistorico, btnDeletar;

        ViewHolder(View itemView) {
            super(itemView);
            imgProduto = itemView.findViewById(R.id.imgProduto);
            tvTitulo = itemView.findViewById(R.id.tvTitulo);
            tvSite = itemView.findViewById(R.id.tvSite);
            tvPreco = itemView.findViewById(R.id.tvPreco);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            tvIndicador = itemView.findViewById(R.id.tvIndicador);
            btnHistorico = itemView.findViewById(R.id.btnHistorico);
            btnDeletar = itemView.findViewById(R.id.btnDeletar);
        }
    }
}
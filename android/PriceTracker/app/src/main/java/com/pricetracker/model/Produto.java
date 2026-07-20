package com.pricetracker.model;

import com.google.gson.annotations.SerializedName;

public class Produto {
    @SerializedName("id")
    public int id;

    @SerializedName("url")
    public String url;

    @SerializedName("titulo")
    public String titulo;

    @SerializedName("imagem_url")
    public String imagemUrl;

    @SerializedName("preco_atual")
    public Double precoAtual;

    @SerializedName("preco_anterior")
    public Double precoAnterior;

    @SerializedName("variacao")
    public Double variacao;

    @SerializedName("preco_meta")
    public Double precoMeta;

    @SerializedName("site")
    public String site;

    @SerializedName("ativo")
    public int ativo;

    @SerializedName("criado_em")
    public String criadoEm;

    @SerializedName("atualizado_em")
    public String atualizadoEm;

    /** Retorna true se o preço caiu desde a última verificação */
    public boolean baixou() {
        return variacao != null && variacao < 0;
    }

    /** Retorna true se subiu */
    public boolean subiu() {
        return variacao != null && variacao > 0;
    }

    /** Retorna true se atingiu a meta */
    public boolean atingiuMeta() {
        return precoMeta != null && precoAtual != null && precoAtual <= precoMeta;
    }

    /** Formata o preço atual como string R$ */
    public String precoFormatado() {
        if (precoAtual == null) return "—";
        return String.format("R$ %.2f", precoAtual);
    }

    /** Formata a meta como string R$ */
    public String metaFormatada() {
        if (precoMeta == null) return "sem meta";
        return String.format("R$ %.2f", precoMeta);
    }

    /** Formata a variação com seta */
    public String variacaoFormatada() {
        if (variacao == null) return "";
        if (variacao < 0) return "↓ " + Math.abs(variacao) + "%";
        if (variacao > 0) return "↑ " + variacao + "%";
        return "→ 0%";
    }
}
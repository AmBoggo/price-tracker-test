package com.pricetracker.model;

import com.google.gson.annotations.SerializedName;

public class ProdutoCreate {
    @SerializedName("url")
    public String url;

    @SerializedName("preco_meta")
    public Double precoMeta;

    public ProdutoCreate(String url, Double precoMeta) {
        this.url = url;
        this.precoMeta = precoMeta;
    }
}
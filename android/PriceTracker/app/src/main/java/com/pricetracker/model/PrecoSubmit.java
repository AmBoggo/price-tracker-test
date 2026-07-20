package com.pricetracker.model;

import com.google.gson.annotations.SerializedName;

public class PrecoSubmit {
    @SerializedName("preco")
    public double preco;

    @SerializedName("titulo")
    public String titulo;

    @SerializedName("imagem_url")
    public String imagemUrl;

    public PrecoSubmit(double preco, String titulo, String imagemUrl) {
        this.preco = preco;
        this.titulo = titulo;
        this.imagemUrl = imagemUrl;
    }
}
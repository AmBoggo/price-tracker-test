package com.pricetracker.model;

import com.google.gson.annotations.SerializedName;

public class HistoricoPreco {
    @SerializedName("id")
    public int id;

    @SerializedName("produto_id")
    public int produtoId;

    @SerializedName("preco")
    public double preco;

    @SerializedName("data_consulta")
    public String dataConsulta;

    public String precoFormatado() {
        return String.format("R$ %.2f", preco);
    }

    public String dataFormatada() {
        if (dataConsulta == null) return "";
        // ISO: 2026-07-20T10:49:20.676002+00:00
        String data = dataConsulta.substring(0, 10);
        String hora = dataConsulta.substring(11, 16);
        return data + " " + hora;
    }
}
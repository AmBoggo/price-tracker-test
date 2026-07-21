package com.pricetracker.model;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

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
        try {
            // ISO: 2026-07-20T10:49:20.676002+00:00
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(dataConsulta.substring(0, 19));
            
            SimpleDateFormat brFormat = new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR"));
            brFormat.setTimeZone(TimeZone.getTimeZone("America/Sao_Paulo"));
            return brFormat.format(date);
        } catch (Exception e) {
            return dataConsulta.substring(0, 10) + " " + dataConsulta.substring(11, 16);
        }
    }
}
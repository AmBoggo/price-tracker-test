package com.pricetracker.api;

import com.pricetracker.model.Produto;
import com.pricetracker.model.ProdutoCreate;
import com.pricetracker.model.PrecoSubmit;
import com.pricetracker.model.HistoricoPreco;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @GET("api/produtos")
    Call<List<Produto>> listarProdutos();

    @POST("api/produtos")
    Call<Produto> addProduto(@Body ProdutoCreate body);

    @GET("api/produtos/{id}")
    Call<Produto> getProduto(@Path("id") int id);

    @POST("api/produtos/{id}/preco")
    Call<Produto> registrarPreco(@Path("id") int id, @Body PrecoSubmit body);

    @PATCH("api/produtos/{id}")
    Call<Produto> atualizarProduto(@Path("id") int id, @Body ProdutoCreate body);

    @DELETE("api/produtos/{id}")
    Call<Void> deletarProduto(@Path("id") int id);

    @GET("api/produtos/{id}/historico")
    Call<List<HistoricoPreco>> getHistorico(@Path("id") int id);
}
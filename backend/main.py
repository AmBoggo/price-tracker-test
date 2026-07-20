"""
PriceTracker API — Backend FastAPI para monitoramento de preços.

Endpoints:
  POST /api/produtos          → Adicionar produto via link
  GET  /api/produtos          → Listar todos
  GET  /api/produtos/{id}     → Detalhes
  PATCH /api/produtos/{id}    → Atualizar meta de preço
  DELETE /api/produtos/{id}   → Remover
  GET /api/produtos/{id}/historico → Histórico de preços
  POST /api/produtos/{id}/preco → Registrar novo preço (vindo do app)
  GET /health → Status
"""
import sqlite3
import json
import os
from datetime import datetime, timezone
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

DB_PATH = os.path.join(os.path.dirname(__file__), "price_tracker.db")

# ─── Pydantic Schemas ───────────────────────────────────────────────────────


class ProdutoCreate(BaseModel):
    url: str = Field(..., description="Link do produto")
    preco_meta: float | None = Field(None, description="Preço objetivo (meta)")


class PrecoUpdate(BaseModel):
    preco: float = Field(..., description="Preço atual do produto")
    titulo: str | None = Field(None, description="Título do produto")
    imagem_url: str | None = Field(None, description="URL da imagem")


class ProdutoOut(BaseModel):
    id: int
    url: str
    titulo: str | None
    imagem_url: str | None
    preco_atual: float | None
    preco_anterior: float | None
    variacao: float | None
    preco_meta: float | None
    site: str | None
    ativo: int
    criado_em: str
    atualizado_em: str | None


class HistoricoOut(BaseModel):
    id: int
    produto_id: int
    preco: float
    data_consulta: str


# ─── Database Setup ──────────────────────────────────────────────────────────


@asynccontextmanager
async def lifespan(app: FastAPI):
    await init_db()
    yield


app = FastAPI(title="PriceTracker API", version="1.0.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


async def get_db():
    """Cria uma conexão async com o SQLite."""
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row
    try:
        yield db
    finally:
        db.close()


async def init_db():
    """Cria as tabelas se não existirem."""
    def _init():
        db = sqlite3.connect(DB_PATH)
        db.execute("""
            CREATE TABLE IF NOT EXISTS produtos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                url TEXT NOT NULL UNIQUE,
                titulo TEXT,
                imagem_url TEXT,
                preco_atual REAL,
                preco_anterior REAL,
                variacao REAL,
                preco_meta REAL,
                site TEXT,
                ativo INTEGER DEFAULT 1,
                criado_em TEXT NOT NULL,
                atualizado_em TEXT
            )
        """)
        db.execute("""
            CREATE TABLE IF NOT EXISTS historico_precos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                produto_id INTEGER NOT NULL,
                preco REAL NOT NULL,
                data_consulta TEXT NOT NULL,
                FOREIGN KEY (produto_id) REFERENCES produtos(id)
            )
        """)
        db.commit()
        db.close()
    _init()


def extract_site(url: str) -> str:
    """Extrai o nome do site da URL."""
    url_lower = url.lower()
    if "zara" in url_lower:
        return "zara"
    elif "renner" in url_lower:
        return "renner"
    elif "sephora" in url_lower:
        return "sephora"
    return "outro"


# ─── Helper: converte Row → dict ─────────────────────────────────────────────


def row_to_dict(row) -> dict:
    if row is None:
        return None
    return dict(row)


# ─── Endpoints ───────────────────────────────────────────────────────────────


@app.get("/health")
async def health():
    return {"status": "ok", "app": "PriceTracker"}


@app.post("/api/produtos", status_code=201)
async def add_produto(body: ProdutoCreate):
    if not body.url.startswith("http"):
        raise HTTPException(400, "URL inválida")

    now = datetime.now(timezone.utc).isoformat()
    site = extract_site(body.url)

    def _insert():
        db = sqlite3.connect(DB_PATH)
        try:
            cur = db.execute(
                """INSERT INTO produtos (url, preco_meta, site, criado_em)
                   VALUES (?, ?, ?, ?)""",
                (body.url, body.preco_meta, site, now),
            )
            db.commit()
            produto_id = cur.lastrowid
            row = db.execute("SELECT * FROM produtos WHERE id = ?", (produto_id,)).fetchone()
            return row_to_dict(row)
        finally:
            db.close()

    try:
        produto = _insert()
        return produto
    except sqlite3.IntegrityError:
        raise HTTPException(409, "Produto já cadastrado com essa URL")


@app.get("/api/produtos")
async def list_produtos():
    def _list():
        db = sqlite3.connect(DB_PATH)
        db.row_factory = sqlite3.Row
        rows = db.execute(
            "SELECT * FROM produtos WHERE ativo = 1 ORDER BY criado_em DESC"
        ).fetchall()
        db.close()
        return [row_to_dict(r) for r in rows]
    return _list()


@app.get("/api/produtos/{produto_id}")
async def get_produto(produto_id: int):
    def _get():
        db = sqlite3.connect(DB_PATH)
        db.row_factory = sqlite3.Row
        row = db.execute("SELECT * FROM produtos WHERE id = ?", (produto_id,)).fetchone()
        db.close()
        return row_to_dict(row)
    p = _get()
    if not p:
        raise HTTPException(404, "Produto não encontrado")
    return p


@app.patch("/api/produtos/{produto_id}")
async def update_produto(produto_id: int, body: PrecoUpdate):
    def _update():
        db = sqlite3.connect(DB_PATH)
        now = datetime.now(timezone.utc).isoformat()
        db.execute(
            """UPDATE produtos SET preco_atual=?, titulo=COALESCE(?, titulo),
               imagem_url=COALESCE(?, imagem_url), atualizado_em=?
               WHERE id=?""",
            (body.preco, body.titulo, body.imagem_url, now, produto_id),
        )
        db.commit()
        row = db.execute("SELECT * FROM produtos WHERE id = ?", (produto_id,)).fetchone()
        db.close()
        return row_to_dict(row)
    p = _update()
    if not p:
        raise HTTPException(404, "Produto não encontrado")
    return p


@app.delete("/api/produtos/{produto_id}")
async def delete_produto(produto_id: int):
    def _delete():
        db = sqlite3.connect(DB_PATH)
        cur = db.execute("DELETE FROM produtos WHERE id = ?", (produto_id,))
        db.commit()
        deleted = cur.rowcount
        db.close()
        return deleted
    if _delete() == 0:
        raise HTTPException(404, "Produto não encontrado")
    return {"mensagem": "Produto removido"}


@app.post("/api/produtos/{produto_id}/preco", status_code=201)
async def register_preco(produto_id: int, body: PrecoUpdate):
    """Registra um novo preço vindo do app Android (WebView scraping)."""
    now = datetime.now(timezone.utc).isoformat()

    def _register():
        db = sqlite3.connect(DB_PATH)
        # Busca preço atual para calcular variação
        row = db.execute("SELECT preco_atual FROM produtos WHERE id = ?", (produto_id,)).fetchone()
        if not row:
            db.close()
            return None

        preco_anterior = row[0]
        variacao = None
        if preco_anterior is not None and preco_anterior > 0:
            variacao = round((body.preco - preco_anterior) / preco_anterior * 100, 1)

        # Atualiza produto
        db.execute(
            """UPDATE produtos SET preco_atual=?, preco_anterior=?,
               variacao=?, titulo=COALESCE(?, titulo),
               imagem_url=COALESCE(?, imagem_url), atualizado_em=?
               WHERE id=?""",
            (body.preco, preco_anterior, variacao, body.titulo, body.imagem_url, now, produto_id),
        )
        # Insere no histórico
        db.execute(
            "INSERT INTO historico_precos (produto_id, preco, data_consulta) VALUES (?, ?, ?)",
            (produto_id, body.preco, now),
        )
        db.commit()
        updated = db.execute("SELECT * FROM produtos WHERE id = ?", (produto_id,)).fetchone()
        db.close()
        return row_to_dict(updated)

    p = _register()
    if not p:
        raise HTTPException(404, "Produto não encontrado")
    return p


@app.get("/api/produtos/{produto_id}/historico")
async def get_historico(produto_id: int):
    def _get():
        db = sqlite3.connect(DB_PATH)
        db.row_factory = sqlite3.Row
        rows = db.execute(
            "SELECT * FROM historico_precos WHERE produto_id = ? ORDER BY data_consulta DESC LIMIT 50",
            (produto_id,),
        ).fetchall()
        db.close()
        return [row_to_dict(r) for r in rows]
    return _get()